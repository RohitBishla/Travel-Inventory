package com.navan.travel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import com.navan.travel.common.shard.Shard;
import com.navan.travel.support.ApiTestBase;

import tools.jackson.databind.JsonNode;

class SchemaTest extends ApiTestBase {

    @Test
    void everyShardHasAUniqueIndexOnExternalId() throws SQLException {
        for (Shard shard : shards.all()) {
            assertThat(indexesByColumn(shard, "transactions", true).get("EXTERNAL_ID"))
                    .as("%s", shard)
                    .contains("UQ_TRANSACTIONS_EXTERNAL_ID");
        }
    }

    @Test
    void databaseRejectsADuplicateExternalIdEvenWithoutTheApplication() {
        Shard shard = shards.forExternalId("direct-1");
        String insert = """
                INSERT INTO transactions (external_id, type, currency, occurred_at, total, version, created_at, updated_at)
                VALUES ('direct-1', 'rail', 'EUR', CURRENT_TIMESTAMP, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """;
        shard.jdbc().sql(insert).update();

        assertThatThrownBy(() -> shard.jdbc().sql(insert).update()).isInstanceOf(DuplicateKeyException.class);
        assertThat(countTransactionsWithExternalId("direct-1")).isEqualTo(1);
    }

    @Test
    void everyShardFindsTheInvoicesOfATransactionByIndex() throws SQLException {
        for (Shard shard : shards.all()) {
            assertThat(indexesByColumn(shard, "invoice_links", true).get("TRANSACTION_ID"))
                    .as("%s", shard)
                    .contains("PRIMARY");
        }
    }

    @Test
    void healthReportsEveryShard() {
        JsonNode health = api.get("/health").body();

        assertThat(health.get("status").stringValue()).isEqualTo("UP");
        assertThat(api.get("/health/readiness").body().get("status").stringValue()).isEqualTo("UP");
    }

    private static Map<String, Set<String>> indexesByColumn(Shard shard, String table, boolean uniqueOnly)
            throws SQLException {
        Map<String, Set<String>> indexesByColumn = new HashMap<>();
        try (Connection connection = shard.dataSource().getConnection();
             ResultSet indexes = connection.getMetaData()
                     .getIndexInfo(connection.getCatalog(), null, table, uniqueOnly, false)) {
            while (indexes.next()) {
                String column = indexes.getString("COLUMN_NAME");
                if (column != null) {
                    indexesByColumn.computeIfAbsent(column.toUpperCase(), key -> new HashSet<>())
                            .add(indexes.getString("INDEX_NAME").toUpperCase());
                }
            }
        }
        return indexesByColumn;
    }
}
