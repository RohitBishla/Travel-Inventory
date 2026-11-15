package com.navan.travel.transaction.identity;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * {@code external_ids} on the key's ring owner is where an {@code external_id} is unique. A new transaction is
 * written on that same shard, so the row and its record commit together. After shards are added the record can sit
 * on a different shard from the row; rows never move, only these small records do.
 */
@Component
public class ExternalIdentity {

    private static final List<String> UNIQUE_CONSTRAINTS = List.of("uq_external_ids_external_id", "uq_transactions_external_id");

    private final JdbcClient jdbc;

    public ExternalIdentity(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Long> findTransactionId(String externalId) {
        return jdbc.sql("SELECT transaction_id FROM external_ids WHERE external_id = :externalId")
                .param("externalId", externalId)
                .query(Long.class)
                .optional();
    }

    public void register(String externalId, long transactionId) {
        jdbc.sql("INSERT INTO external_ids (external_id, transaction_id) VALUES (:externalId, :transactionId)")
                .param("externalId", externalId)
                .param("transactionId", transactionId)
                .update();
    }

    public void forget(String externalId, long transactionId) {
        jdbc.sql("DELETE FROM external_ids WHERE external_id = :externalId AND transaction_id = :transactionId")
                .param("externalId", externalId)
                .param("transactionId", transactionId)
                .update();
    }

    public List<IdentityRecord> page(String afterExternalId, int limit) {
        return jdbc.sql("""
                        SELECT external_id, transaction_id FROM external_ids
                        WHERE external_id > :after ORDER BY external_id LIMIT :limit
                        """)
                .param("after", afterExternalId)
                .param("limit", limit)
                .query((rs, rowNum) -> new IdentityRecord(rs.getString("external_id"), rs.getLong("transaction_id")))
                .list();
    }

    public void copy(Collection<IdentityRecord> records) {
        for (IdentityRecord record : records) {
            jdbc.sql("INSERT IGNORE INTO external_ids (external_id, transaction_id) VALUES (:externalId, :transactionId)")
                    .param("externalId", record.externalId())
                    .param("transactionId", record.transactionId())
                    .update();
        }
    }

    public void forget(Collection<IdentityRecord> records) {
        records.forEach(record -> forget(record.externalId(), record.transactionId()));
    }

    public boolean isExternalIdConflict(DuplicateKeyException e) {
        String message = e.getMostSpecificCause().getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return UNIQUE_CONSTRAINTS.stream().anyMatch(normalized::contains);
    }

    public record IdentityRecord(String externalId, long transactionId) {
    }
}
