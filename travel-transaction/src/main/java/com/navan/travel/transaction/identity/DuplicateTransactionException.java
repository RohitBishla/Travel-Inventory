package com.navan.travel.transaction.identity;

public class DuplicateTransactionException extends RuntimeException {

    private final long existingId;

    public DuplicateTransactionException(String externalId, long existingId) {
        super("A transaction with external_id '" + externalId + "' already exists");
        this.existingId = existingId;
    }

    public long existingId() {
        return existingId;
    }
}
