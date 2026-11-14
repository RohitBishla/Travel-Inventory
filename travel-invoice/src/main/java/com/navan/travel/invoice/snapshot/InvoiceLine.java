package com.navan.travel.invoice.snapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.navan.travel.transaction.FeeLine;
import com.navan.travel.transaction.TaxLine;
import com.navan.travel.transaction.TransactionType;
import com.navan.travel.transaction.metadata.FeeKind;

public record InvoiceLine(
        long transactionId,
        String externalId,
        TransactionType type,
        FeeKind feeKind,
        String currency,
        Instant occurredAt,
        BigDecimal total,
        long transactionVersion,
        List<TaxLine> taxLines,
        List<FeeLine> feeLines,
        String metadataJson) {

    public String category() {
        return feeKind != null ? feeKind.code() : type.code();
    }
}
