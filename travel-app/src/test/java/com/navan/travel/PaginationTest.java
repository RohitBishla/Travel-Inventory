package com.navan.travel;

import static com.navan.travel.support.TestJson.text;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.navan.travel.support.ApiResponse;
import com.navan.travel.support.ApiTestBase;
import com.navan.travel.support.Fixtures;

import tools.jackson.databind.JsonNode;

class PaginationTest extends ApiTestBase {

    private static final int TRANSACTION_COUNT = 120;

    @Test
    void listDefaultsToFiftyAndCapsLargeLimitsAtOneHundred() {
        createFees(TRANSACTION_COUNT);

        JsonNode defaultPage = api.get("/transactions").body();
        assertThat(defaultPage.get("items")).hasSize(50);
        assertThat(defaultPage.get("limit").asInt()).isEqualTo(50);
        assertThat(defaultPage.has("next_cursor")).isTrue();

        JsonNode cappedPage = api.get("/transactions?limit=1000").body();
        assertThat(cappedPage.get("items")).hasSize(100);
        assertThat(cappedPage.get("limit").asInt()).isEqualTo(100);
    }

    @Test
    void cursorWalksEveryTransactionOnceNewestFirst() {
        List<String> created = createFees(TRANSACTION_COUNT);

        List<String> seen = new ArrayList<>();
        String path = "/transactions?limit=25";
        while (path != null) {
            JsonNode page = api.get(path).body();
            page.get("items").forEach(item -> seen.add(text(item, "transaction_id")));
            path = page.has("next_cursor") ? "/transactions?limit=25&cursor=" + text(page, "next_cursor") : null;
        }

        assertThat(seen).hasSize(TRANSACTION_COUNT);
        assertThat(new HashSet<>(seen)).hasSize(TRANSACTION_COUNT);
        assertThat(seen).containsExactlyElementsOf(created.reversed());
    }

    @Test
    void invoiceListUsesTheSamePagingRules() {
        List<String> fees = createFees(3);
        fees.forEach(fee -> api.createInvoice(List.of(fee)));

        JsonNode firstPage = api.get("/invoices?limit=2").body();
        assertThat(firstPage.get("items")).hasSize(2);
        JsonNode secondPage = api.get("/invoices?limit=2&cursor=" + text(firstPage, "next_cursor")).body();
        assertThat(secondPage.get("items")).hasSize(1);
        assertThat(secondPage.has("next_cursor")).isFalse();
        assertThat(api.get("/invoices?limit=1000").body().get("limit").asInt()).isEqualTo(100);
    }

    @Test
    void invalidPagingParametersAreRejected() {
        assertThat(api.get("/transactions?limit=0").status()).isEqualTo(400);
        assertThat(api.get("/transactions?limit=abc").status()).isEqualTo(400);
        assertThat(api.get("/transactions?cursor=not-a-cursor").status()).isEqualTo(400);
        assertThat(api.get("/invoices?limit=-5").status()).isEqualTo(400);
    }

    private List<String> createFees(int count) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ApiResponse response = api.post("/transactions", Fixtures.navanFee("fee-" + i, "EUR"));
            ids.add(response.transactionId());
        }
        return ids;
    }
}
