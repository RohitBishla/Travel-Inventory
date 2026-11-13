package com.navan.travel.transaction.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Collection;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.navan.travel.common.PageQuery;
import com.navan.travel.common.SqlTime;
import com.navan.travel.transaction.TransactionDraft;
import com.navan.travel.transaction.TransactionType;

@Repository
class TransactionRepository {

    private static final String HEADER_COLUMNS =
            "id, type, external_id, currency, occurred_at, total, version, created_at, updated_at";

    private final JdbcClient jdbc;
    private final Clock clock;

    TransactionRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    long insert(TransactionDraft draft) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.sql("""
                        INSERT INTO transactions
                            (external_id, type, currency, occurred_at, total, version, created_at, updated_at)
                        VALUES (:externalId, :type, :currency, :occurredAt, :total, 1, :now, :now)
                        """)
                .param("externalId", draft.externalId())
                .param("type", draft.type().code())
                .param("currency", draft.currency())
                .param("occurredAt", SqlTime.toSql(draft.occurredAt()))
                .param("total", draft.total())
                .param("now", SqlTime.toSql(clock.instant()))
                .update(keyHolder, "id");
        return keyHolder.getKeyAs(Long.class);
    }

    void updateHeader(long id, TransactionDraft draft) {
        jdbc.sql("""
                        UPDATE transactions
                        SET occurred_at = :occurredAt, total = :total, version = version + 1, updated_at = :now
                        WHERE id = :id
                        """)
                .param("occurredAt", SqlTime.toSql(draft.occurredAt()))
                .param("total", draft.total())
                .param("now", SqlTime.toSql(clock.instant()))
                .param("id", id)
                .update();
    }

    boolean delete(long id) {
        return jdbc.sql("DELETE FROM transactions WHERE id = :id").param("id", id).update() == 1;
    }

    boolean lockById(long id) {
        return jdbc.sql("SELECT id FROM transactions WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(Long.class)
                .optional()
                .isPresent();
    }

    List<Long> lockByIds(Collection<Long> ids) {
        return jdbc.sql("SELECT id FROM transactions WHERE id IN (:ids) ORDER BY id FOR UPDATE")
                .param("ids", ids)
                .query(Long.class)
                .list();
    }

    List<TransactionHeader> findHeaders(Collection<Long> ids) {
        return jdbc.sql("SELECT " + HEADER_COLUMNS + " FROM transactions WHERE id IN (:ids)")
                .param("ids", ids)
                .query(this::mapHeader)
                .list();
    }

    List<TransactionHeader> findHeaderPage(PageQuery page) {
        return jdbc.sql("SELECT " + HEADER_COLUMNS + " FROM transactions "
                        + "WHERE id < :idUpperBound ORDER BY id DESC LIMIT :limit")
                .param("idUpperBound", page.idUpperBound())
                .param("limit", page.fetchSize())
                .query(this::mapHeader)
                .list();
    }

    private TransactionHeader mapHeader(ResultSet rs, int rowNum) throws SQLException {
        return new TransactionHeader(
                rs.getLong("id"),
                readType(rs),
                rs.getString("external_id"),
                rs.getString("currency"),
                SqlTime.read(rs, "occurred_at"),
                rs.getBigDecimal("total"),
                rs.getLong("version"),
                SqlTime.read(rs, "created_at"),
                SqlTime.read(rs, "updated_at"));
    }

    private static TransactionType readType(ResultSet rs) throws SQLException {
        String code = rs.getString("type");
        return TransactionType.fromCode(code)
                .orElseThrow(() -> new IllegalStateException("Unknown transaction type in database: " + code));
    }
}
