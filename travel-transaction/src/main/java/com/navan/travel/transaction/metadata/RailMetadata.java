package com.navan.travel.transaction.metadata;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.UnaryOperator;

import com.navan.travel.transaction.TransactionType;

public record RailMetadata(List<RailLeg> legs) implements TransactionMetadata {

    @Override
    public TransactionType type() {
        return TransactionType.RAIL;
    }

    @Override
    public RailMetadata mapAmounts(UnaryOperator<BigDecimal> amountMapper) {
        return this;
    }
}
