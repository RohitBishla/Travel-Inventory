package com.navan.travel.transaction.metadata;

import java.math.BigDecimal;
import java.util.function.UnaryOperator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.navan.travel.transaction.TransactionType;

public sealed interface TransactionMetadata
        permits FlightMetadata, HotelMetadata, RailMetadata, NavanFeeMetadata {

    @JsonIgnore
    TransactionType type();

    TransactionMetadata mapAmounts(UnaryOperator<BigDecimal> amountMapper);
}
