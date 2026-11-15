package com.navan.travel.invoice.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@code invoice_links} sits on each transaction's own shard and says "this transaction may be billed by that
 * invoice". It is written before the invoice commits and removed after the invoice lets go, so a missing link
 * always means "not billed". A link whose invoice does not bill the transaction is a leftover and is removed
 * when found.
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
