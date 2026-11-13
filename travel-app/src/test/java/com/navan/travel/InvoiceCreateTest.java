package com.navan.travel;

import static com.navan.travel.support.TestJson.decimal;
import static com.navan.travel.support.TestJson.text;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.navan.travel.support.ApiResponse;
import com.navan.travel.support.ApiTestBase;
import com.navan.travel.support.Fixtures;

import tools.jackson.databind.JsonNode;

class InvoiceCreateTest extends ApiTestBase {

    @Test
    void tripInvoiceMatchesExpectedInvoiceFixture() {
        Map<String, String> ids = createTripTransactions();
        JsonNode expected = Fixtures.expectedInvoice();

        ApiResponse response = api.createInvoice(List.copyOf(ids.values()));

        assertThat(response.status()).isEqualTo(201);
        JsonNode invoice = api.get("/invoices/" + response.id()).body();
        assertThat(text(invoice, "currency")).isEqualTo(text(expected, "currency"));
        assertThat(decimal(invoice.get("total"))).isEqualByComparingTo("685.70");
        expected.get("totals").properties().forEach(total ->
                assertThat(decimal(invoice.get("totals").get(total.getKey())))
                        .as(total.getKey())
                        .isEqualByComparingTo(decimal(total.getValue())));

        JsonNode lines = invoice.get("lines");
        assertThat(lines.size()).isGreaterThanOrEqualTo(expected.get("line_count_minimum").asInt());
        assertThat(valuesOf(lines, "type")).containsAll(valuesOf(expected.get("transaction_types_included")));
        assertThat(valuesOf(lines, "category")).containsAll(valuesOf(expected.get("navan_fee_kinds_included")));

        assertThat(rollupAsStrings(invoice.get("tax_rollup")))
                .containsExactlyElementsOf(rollupAsStrings(expected.get("tax_rollup_example")));
        assertThat(rollupAsStrings(invoice.get("fee_rollup")))
                .containsExactlyElementsOf(rollupAsStrings(expected.get("fee_rollup_example")));

        assertThat(lines.get(0).get("metadata").get("coupons").get(0).has("flight_number")).isTrue();
        assertThat(lines.get(1).get("metadata").get("address").has("city")).isTrue();
        assertThat(lines.get(2).get("metadata").get("legs")).hasSize(2);
        assertThat(text(lines.get(3).get("metadata"), "fee_kind")).isEqualTo("trip_fee");
    }

    @Test
    void singleTransactionIdBodyCreatesAnInvoice() {
        String flightId = api.createTransaction(Fixtures.seed("flight"));

        ApiResponse response = api.post("/invoices", Map.of("transaction_id", flightId));

        assertThat(response.status()).isEqualTo(201);
        assertThat(decimal(response.body().get("total"))).isEqualByComparingTo("220.00");
        assertThat(response.body().get("lines")).hasSize(1);
    }

    @Test
    void sameTransactionCanBeOnMoreThanOneInvoice() {
        String flightId = api.createTransaction(Fixtures.seed("flight"));

        assertThat(api.post("/invoices", Map.of("transaction_id", flightId)).status()).isEqualTo(201);
        assertThat(api.post("/invoices", Map.of("transaction_id", flightId)).status()).isEqualTo(201);
    }

    @Test
    void mixedCurrenciesAreRejected() {
        String euroFee = api.createTransaction(Fixtures.navanFee("fee-eur", "EUR"));
        String dollarFee = api.createTransaction(Fixtures.navanFee("fee-usd", "USD"));

        ApiResponse response = api.createInvoice(List.of(euroFee, dollarFee));

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.error()).isEqualTo("mixed_currency");
        assertThat(countRows("invoices")).isZero();
    }

    @Test
    void sameTransactionListedTwiceIsRejected() {
        String flightId = api.createTransaction(Fixtures.seed("flight"));

        ApiResponse response = api.createInvoice(List.of(flightId, flightId));

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.error()).isEqualTo("duplicate_transaction_id");
        assertThat(countRows("invoices")).isZero();
    }

    @Test
    void missingTransactionIdReturnsNotFound() {
        String flightId = api.createTransaction(Fixtures.seed("flight"));

        ApiResponse response = api.createInvoice(List.of(flightId, "txn_999999"));

        assertThat(response.status()).isEqualTo(404);
        assertThat(text(response.body(), "message")).contains("txn_999999");
        assertThat(countRows("invoices")).isZero();
    }

    @Test
    void emptyOrAmbiguousBodiesAreRejected() {
        assertThat(api.createInvoice(List.of()).status()).isEqualTo(400);
        assertThat(api.post("/invoices", Map.of()).status()).isEqualTo(400);
        assertThat(api.post("/invoices", Map.of("transaction_id", "txn_1", "transaction_ids", List.of("txn_2")))
                .status()).isEqualTo(400);
    }

    private static List<String> valuesOf(JsonNode lines, String field) {
        List<String> values = new ArrayList<>();
        lines.forEach(line -> values.add(text(line, field)));
        return values;
    }

    private static List<String> valuesOf(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.stringValue()));
        return values;
    }

    private static List<String> rollupAsStrings(JsonNode rollup) {
        List<String> entries = new ArrayList<>();
        rollup.forEach(entry -> entries.add(text(entry, "name")
                + "|" + (entry.has("rate") ? decimal(entry.get("rate")).stripTrailingZeros().toPlainString() : "-")
                + "|" + decimal(entry.get("amount")).stripTrailingZeros().toPlainString()));
        return entries;
    }
}
