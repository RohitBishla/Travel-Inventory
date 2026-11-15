package com.navan.travel.invoice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.navan.travel.common.ConflictException;
import com.navan.travel.common.NotFoundException;
import com.navan.travel.common.Page;
import com.navan.travel.common.PagePosition;
import com.navan.travel.common.PageQuery;
import com.navan.travel.common.PublicIds;
import com.navan.travel.common.TransientFailureRetry;
import com.navan.travel.common.ValidationException;
import com.navan.travel.common.shard.Shard;
import com.navan.travel.common.shard.Shards;
import com.navan.travel.invoice.persistence.InvoiceLinkRepository;
import com.navan.travel.invoice.persistence.InvoiceRepository;
import com.navan.travel.invoice.persistence.InvoiceSnapshotRepository;
import com.navan.travel.invoice.snapshot.InvoiceSnapshot;
import com.navan.travel.invoice.snapshot.InvoiceSnapshotBuilder;
import com.navan.travel.transaction.Transaction;
import com.navan.travel.transaction.TransactionService;

/**
 * An invoice lives on the lowest shard among its transactions. Writes open one transaction per shard, in shard
 * order, and the inner ones commit first: links to an invoice exist before it does and go only after it does.
 * That keeps "is it billed?" right without a distributed commit.
 */
@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);
    private static final int MAX_TRANSACTIONS_PER_INVOICE = 100;

    private final InvoiceRepository invoices;
    private final InvoiceSnapshotRepository snapshots;
    private final InvoiceLinkRepository links;
    private final InvoiceSnapshotBuilder snapshotBuilder;
    private final TransactionService transactionService;
    private final Shards shards;
    private final TransientFailureRetry retry;

    InvoiceService(InvoiceRepository invoices,
                   InvoiceSnapshotRepository snapshots,
                   InvoiceLinkRepository links,
                   InvoiceSnapshotBuilder snapshotBuilder,
                   TransactionService transactionService,
                   Shards shards,
                   TransientFailureRetry retry) {
        this.invoices = invoices;
        this.snapshots = snapshots;
        this.links = links;
        this.snapshotBuilder = snapshotBuilder;
        this.transactionService = transactionService;
        this.shards = shards;
        this.retry = retry;
    }

    public Invoice create(List<Long> transactionIds) {
        validateTransactionIds(transactionIds);
        SortedMap<Shard, List<Long>> idsByShard = groupByShard(transactionIds);
        Shard home = idsByShard.firstKey();
        long invoiceId = retry.run(() -> withLockedTransactions(idsByShard, locked -> {
            InvoiceSnapshot snapshot = snapshotBuilder.build(inRequestOrder(transactionIds, locked));
            long newId = shards.inTransaction(home, () -> {
                long id = invoices.insert(snapshot);
                snapshots.insert(id, snapshot);
                return id;
            });
            link(newId, idsByShard);
            return newId;
        }));
        return get(invoiceId);
    }

    public Invoice get(long id) {
        return shards.read(homeOf(id), () -> load(id));
    }

    public Page<InvoiceSummary> list(PageQuery query) {
        List<List<InvoiceSummary>> rowsPerShard = shards.all().stream()
                .map(shard -> shards.read(shard, () -> invoices.findSummaryPage(query)))
                .toList();
        return Page.mergeOverfetched(rowsPerShard, query,
                summary -> new PagePosition(summary.createdAt(), summary.id()));
    }

    public Invoice replaceTransactions(long id, List<Long> transactionIds) {
        validateTransactionIds(transactionIds);
        Shard home = homeOf(id);
        SortedMap<Shard, List<Long>> idsByShard = groupByShard(transactionIds);
        Set<Long> kept = Set.copyOf(transactionIds);
        List<Long> released = retry.run(() -> shards.inTransaction(home, () -> {
            if (!invoices.lockById(id)) {
                throw notFound(id);
            }
            List<Long> previous = snapshots.transactionIds(id);
            return withLockedTransactions(idsByShard, locked -> {
                InvoiceSnapshot snapshot = snapshotBuilder.build(inRequestOrder(transactionIds, locked));
                shards.inTransaction(home, () -> {
                    snapshots.delete(id);
                    snapshots.insert(id, snapshot);
                    invoices.update(id, snapshot);
                });
                link(id, idsByShard);
                return previous.stream().filter(previousId -> !kept.contains(previousId)).toList();
            });
        }));
        unlink(id, released);
        return get(id);
    }

    public void delete(long id) {
        List<Long> released = retry.run(() -> shards.inTransaction(homeOf(id), () -> {
            if (!invoices.lockById(id)) {
                throw notFound(id);
            }
            List<Long> billed = snapshots.transactionIds(id);
            invoices.delete(id);
            return billed;
        }));
        unlink(id, released);
    }

    private <T> T withLockedTransactions(SortedMap<Shard, List<Long>> idsByShard,
                                         Function<List<Transaction>, T> action) {
        return lockShardByShard(List.copyOf(idsByShard.entrySet()), new ArrayList<>(), action);
    }

    private <T> T lockShardByShard(List<Map.Entry<Shard, List<Long>>> remaining,
                                   List<Transaction> locked,
                                   Function<List<Transaction>, T> action) {
        if (remaining.isEmpty()) {
            return action.apply(locked);
        }
        Map.Entry<Shard, List<Long>> next = remaining.getFirst();
        return shards.inTransaction(next.getKey(), () -> {
            locked.addAll(transactionService.lockForInvoicing(next.getValue()));
            return lockShardByShard(remaining.subList(1, remaining.size()), locked, action);
        });
    }

    private void link(long invoiceId, SortedMap<Shard, List<Long>> idsByShard) {
        idsByShard.forEach((shard, ids) -> shards.inTransaction(shard, () -> links.add(invoiceId, ids)));
    }

    private void unlink(long invoiceId, List<Long> transactionIds) {
        groupByShard(transactionIds).forEach((shard, ids) -> {
            try {
                shards.inTransaction(shard, () -> links.remove(invoiceId, ids));
            } catch (RuntimeException e) {
                log.warn("Could not unlink invoice {} on {}; the leftover links are removed when next checked",
                        invoiceId, shard, e);
            }
        });
    }

    private Shard homeOf(long invoiceId) {
        return shards.findForId(invoiceId).orElseThrow(() -> notFound(invoiceId));
    }

    private SortedMap<Shard, List<Long>> groupByShard(List<Long> transactionIds) {
        String unknown = transactionIds.stream()
                .filter(id -> shards.findForId(id).isEmpty())
                .map(PublicIds::transaction)
                .collect(Collectors.joining(", "));
        if (!unknown.isEmpty()) {
            throw new NotFoundException("Transactions not found: " + unknown);
        }
        return transactionIds.stream().collect(Collectors.groupingBy(
                shards::forId,
                () -> new TreeMap<>(Comparator.comparingInt(Shard::id)),
                Collectors.toList()));
    }

    private static List<Transaction> inRequestOrder(List<Long> transactionIds, List<Transaction> locked) {
        Map<Long, Transaction> byId = locked.stream()
                .collect(Collectors.toMap(Transaction::id, Function.identity()));
        return transactionIds.stream().map(byId::get).toList();
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
