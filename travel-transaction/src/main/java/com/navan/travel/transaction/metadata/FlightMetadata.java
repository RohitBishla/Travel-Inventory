package com.navan.travel.transaction.metadata;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.UnaryOperator;

import com.navan.travel.transaction.TransactionType;

public record FlightMetadata(String origin, String destination, BigDecimal fare, List<FlightCoupon> coupons)
        implements TransactionMetadata {

    @Override
    public TransactionType type() {
        return TransactionType.FLIGHT;
    }

    @Override
    public FlightMetadata mapAmounts(UnaryOperator<BigDecimal> amountMapper) {
        return new FlightMetadata(origin, destination, amountMapper.apply(fare), coupons);
    }
}
