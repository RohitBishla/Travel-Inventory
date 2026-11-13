package com.navan.travel;

import static com.navan.travel.support.TestJson.decimal;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.navan.travel.support.ApiResponse;
import com.navan.travel.support.ApiTestBase;
import com.navan.travel.support.Fixtures;

import tools.jackson.databind.JsonNode;

class InvoiceUpdateDeleteTest extends ApiTestBase {

    @Test
    void deletingAnInvoiceKeepsItsTransactions() {
        Map<String, String> ids = createTripTransactions();
        String invoiceId = createTripInvoice(ids);

        assertThat(api.delete("/invoices/" + invoiceId).status()).isEqualTo(204);

        assertThat(api.get("/invoices/" + invoiceId).status()).isEqualTo(404);
        ids.values().forEach(id -> assertThat(api.get("/transactions/" + id).status()).isEqualTo(200));
        assertThat(countRows("invoice_lines")).isZero();
        assertThat(countRows("invoice_line_tax_lines")).isZero();
        assertThat(countRows("invoice_tax_rollups")).isZero();
    }

    @Test
    void transactionsBecomeEditableOnceNoInvoiceHoldsThem() {
        Map<String, String> ids = createTripTransactions();
        String invoiceId = createTripInvoice(ids);
        api.delete("/invoices/" + invoiceId);

        ApiResponse patch = api.patch("/transactions/" + ids.get("flight"), Map.of("total", 230.00));

        assertThat(patch.status()).isEqualTo(200);
    }

    @Test
    void deletingMissingInvoiceReturnsNotFound() {
        assertThat(api.delete("/invoices/inv_999999").status()).isEqualTo(404);
    }

    @Test
    void patchRebuildsTheSnapshotFromCurrentTransactions() {
        Map<String, String> ids = createTripTransactions();
        String invoiceId = api.createInvoice(List.of(ids.get("flight"))).id();

        ApiResponse response = api.patch("/invoices/" + invoiceId,
                Map.of("transaction_ids", List.of(ids.get("hotel"), ids.get("rail"))));

        assertThat(response.status()).isEqualTo(200);
        JsonNode invoice = api.get("/invoices/" + invoiceId).body();
        assertThat(invoice.get("lines")).hasSize(2);
        assertThat(invoice.get("totals").has("flight")).isFalse();
        assertThat(decimal(invoice.get("total"))).isEqualByComparingTo("425.70");
        assertThat(invoice.get("version").asLong()).isEqualTo(2);
        assertThat(api.patch("/transactions/" + ids.get("flight"), Map.of("total", 230.00)).status())
                .isEqualTo(200);
    }

    @Test
    void mixedCurrencyPatchLeavesTheInvoiceUnchanged() {
        Map<String, String> ids = createTripTransactions();
        String invoiceId = createTripInvoice(ids);
        String dollarFee = api.createTransaction(Fixtures.navanFee("fee-usd", "USD"));

        ApiResponse response = api.patch("/invoices/" + invoiceId,
                Map.of("transaction_ids", List.of(ids.get("flight"), dollarFee)));

        assertThat(response.status()).isEqualTo(409);
        JsonNode invoice = api.get("/invoices/" + invoiceId).body();
        assertThat(invoice.get("lines")).hasSize(5);
        assertThat(decimal(invoice.get("total"))).isEqualByComparingTo("685.70");
        assertThat(invoice.get("version").asLong()).isEqualTo(1);
    }

    @Test
    void patchWithMissingTransactionOrInvoiceReturnsNotFound() {
        String flightId = api.createTransaction(Fixtures.seed("flight"));
        String invoiceId = api.createInvoice(List.of(flightId)).id();

        assertThat(api.patch("/invoices/" + invoiceId, Map.of("transaction_ids", List.of("txn_999999"))).status())
                .isEqualTo(404);
        assertThat(api.patch("/invoices/inv_999999", Map.of("transaction_ids", List.of(flightId))).status())
                .isEqualTo(404);
        assertThat(api.get("/invoices/" + invoiceId).body().get("lines")).hasSize(1);
    }
}
