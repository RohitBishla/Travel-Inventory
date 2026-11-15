package com.navan.travel.invoice.snapshot;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.navan.travel.transaction.FeeLine;
import com.navan.travel.transaction.TaxLine;
import com.navan.travel.transaction.TransactionType;
import com.navan.travel.transaction.metadata.FeeKind;

public record InvoiceSnapshot(String currency, BigDecimal total, List<InvoiceLine> lines) {

    public List<TaxLine> taxRollup() {
        return RollupCalculator.rollUpTaxes(lines.stream().flatMap(line -> line.taxLines().stream()).toList());
    }

    public List<FeeLine> feeRollup() {
        return RollupCalculator.rollUpFees(lines.stream().flatMap(line -> line.feeLines().stream()).toList());
    }

    public Map<String, BigDecimal> totalsByCategory() {
        return RollupCalculator.totalsByCategory(lines);
    }

    public List<TransactionType> transactionTypes() {
        return List.copyOf(lines.stream()
                .map(InvoiceLine::type)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TransactionType.class))));
    }

    public List<FeeKind> navanFeeKinds() {
        return List.copyOf(lines.stream()
                .map(InvoiceLine::feeKind)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(FeeKind.class))));
    }
}
