package com.navan.travel.invoice.snapshot;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.navan.travel.transaction.FeeLine;
import com.navan.travel.transaction.TaxLine;

public record InvoiceSnapshot(
        String currency,
        BigDecimal total,
        List<InvoiceLine> lines,
        List<TaxLine> taxRollup,
        List<FeeLine> feeRollup) {

    public Map<String, BigDecimal> totalsByCategory() {
        return RollupCalculator.totalsByCategory(lines);
    }
}
