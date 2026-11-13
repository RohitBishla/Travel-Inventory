package com.navan.travel.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.navan.travel.transaction.metadata.TransactionMetadata;

public record TransactionDraft(
        TransactionType type,
        String externalId,
        String currency,
        Instant occurredAt,
        BigDecimal total,
        List<TaxLine> taxLines,
        List<FeeLine> feeLines,
        TransactionMetadata metadata) {
}
