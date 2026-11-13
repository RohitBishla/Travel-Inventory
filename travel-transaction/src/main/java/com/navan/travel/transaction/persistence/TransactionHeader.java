package com.navan.travel.transaction.persistence;

import java.math.BigDecimal;
import java.time.Instant;

import com.navan.travel.transaction.TransactionType;

record TransactionHeader(
        long id,
        TransactionType type,
        String externalId,
        String currency,
        Instant occurredAt,
        BigDecimal total,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
