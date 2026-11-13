package com.navan.travel;

import static com.navan.travel.support.TestJson.decimal;
import static com.navan.travel.support.TestJson.text;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import com.navan.travel.support.ApiResponse;
import com.navan.travel.support.ApiTestBase;
import com.navan.travel.support.Fixtures;

import tools.jackson.databind.JsonNode;

class TransactionUpdateDeleteTest extends ApiTestBase {

    @Test
    void patchReplacesFieldsAndBumpsVersion() {
        String railId = api.createTransaction(Fixtures.seed("rail"));

        ApiResponse response = api.patch("/transactions/" + railId, Map.of(
                "total", 99.50,
                "fee_lines", List.of(Map.of("name", "Booking fee", "amount", 4.50), Map.of("name", "Seat", "amount", 5))));

        assertThat(response.status()).as("%s", response.body()).isEqualTo(200);
        JsonNode stored = api.get("/transactions/" + railId).body();
        assertThat(decimal(stored.get("total"))).isEqualByComparingTo("99.50");
        assertThat(stored.get("fee_lines")).hasSize(2);
        assertThat(text(stored.get("fee_lines").get(1), "name")).isEqualTo("Seat");
        assertThat(stored.get("metadata").get("legs")).hasSize(2);
        assertThat(stored.get("version").asLong()).isEqualTo(2);
    }

    @Test
    void patchReplacesMetadataUsingTheStoredType() {
        String feeId = api.createTransaction(Fixtures.seed("trip_fee"));

        ApiResponse response = api.patch("/transactions/" + feeId,
                Map.of("metadata", Map.of("fee_kind", "agent_call_fee", "description", "Rebooked by phone")));

        assertThat(response.status()).isEqualTo(200);
        assertThat(text(response.body().get("metadata"), "fee_kind")).isEqualTo("agent_call_fee");
        assertThat(response.body().get("metadata").has("related_external_id")).isFalse();
    }

    @Test
    void patchCannotChangeIdentityFields() {
        String flightId = api.createTransaction(Fixtures.seed("flight"));

        ApiResponse response = api.patch("/transactions/" + flightId, Map.of("currency", "USD"));

        assertThat(response.status()).isEqualTo(400);
        assertThat(text(api.get("/transactions/" + flightId).body(), "currency")).isEqualTo("EUR");
    }

    @Test
    void invalidPatchPersistsNothing() {
        String flightId = api.createTransaction(Fixtures.seed("flight"));

        ApiResponse response = api.patch("/transactions/" + flightId, Map.of(
                "total", 300.00,
                "metadata", Map.of("origin", "TXL", "destination", "LHR", "fare", 180, "coupons", List.of())));

        assertThat(response.status()).isEqualTo(400);
        JsonNode stored = api.get("/transactions/" + flightId).body();
        assertThat(decimal(stored.get("total"))).isEqualByComparingTo("220.00");
        assertThat(stored.get("metadata").get("coupons")).hasSize(1);
    }

    @Test
    void patchOfMissingTransactionReturnsNotFound() {
        assertThat(api.patch("/transactions/txn_999999", Map.of("total", 1)).status()).isEqualTo(404);
    }

    @Test
    void deletingAnInvoicedTransactionIsRejectedAndKeepsTheRow() {
        Map<String, String> ids = createTripTransactions();
        createTripInvoice(ids);
        String hotelId = ids.get("hotel");

        ApiResponse response = api.delete("/transactions/" + hotelId);

        assertThat(response.status()).isEqualTo(409);
        assertThat(api.get("/transactions/" + hotelId).status()).isEqualTo(200);
        assertThat(countRows("hotel_details")).isEqualTo(1);
    }

    @Test
    void deletingAnUnbilledTransactionRemovesItAndItsLines() {
        String flightId = api.createTransaction(Fixtures.seed("flight"));

        assertThat(api.delete("/transactions/" + flightId).status()).isEqualTo(204);

        assertThat(api.get("/transactions/" + flightId).status()).isEqualTo(404);
        assertThat(api.delete("/transactions/" + flightId).status()).isEqualTo(404);
        assertThat(countRows("transaction_tax_lines")).isZero();
        assertThat(countRows("transaction_fee_lines")).isZero();
        assertThat(countRows("flight_coupons")).isZero();
    }

    @Test
    void deletedExternalIdCanBeCreatedAgain() {
        String flightId = api.createTransaction(Fixtures.seed("flight"));
        api.delete("/transactions/" + flightId);

        assertThat(api.post("/transactions", Fixtures.seed("flight")).status()).isEqualTo(201);
    }

    @Test
    void concurrentPatchesNeverTearTheTaxLines() throws Exception {
        String hotelId = api.createTransaction(Fixtures.seed("hotel"));
        List<Map<String, Object>> taxLinesA = List.of(
                Map.of("name", "VAT", "rate", 0.07, "amount", 21.70),
                Map.of("name", "City tax", "amount", 5.00));
        List<Map<String, Object>> taxLinesB = List.of(
                Map.of("name", "VAT", "rate", 0.19, "amount", 52.96),
                Map.of("name", "Tourism levy", "amount", 3.00),
                Map.of("name", "Service tax", "amount", 1.00));

        ExecutorService clients = Executors.newFixedThreadPool(8);
        try {
            CountDownLatch startGate = new CountDownLatch(1);
            List<Future<ApiResponse>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                List<Map<String, Object>> taxLines = i % 2 == 0 ? taxLinesA : taxLinesB;
                futures.add(clients.submit(() -> {
                    startGate.await();
                    return api.patch("/transactions/" + hotelId, Map.of("tax_lines", taxLines));
                }));
            }
            startGate.countDown();
            for (Future<ApiResponse> future : futures) {
                assertThat(future.get().status()).isEqualTo(200);
            }
        } finally {
            clients.shutdownNow();
        }

        List<String> storedNames = new ArrayList<>();
        api.get("/transactions/" + hotelId).body().get("tax_lines").forEach(line -> storedNames.add(text(line, "name")));
        assertThat(storedNames).isIn(
                List.of("VAT", "City tax"),
                List.of("VAT", "Tourism levy", "Service tax"));
    }
}
