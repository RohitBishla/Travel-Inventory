package com.navan.travel.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void knowsTheMinorUnitsOfSupportedCurrencies() {
        assertThat(Money.minorUnits("EUR")).contains(2);
        assertThat(Money.minorUnits("JPY")).contains(0);
        assertThat(Money.minorUnits("eur")).isEmpty();
        assertThat(Money.minorUnits("XYZ")).isEmpty();
        assertThat(Money.minorUnits(null)).isEmpty();
    }

    @Test
    void rendersStoredAmountsAtCurrencyScaleWithoutRounding() {
        assertThat(Money.toCurrencyScale(new BigDecimal("220.0000"), "EUR")).isEqualTo(new BigDecimal("220.00"));
        assertThatThrownBy(() -> Money.toCurrencyScale(new BigDecimal("1.2345"), "EUR"))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void normalizesRatesToTheirShortestPlainForm() {
        assertThat(Money.normalizeRate(new BigDecimal("0.070000"))).isEqualTo(new BigDecimal("0.07"));
        assertThat(Money.normalizeRate(new BigDecimal("1.000000"))).isEqualTo(new BigDecimal("1"));
        assertThat(Money.normalizeRate(null)).isNull();
    }
}
