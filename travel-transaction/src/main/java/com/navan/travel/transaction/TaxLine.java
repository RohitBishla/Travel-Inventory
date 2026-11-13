package com.navan.travel.transaction;

import java.math.BigDecimal;

public record TaxLine(String name, BigDecimal rate, BigDecimal amount) {
}
