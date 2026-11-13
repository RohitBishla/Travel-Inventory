package com.navan.travel.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.navan.travel.common.PublicIds;
import com.navan.travel.invoice.Invoice;
import com.navan.travel.invoice.snapshot.InvoiceLine;
import com.navan.travel.invoice.snapshot.InvoiceSnapshot;
import com.navan.travel.transaction.FeeLine;
import com.navan.travel.transaction.TaxLine;
import com.navan.travel.transaction.TransactionType;

public record InvoiceResponse(
        String id,
        String currency,
        BigDecimal total,
        Map<String, BigDecimal> totals,
        List<Line> lines,
        List<TaxLine> taxRollup,
        List<FeeLine> feeRollup,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    static InvoiceResponse from(Invoice invoice) {
        InvoiceSnapshot snapshot = invoice.snapshot();
        Map<String, BigDecimal> totals = new LinkedHashMap<>(snapshot.totalsByCategory());
        totals.put("grand_total", snapshot.total());
        return new InvoiceResponse(
                PublicIds.invoice(invoice.id()),
                snapshot.currency(),
                snapshot.total(),
                totals,
                snapshot.lines().stream().map(Line::from).toList(),
                snapshot.taxRollup(),
                snapshot.feeRollup(),
                invoice.version(),
                invoice.createdAt(),
                invoice.updatedAt());
    }

    public record Line(
            String transactionId,
            String externalId,
            TransactionType type,
            String category,
            String currency,
            Instant occurredAt,
            BigDecimal total,
            long transactionVersion,
            List<TaxLine> taxLines,
            List<FeeLine> feeLines,
            @JsonRawValue String metadata) {

        static Line from(InvoiceLine line) {
            return new Line(
                    PublicIds.transaction(line.transactionId()),
                    line.externalId(),
                    line.type(),
                    line.category(),
                    line.currency(),
                    line.occurredAt(),
                    line.total(),
                    line.transactionVersion(),
                    line.taxLines(),
                    line.feeLines(),
                    line.metadataJson());
        }
    }
}
