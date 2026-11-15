package com.navan.travel.invoice;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.navan.travel.common.shard.Shards;
import com.navan.travel.invoice.persistence.InvoiceLinkRepository;
import com.navan.travel.invoice.persistence.InvoiceSnapshotRepository;
import com.navan.travel.transaction.InvoiceUsage;

/** Caller holds the transaction's row lock. Links name candidate invoices; the invoice's shard has the final say. */
@Component
class BilledTransactionCheck implements InvoiceUsage {

    private static final Logger log = LoggerFactory.getLogger(BilledTransactionCheck.class);

    private final Shards shards;
    private final InvoiceLinkRepository links;
    private final InvoiceSnapshotRepository snapshots;

    BilledTransactionCheck(Shards shards, InvoiceLinkRepository links, InvoiceSnapshotRepository snapshots) {
        this.shards = shards;
        this.links = links;
        this.snapshots = snapshots;
    }

    @Override
    public boolean isOnAnyInvoice(long transactionId) {
        for (long invoiceId : links.invoiceIds(transactionId)) {
            boolean billed = shards.findForId(invoiceId)
                    .map(home -> shards.inTransaction(home, () -> snapshots.billsTransaction(invoiceId, transactionId)))
                    .orElse(false);
            if (billed) {
                return true;
            }
            log.info("Removing leftover link from transaction {} to invoice {}", transactionId, invoiceId);
            links.remove(invoiceId, List.of(transactionId));
        }
        return false;
    }
}
