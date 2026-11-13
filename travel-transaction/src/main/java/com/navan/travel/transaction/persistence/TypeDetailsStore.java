package com.navan.travel.transaction.persistence;

import java.util.Collection;
import java.util.Map;

import com.navan.travel.transaction.metadata.TransactionMetadata;

interface TypeDetailsStore<M extends TransactionMetadata> {

    void insert(long transactionId, M metadata);

    void delete(long transactionId);

    Map<Long, M> findByTransactionIds(Collection<Long> transactionIds);
}
