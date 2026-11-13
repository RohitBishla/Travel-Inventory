package com.navan.travel.invoice;

import java.util.HashSet;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.navan.travel.common.ConflictException;
import com.navan.travel.common.NotFoundException;
import com.navan.travel.common.Page;
import com.navan.travel.common.PageQuery;
import com.navan.travel.common.PublicIds;
import com.navan.travel.common.TransientFailureRetry;
import com.navan.travel.common.ValidationException;
import com.navan.travel.invoice.persistence.InvoiceRepository;
import com.navan.travel.invoice.persistence.InvoiceSnapshotRepository;
import com.navan.travel.invoice.snapshot.InvoiceSnapshot;
import com.navan.travel.invoice.snapshot.InvoiceSnapshotBuilder;
import com.navan.travel.transaction.TransactionService;

@Service
public class InvoiceService {

    private static final int MAX_TRANSACTIONS_PER_INVOICE = 100;

    private final InvoiceRepository invoices;
    private final InvoiceSnapshotRepository snapshots;
    private final InvoiceSnapshotBuilder snapshotBuilder;
    private final TransactionService transactionService;
    private final TransactionTemplate transactionTemplate;
    private final TransientFailureRetry retry;

    InvoiceService(InvoiceRepository invoices,
                          InvoiceSnapshotRepository snapshots,
                          InvoiceSnapshotBuilder snapshotBuilder,
                          TransactionService transactionService,
                          TransactionTemplate transactionTemplate,
                          TransientFailureRetry retry) {
        this.invoices = invoices;
        this.snapshots = snapshots;
        this.snapshotBuilder = snapshotBuilder;
        this.transactionService = transactionService;
        this.transactionTemplate = transactionTemplate;
        this.retry = retry;
    }

    public Invoice create(List<Long> transactionIds) {
        validateTransactionIds(transactionIds);
        return retry.run(() -> transactionTemplate.execute(status -> {
            InvoiceSnapshot snapshot = snapshotOf(transactionIds);
            long invoiceId = invoices.insert(snapshot);
            snapshots.insert(invoiceId, snapshot);
            return load(invoiceId);
        }));
    }

    public Invoice get(long id) {
        return load(id);
    }

    public Page<InvoiceSummary> list(PageQuery query) {
        return Page.fromOverfetched(invoices.findSummaryPage(query), query, InvoiceSummary::id);
    }

    public Invoice replaceTransactions(long id, List<Long> transactionIds) {
        validateTransactionIds(transactionIds);
        return retry.run(() -> transactionTemplate.execute(status -> {
            if (!invoices.lockById(id)) {
                throw notFound(id);
            }
            InvoiceSnapshot snapshot = snapshotOf(transactionIds);
            snapshots.delete(id);
            snapshots.insert(id, snapshot);
            invoices.update(id, snapshot);
            return load(id);
        }));
    }

    public void delete(long id) {
        retry.runWithoutResult(() -> transactionTemplate.executeWithoutResult(status -> {
            if (!invoices.delete(id)) {
                throw notFound(id);
            }
        }));
    }

    private InvoiceSnapshot snapshotOf(List<Long> transactionIds) {
        return snapshotBuilder.build(transactionService.lockForInvoicing(transactionIds));
    }

    private Invoice load(long id) {
        InvoiceSummary summary = invoices.findSummary(id).orElseThrow(() -> notFound(id));
        InvoiceSnapshot snapshot = snapshots.find(id, summary.currency(), summary.total());
        return new Invoice(id, snapshot, summary.version(), summary.createdAt(), summary.updatedAt());
    }

    private static void validateTransactionIds(List<Long> transactionIds) {
        if (transactionIds.isEmpty()) {
            throw new ValidationException("An invoice needs at least one transaction id");
        }
        if (transactionIds.size() > MAX_TRANSACTIONS_PER_INVOICE) {
            throw new ValidationException(
                    "An invoice can include at most " + MAX_TRANSACTIONS_PER_INVOICE + " transactions");
        }
        if (new HashSet<>(transactionIds).size() != transactionIds.size()) {
            throw new ConflictException("duplicate_transaction_id",
                    "The same transaction id is listed more than once on this invoice");
        }
    }

    private static NotFoundException notFound(long id) {
        return new NotFoundException("Invoice " + PublicIds.invoice(id) + " not found");
    }
}
