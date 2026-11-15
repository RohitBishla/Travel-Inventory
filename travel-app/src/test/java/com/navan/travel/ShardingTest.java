package com.navan.travel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.navan.travel.common.shard.Shard;
import com.navan.travel.support.ApiTestBase;
import com.navan.travel.support.Fixtures;

import tools.jackson.databind.JsonNode;

class ShardingTest extends ApiTestBase {

    @Test
    void transactionsLiveOnTheShardTheirExternalIdHashesToAndTheirIdNamesIt() {
        Map<String, String> ids = createTripTransactions();

        for (Map.Entry<String, String> seed : ids.entrySet()) {
            String externalId = Fixtures.seed(seed.getKey()).get("external_id").stringValue();
            Shard expected = shards.forExternalId(externalId);
            assertThat(shardOf(seed.getValue())).as(seed.getKey()).isEqualTo(expected);
            assertThat(expected.jdbc().sql("SELECT COUNT(*) FROM transactions WHERE id = :id")
                    .param("id", numericId(seed.getValue()))
                    .query(Long.class)
                    .single()).as(seed.getKey()).isEqualTo(1);
        }
        assertThat(ids.values().stream().map(this::shardOf).distinct()).as("the trip spans both shards").hasSize(2);
    }

    @Test
    void tripInvoiceLivesOnTheLowestShardAndLinksEachTransactionOnItsOwnShard() {
        Map<String, String> ids = createTripTransactions();

        String invoiceId = createTripInvoice(ids);

        assertThat(shardOf(invoiceId).id()).isZero();
        for (String transactionId : ids.values()) {
            assertThat(jdbcOnShardOf(transactionId)
                    .sql("SELECT invoice_id FROM invoice_links WHERE transaction_id = :id")
                    .param("id", numericId(transactionId))
                    .query(Long.class)
                    .list()).as(transactionId).containsExactly(numericId(invoiceId));
        }
        assertThat(countRows("invoice_links")).isEqualTo(ids.size());
    }

    @Test
    void transactionOnAnotherShardThanItsInvoiceCannotChange() {
        Map<String, String> ids = createTripTransactions();
        String invoiceId = createTripInvoice(ids);
        String otherShardTransaction = ids.values().stream()
                .filter(id -> !shardOf(id).equals(shardOf(invoiceId)))
                .findFirst()
                .orElseThrow();

        assertThat(api.patch("/transactions/" + otherShardTransaction, Map.of("total", 1.00)).status()).isEqualTo(409);
        assertThat(api.delete("/transactions/" + otherShardTransaction).status()).isEqualTo(409);
    }

    @Test
    void deletingAnInvoiceReleasesItsTransactionsOnEveryShard() {
        Map<String, String> ids = createTripTransactions();
        String invoiceId = createTripInvoice(ids);

        assertThat(api.delete("/invoices/" + invoiceId).status()).isEqualTo(204);

        assertThat(countRows("invoice_links")).isZero();
        for (String transactionId : ids.values()) {
            assertThat(api.patch("/transactions/" + transactionId, Map.of("occurred_at", "2026-03-11T00:00:00Z"))
                    .status()).as(transactionId).isEqualTo(200);
        }
    }

    @Test
    void leftoverLinkToAnInvoiceThatDoesNotBillTheTransactionIsIgnoredAndRemoved() {
        String railId = api.createTransaction(Fixtures.seed("rail"));
        long missingInvoiceId = 999_999;
        jdbcOnShardOf(railId).sql("INSERT INTO invoice_links (transaction_id, invoice_id) VALUES (:transactionId, :invoiceId)")
                .param("transactionId", numericId(railId))
                .param("invoiceId", missingInvoiceId)
                .update();

        assertThat(api.patch("/transactions/" + railId, Map.of("total", 96.00, "fee_lines",
                List.of(Map.of("name", "Booking fee", "amount", 6.00)))).status()).isEqualTo(200);
        assertThat(countRows("invoice_links")).isZero();
    }

    @Test
    void databaseRefusesToDeleteALinkedTransactionEvenWithoutTheApplication() {
        Map<String, String> ids = createTripTransactions();
        createTripInvoice(ids);
        String railId = ids.get("rail");

        assertThatThrownBy(() -> jdbcOnShardOf(railId).sql("DELETE FROM transactions WHERE id = :id")
                .param("id", numericId(railId))
                .update()).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void listPagesMergeBothShardsNewestFirstWithoutGapsOrRepeats() {
        List<String> created = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            created.add(api.createTransaction(Fixtures.navanFee("fee-merge-" + i, "EUR")));
        }

        List<Long> seen = new ArrayList<>();
        String path = "/transactions?limit=5";
        while (path != null) {
            JsonNode page = api.get(path).body();
            page.get("items").forEach(item -> seen.add(numericId(item.get("transaction_id").stringValue())));
            path = page.has("next_cursor") ? "/transactions?limit=5&cursor=" + page.get("next_cursor").stringValue() : null;
        }

        assertThat(created.stream().map(this::shardOf).distinct()).as("rows on both shards").hasSize(2);
        assertThat(seen).as("newest first across shards, though ids interleave")
                .containsExactlyElementsOf(created.reversed().stream().map(ShardingTest::numericId).toList());
    }
}
