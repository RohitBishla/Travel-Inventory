package com.navan.travel.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Optional;

public final class Money {

    public static final int STORAGE_SCALE = 4;
    public static final int RATE_SCALE = 6;

    private Money() {
    }

    public static Optional<Integer> minorUnits(String currencyCode) {
        if (currencyCode == null || !currencyCode.matches("[A-Z]{3}")) {
            return Optional.empty();
        }
        try {
            int digits = Currency.getInstance(currencyCode).getDefaultFractionDigits();
            return digits >= 0 && digits <= STORAGE_SCALE ? Optional.of(digits) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static BigDecimal toCurrencyScale(BigDecimal amount, String currencyCode) {
        int scale = minorUnits(currencyCode).orElse(STORAGE_SCALE);
        return amount.setScale(scale, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal normalizeRate(BigDecimal rate) {
        if (rate == null) {
            return null;
        }
        BigDecimal stripped = rate.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
