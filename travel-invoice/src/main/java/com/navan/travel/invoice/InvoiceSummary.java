package com.navan.travel.invoice;

import java.math.BigDecimal;
import java.time.Instant;

public record InvoiceSummary(
        long id,
        String currency,
        BigDecimal total,
        int lineCount,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
