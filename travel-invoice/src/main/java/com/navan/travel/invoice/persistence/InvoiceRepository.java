package com.navan.travel.invoice.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.navan.travel.common.Money;
import com.navan.travel.common.PageQuery;
import com.navan.travel.common.SqlTime;
import com.navan.travel.invoice.InvoiceSummary;
import com.navan.travel.invoice.snapshot.InvoiceSnapshot;

@Repository
public class InvoiceRepository {

    private static final String SUMMARY_COLUMNS =
            "id, currency, total, line_count, version, created_at, updated_at";

    private final JdbcClient jdbc;
    private final Clock clock;

    public InvoiceRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public long insert(InvoiceSnapshot snapshot) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.sql("""
                        INSERT INTO invoices (currency, total, line_count, version, created_at, updated_at)
                        VALUES (:currency, :total, :lineCount, 1, :now, :now)
                        """)
                .param("currency", snapshot.currency())
                .param("total", snapshot.total())
                .param("lineCount", snapshot.lines().size())
                .param("now", SqlTime.toSql(clock.instant()))
                .update(keyHolder, "id");
        return keyHolder.getKeyAs(Number.class).longValue();
    }

    public void update(long id, InvoiceSnapshot snapshot) {
        jdbc.sql("""
                        UPDATE invoices
                        SET currency = :currency, total = :total, line_count = :lineCount,
                            version = version + 1, updated_at = :now
                        WHERE id = :id
                        """)
                .param("currency", snapshot.currency())
                .param("total", snapshot.total())
                .param("lineCount", snapshot.lines().size())
                .param("now", SqlTime.toSql(clock.instant()))
                .param("id", id)
                .update();
    }

    public boolean lockById(long id) {
        return jdbc.sql("SELECT id FROM invoices WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(Long.class)
                .optional()
                .isPresent();
    }

    public boolean delete(long id) {
        return jdbc.sql("DELETE FROM invoices WHERE id = :id").param("id", id).update() == 1;
    }

    public Optional<InvoiceSummary> findSummary(long id) {
        return jdbc.sql("SELECT " + SUMMARY_COLUMNS + " FROM invoices WHERE id = :id")
                .param("id", id)
                .query(this::mapSummary)
                .optional();
    }

    public List<InvoiceSummary> findSummaryPage(PageQuery page) {
        return jdbc.sql("SELECT " + SUMMARY_COLUMNS + " FROM invoices "
                        + "WHERE created_at < :createdAt OR (created_at = :createdAt AND id < :id) "
                        + "ORDER BY created_at DESC, id DESC LIMIT :limit")
                .param("createdAt", SqlTime.toSql(page.upperBound().createdAt()))
                .param("id", page.upperBound().id())
                .param("limit", page.fetchSize())
                .query(this::mapSummary)
                .list();
    }

    private InvoiceSummary mapSummary(ResultSet rs, int rowNum) throws SQLException {
        String currency = rs.getString("currency");
        return new InvoiceSummary(
                rs.getLong("id"),
                currency,
                Money.toCurrencyScale(rs.getBigDecimal("total"), currency),
                rs.getInt("line_count"),
                rs.getLong("version"),
                SqlTime.read(rs, "created_at"),
                SqlTime.read(rs, "updated_at"));
    }
}
