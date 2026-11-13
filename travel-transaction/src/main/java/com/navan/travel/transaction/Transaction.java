package com.navan.travel.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.navan.travel.transaction.metadata.NavanFeeMetadata;
import com.navan.travel.transaction.metadata.TransactionMetadata;

public record Transaction(
        long id,
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

    public String billingCategory() {
        if (metadata instanceof NavanFeeMetadata navanFee) {
            return navanFee.feeKind().code();
        }
        return type.code();
    }

    public TransactionDraft withChanges(TransactionChanges changes, TransactionMetadata changedMetadata) {
        return new TransactionDraft(
                type,
                externalId,
                currency,
                changes.occurredAt() != null ? changes.occurredAt() : occurredAt,
                changes.total() != null ? changes.total() : total,
                changes.taxLines() != null ? changes.taxLines() : taxLines,
                changes.feeLines() != null ? changes.feeLines() : feeLines,
                changedMetadata != null ? changedMetadata : metadata);
    }
}
