package com.navan.travel;

import static com.navan.travel.support.TestJson.decimal;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.navan.travel.support.ApiResponse;
import com.navan.travel.support.ApiTestBase;

import tools.jackson.databind.JsonNode;

class InvoiceSnapshotTest extends ApiTestBase {

    @Test
    void invoiceKeepsBilledValuesAfterTheStoredFlightChanges() {
        Map<String, String> ids = createTripTransactions();
        String invoiceId = createTripInvoice(ids);
        long flightId = numericId(ids.get("flight"));

        jdbc.sql("UPDATE transactions SET total = 999.99, version = version + 1 WHERE id = :id")
                .param("id", flightId)
                .update();
        jdbc.sql("UPDATE transaction_tax_lines SET amount = 500 WHERE transaction_id = :id")
                .param("id", flightId)
                .update();
        jdbc.sql("UPDATE flight_coupons SET flight_number = 'XX000' WHERE transaction_id = :id")
                .param("id", flightId)
                .update();

        JsonNode invoice = api.get("/invoices/" + invoiceId).body();
        JsonNode flightLine = invoice.get("lines").get(0);
        assertThat(decimal(invoice.get("totals").get("flight"))).isEqualByComparingTo("220.00");
        assertThat(decimal(flightLine.get("total"))).isEqualByComparingTo("220.00");
        assertThat(decimal(flightLine.get("tax_lines").get(0).get("amount"))).isEqualByComparingTo("15.00");
        assertThat(flightLine.get("metadata").get("coupons").get(0).get("flight_number").stringValue())
                .isEqualTo("BA982");
        assertThat(decimal(invoice.get("tax_rollup").get(0).get("amount"))).isEqualByComparingTo("15.00");
        assertThat(decimal(invoice.get("totals").get("grand_total"))).isEqualByComparingTo("685.70");

        JsonNode storedFlight = api.get("/transactions/" + ids.get("flight")).body();
        assertThat(decimal(storedFlight.get("total"))).isEqualByComparingTo("999.99");
    }

    @Test
    void patchingAnInvoicedTransactionIsRejectedAndChangesNothing() {
        Map<String, String> ids = createTripTransactions();
        String invoiceId = createTripInvoice(ids);
        String flightId = ids.get("flight");

        ApiResponse patch = api.patch("/transactions/" + flightId, Map.of(
                "total", 999.99,
                "tax_lines", List.of(Map.of("name", "Airport tax", "amount", 99.00))));

        assertThat(patch.status()).isEqualTo(409);
        assertThat(patch.error()).isEqualTo("transaction_invoiced");
        JsonNode flight = api.get("/transactions/" + flightId).body();
        assertThat(decimal(flight.get("total"))).isEqualByComparingTo("220.00");
        assertThat(decimal(flight.get("tax_lines").get(0).get("amount"))).isEqualByComparingTo("15.00");
        assertThat(flight.get("version").asLong()).isEqualTo(1);

        JsonNode invoice = api.get("/invoices/" + invoiceId).body();
        assertThat(decimal(invoice.get("totals").get("flight"))).isEqualByComparingTo("220.00");
        assertThat(decimal(invoice.get("totals").get("grand_total"))).isEqualByComparingTo("685.70");
    }
}
