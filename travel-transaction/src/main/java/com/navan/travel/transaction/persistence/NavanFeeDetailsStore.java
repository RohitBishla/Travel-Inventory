package com.navan.travel.transaction.persistence;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.navan.travel.common.ChildRow;
import com.navan.travel.transaction.metadata.FeeKind;
import com.navan.travel.transaction.metadata.NavanFeeMetadata;

@Repository
class NavanFeeDetailsStore implements TypeDetailsStore<NavanFeeMetadata> {

    private final JdbcClient jdbc;

    NavanFeeDetailsStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(long transactionId, NavanFeeMetadata navanFee) {
        jdbc.sql("""
                        INSERT INTO navan_fee_details (transaction_id, fee_kind, description, related_external_id)
                        VALUES (:transactionId, :feeKind, :description, :relatedExternalId)
                        """)
                .param("transactionId", transactionId)
                .param("feeKind", navanFee.feeKind().code())
                .param("description", navanFee.description())
                .param("relatedExternalId", navanFee.relatedExternalId())
                .update();
    }

    @Override
    public void delete(long transactionId) {
        jdbc.sql("DELETE FROM navan_fee_details WHERE transaction_id = :id").param("id", transactionId).update();
    }

    @Override
    public Map<Long, NavanFeeMetadata> findByTransactionIds(Collection<Long> transactionIds) {
        return jdbc.sql("""
                        SELECT transaction_id, fee_kind, description, related_external_id
                        FROM navan_fee_details
                        WHERE transaction_id IN (:ids)
                        """)
                .param("ids", transactionIds)
                .query((rs, rowNum) -> new ChildRow<>(rs.getLong("transaction_id"), new NavanFeeMetadata(
                        readFeeKind(rs.getString("fee_kind")),
                        rs.getString("description"),
                        rs.getString("related_external_id"))))
                .list()
                .stream()
                .collect(Collectors.toMap(ChildRow::parentId, ChildRow::value));
    }

    private static FeeKind readFeeKind(String code) {
        return FeeKind.fromCode(code)
                .orElseThrow(() -> new IllegalStateException("Unknown fee kind in database: " + code));
    }
}
