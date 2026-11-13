package com.navan.travel.invoice.snapshot;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.navan.travel.common.Money;
import com.navan.travel.transaction.FeeLine;
import com.navan.travel.transaction.TaxLine;

public final class RollupCalculator {

    private RollupCalculator() {
    }

    public static List<TaxLine> rollUpTaxes(List<TaxLine> taxLines) {
        Map<TaxGroup, BigDecimal> amountByGroup = new LinkedHashMap<>();
        for (TaxLine line : taxLines) {
            TaxGroup group = new TaxGroup(line.name().trim(), Money.normalizeRate(line.rate()));
            amountByGroup.merge(group, line.amount(), BigDecimal::add);
        }
        return amountByGroup.entrySet().stream()
                .map(entry -> new TaxLine(entry.getKey().name(), entry.getKey().rate(), entry.getValue()))
                .toList();
    }

    public static List<FeeLine> rollUpFees(List<FeeLine> feeLines) {
        Map<String, BigDecimal> amountByName = new LinkedHashMap<>();
        for (FeeLine line : feeLines) {
            amountByName.merge(line.name().trim(), line.amount(), BigDecimal::add);
        }
        return amountByName.entrySet().stream()
                .map(entry -> new FeeLine(entry.getKey(), entry.getValue()))
                .toList();
    }

    public static Map<String, BigDecimal> totalsByCategory(List<InvoiceLine> lines) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (InvoiceLine line : lines) {
            totals.merge(line.category(), line.total(), BigDecimal::add);
        }
        return totals;
    }

    public static BigDecimal grandTotal(List<InvoiceLine> lines) {
        return lines.stream().map(InvoiceLine::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private record TaxGroup(String name, BigDecimal rate) {
    }
}
