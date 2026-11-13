package com.navan.travel.invoice.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.navan.travel.transaction.FeeLine;
import com.navan.travel.transaction.TaxLine;

class RollupCalculatorTest {

    @Test
    void groupsTaxesByNameAndRateInFirstSeenOrder() {
        List<TaxLine> taxLines = List.of(
                new TaxLine("VAT", new BigDecimal("0.07"), new BigDecimal("21.70")),
                new TaxLine("Airport tax", null, new BigDecimal("15.00")),
                new TaxLine("VAT", new BigDecimal("0.070000"), new BigDecimal("3.30")),
                new TaxLine("VAT", new BigDecimal("0.19"), new BigDecimal("19.00")),
                new TaxLine(" Airport tax ", null, new BigDecimal("5.00")));

        List<TaxLine> rollup = RollupCalculator.rollUpTaxes(taxLines);

        assertThat(rollup).containsExactly(
                new TaxLine("VAT", new BigDecimal("0.07"), new BigDecimal("25.00")),
                new TaxLine("Airport tax", null, new BigDecimal("20.00")),
                new TaxLine("VAT", new BigDecimal("0.19"), new BigDecimal("19.00")));
    }

    @Test
    void groupsFeesByName() {
        List<FeeLine> rollup = RollupCalculator.rollUpFees(List.of(
                new FeeLine("YQ", new BigDecimal("25.00")),
                new FeeLine("Booking fee", new BigDecimal("4.00")),
                new FeeLine("YQ", new BigDecimal("10.00"))));

        assertThat(rollup).containsExactly(
                new FeeLine("YQ", new BigDecimal("35.00")),
                new FeeLine("Booking fee", new BigDecimal("4.00")));
    }

    @Test
    void sumsManySmallAmountsExactly() {
        List<FeeLine> tenCents = Collections.nCopies(1000, new FeeLine("Fee", new BigDecimal("0.10")));

        assertThat(RollupCalculator.rollUpFees(tenCents).getFirst().amount()).isEqualTo(new BigDecimal("100.00"));
    }
}
