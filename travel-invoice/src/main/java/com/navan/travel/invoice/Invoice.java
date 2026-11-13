package com.navan.travel.invoice;

import java.time.Instant;

import com.navan.travel.invoice.snapshot.InvoiceSnapshot;

public record Invoice(
        long id,
        InvoiceSnapshot snapshot,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
