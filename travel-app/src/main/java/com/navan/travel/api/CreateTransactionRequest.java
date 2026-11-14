package com.navan.travel.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.navan.travel.transaction.FeeLine;
import com.navan.travel.transaction.NewTransaction;
import com.navan.travel.transaction.TaxLine;
import com.navan.travel.transaction.TransactionType;

import tools.jackson.databind.JsonNode;

public record CreateTransactionRequest(
        TransactionType type,
        String externalId,
        String currency,
        Instant occurredAt,
        BigDecimal total,
        List<TaxLine> taxLines,
        List<FeeLine> feeLines,
        JsonNode metadata) {

    NewTransaction toCommand() {
        return new NewTransaction(type, externalId, currency, occurredAt, total, taxLines, feeLines, metadata);
    }
}
