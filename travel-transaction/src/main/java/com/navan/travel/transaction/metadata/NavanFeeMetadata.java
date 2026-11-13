package com.navan.travel.transaction.metadata;

import java.math.BigDecimal;
import java.util.function.UnaryOperator;

import com.navan.travel.transaction.TransactionType;

public record NavanFeeMetadata(FeeKind feeKind, String description, String relatedExternalId)
        implements TransactionMetadata {

    @Override
    public TransactionType type() {
        return TransactionType.NAVAN_FEE;
    }

    @Override
    public NavanFeeMetadata mapAmounts(UnaryOperator<BigDecimal> amountMapper) {
        return this;
    }
}
