package com.navan.travel.api;

import java.math.BigDecimal;
import java.time.Instant;

import com.navan.travel.common.PublicIds;
import com.navan.travel.invoice.InvoiceSummary;

public record InvoiceSummaryResponse(
        String id,
        String currency,
        BigDecimal total,
        int lineCount,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    static InvoiceSummaryResponse from(InvoiceSummary summary) {
        return new InvoiceSummaryResponse(
                PublicIds.invoice(summary.id()),
                summary.currency(),
                summary.total(),
                summary.lineCount(),
                summary.version(),
                summary.createdAt(),
                summary.updatedAt());
    }
}
