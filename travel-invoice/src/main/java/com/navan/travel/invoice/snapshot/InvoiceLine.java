package com.navan.travel.invoice.snapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.navan.travel.transaction.FeeLine;
import com.navan.travel.transaction.TaxLine;
import com.navan.travel.transaction.TransactionType;

public record InvoiceLine(
        long transactionId,
        String externalId,
        TransactionType type,
        String category,
        String currency,
        Instant occurredAt,
        BigDecimal total,
        long transactionVersion,
        List<TaxLine> taxLines,
        List<FeeLine> feeLines,
        String metadataJson) {
}
