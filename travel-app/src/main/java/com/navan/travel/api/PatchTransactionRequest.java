package com.navan.travel.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.navan.travel.common.ValidationException;
import com.navan.travel.transaction.FeeLine;
import com.navan.travel.transaction.TaxLine;
import com.navan.travel.transaction.TransactionChanges;

import tools.jackson.databind.JsonNode;

public record PatchTransactionRequest(
        String type,
        String externalId,
        String currency,
        Instant occurredAt,
        BigDecimal total,
        List<TaxLine> taxLines,
        List<FeeLine> feeLines,
        JsonNode metadata) {

    TransactionChanges toChanges() {
        if (type != null || externalId != null || currency != null) {
            throw new ValidationException("type, external_id and currency cannot be changed");
        }
        return new TransactionChanges(occurredAt, total, taxLines, feeLines, metadata);
    }
}
