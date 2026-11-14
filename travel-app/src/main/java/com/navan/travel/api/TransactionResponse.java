package com.navan.travel.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.navan.travel.common.PublicIds;
import com.navan.travel.transaction.FeeLine;
import com.navan.travel.transaction.TaxLine;
import com.navan.travel.transaction.Transaction;
import com.navan.travel.transaction.TransactionType;
import com.navan.travel.transaction.metadata.TransactionMetadata;

public record TransactionResponse(
        String transactionId,
        TransactionType type,
        String externalId,
        String currency,
        Instant occurredAt,
        BigDecimal total,
        List<TaxLine> taxLines,
        List<FeeLine> feeLines,
        TransactionMetadata metadata,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                PublicIds.transaction(transaction.id()),
                transaction.type(),
                transaction.externalId(),
                transaction.currency(),
                transaction.occurredAt(),
                transaction.total(),
                transaction.taxLines(),
                transaction.feeLines(),
                transaction.metadata(),
                transaction.version(),
                transaction.createdAt(),
                transaction.updatedAt());
    }
}
