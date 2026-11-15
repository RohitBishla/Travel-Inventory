package com.navan.travel.invoice.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Links live on the transaction's shard and say "this invoice may bill me". No link means not billed. A link the
 * invoice doesn't back up is a leftover from a failed write and gets removed when found.
 */
@Repository
public class InvoiceLinkRepository {

    private final JdbcClient jdbc;

    public InvoiceLinkRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void add(long invoiceId, Collection<Long> transactionIds) {
        for (long transactionId : transactionIds) {
            jdbc.sql("INSERT IGNORE INTO invoice_links (transaction_id, invoice_id) VALUES (:transactionId, :invoiceId)")
                    .param("transactionId", transactionId)
                    .param("invoiceId", invoiceId)
                    .update();
        }
    }

    public void remove(long invoiceId, Collection<Long> transactionIds) {
        if (transactionIds.isEmpty()) {
            return;
        }
        jdbc.sql("DELETE FROM invoice_links WHERE invoice_id = :invoiceId AND transaction_id IN (:transactionIds)")
                .param("invoiceId", invoiceId)
                .param("transactionIds", transactionIds)
                .update();
    }

    public List<Long> invoiceIds(long transactionId) {
        return jdbc.sql("SELECT invoice_id FROM invoice_links WHERE transaction_id = :transactionId ORDER BY invoice_id")
                .param("transactionId", transactionId)
                .query(Long.class)
                .list();
    }
}
