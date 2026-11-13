package com.navan.travel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import com.navan.travel.support.ApiTestBase;

class SchemaTest extends ApiTestBase {

    @Autowired
    private DataSource dataSource;

    @Test
    void externalIdHasAUniqueIndex() throws SQLException {
        assertThat(indexedColumns("TRANSACTIONS", true)).containsEntry("EXTERNAL_ID", "UQ_TRANSACTIONS_EXTERNAL_ID");
    }

    @Test
    void databaseRejectsADuplicateExternalIdEvenWithoutTheApplication() {
        String insert = """
                INSERT INTO transactions (external_id, type, currency, occurred_at, total, version, created_at, updated_at)
                VALUES ('direct-1', 'rail', 'EUR', CURRENT_TIMESTAMP, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """;
        jdbc.sql(insert).update();

        assertThatThrownBy(() -> jdbc.sql(insert).update()).isInstanceOf(DuplicateKeyException.class);
        assertThat(countTransactionsWithExternalId("direct-1")).isEqualTo(1);
    }

    @Test
    void invoiceLinesAreIndexedByTransactionId() throws SQLException {
        assertThat(indexedColumns("INVOICE_LINES", false)).containsEntry("TRANSACTION_ID", "IX_INVOICE_LINES_TRANSACTION_ID");
    }

    @Test
    void healthEndpointReportsUp() {
        assertThat(api.get("/health").body().get("status").stringValue()).isEqualTo("UP");
    }

    private Map<String, String> indexedColumns(String table, boolean uniqueOnly) throws SQLException {
        Map<String, String> indexByColumn = new HashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet indexes = metaData.getIndexInfo(null, null, table, uniqueOnly, false)) {
                while (indexes.next()) {
                    String column = indexes.getString("COLUMN_NAME");
                    if (column != null) {
                        indexByColumn.put(column.toUpperCase(), indexes.getString("INDEX_NAME").toUpperCase());
                    }
                }
            }
        }
        return indexByColumn;
    }
}
