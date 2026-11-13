package com.navan.travel.transaction.identity;

import java.util.Locale;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class ExternalIdentity {

    private static final String UNIQUE_INDEX_NAME = "uq_transactions_external_id";

    private final JdbcClient jdbc;

    public ExternalIdentity(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Long> findTransactionId(String externalId) {
        return jdbc.sql("SELECT id FROM transactions WHERE external_id = :externalId")
                .param("externalId", externalId)
                .query(Long.class)
                .optional();
    }

    public void rejectIfTaken(String externalId) {
        if (externalId == null) {
            return;
        }
        findTransactionId(externalId).ifPresent(existingId -> {
            throw new DuplicateTransactionException(externalId, existingId);
        });
    }

    public boolean isExternalIdConflict(DuplicateKeyException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains(UNIQUE_INDEX_NAME);
    }
}
