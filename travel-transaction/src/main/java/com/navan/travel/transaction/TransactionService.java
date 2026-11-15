package com.navan.travel.transaction;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.navan.travel.common.ConflictException;
import com.navan.travel.common.NotFoundException;
import com.navan.travel.common.Page;
import com.navan.travel.common.PagePosition;
import com.navan.travel.common.PageQuery;
import com.navan.travel.common.PublicIds;
import com.navan.travel.common.TransientFailureRetry;
import com.navan.travel.common.shard.Shard;
import com.navan.travel.common.shard.Shards;
import com.navan.travel.transaction.identity.DuplicateTransactionException;
import com.navan.travel.transaction.identity.ExternalIdentity;
import com.navan.travel.transaction.persistence.TransactionLoader;
import com.navan.travel.transaction.persistence.TransactionWriter;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionDraftFactory draftFactory;
    private final TransactionWriter writer;
    private final TransactionLoader loader;
    private final ExternalIdentity identity;
    private final InvoiceUsage invoiceUsage;
    private final Shards shards;
    private final TransientFailureRetry retry;

    TransactionService(TransactionDraftFactory draftFactory,
                       TransactionWriter writer,
                       TransactionLoader loader,
                       ExternalIdentity identity,
                       InvoiceUsage invoiceUsage,
                       Shards shards,
                       TransientFailureRetry retry) {
        this.draftFactory = draftFactory;
        this.writer = writer;
        this.loader = loader;
        this.identity = identity;
        this.invoiceUsage = invoiceUsage;
        this.shards = shards;
        this.retry = retry;
    }

    public Transaction create(NewTransaction input) {
        if (input.externalId() != null) {
            rejectIfTaken(input.externalId());
        }
        TransactionDraft draft = draftFactory.fromNew(input);
        Shard owner = shards.forExternalId(draft.externalId());
        return retry.run(() -> insertOrRejectDuplicate(owner, draft));
    }

    public Transaction get(long id) {
        return shards.read(shardOf(id), () -> loader.findById(id)).orElseThrow(() -> notFound(id));
    }

    public Page<Transaction> list(PageQuery query) {
        List<List<Transaction>> rowsPerShard = shards.all().stream()
                .map(shard -> shards.read(shard, () -> loader.findPageRows(query)))
                .toList();
        return Page.mergeOverfetched(rowsPerShard, query,
                transaction -> new PagePosition(transaction.createdAt(), transaction.id()));
    }

    public Transaction update(long id, TransactionChanges changes) {
        return retry.run(() -> shards.inTransaction(shardOf(id), () -> {
            lockUnbilled(id);
            Transaction current = get(id);
            writer.replace(id, draftFactory.withChanges(current, changes));
            return get(id);
        }));
    }

    /** Row first, uniqueness record after: a record left behind points at nothing and the next create clears it. */
    public void delete(long id) {
        String externalId = retry.run(() -> shards.inTransaction(shardOf(id), () -> {
            lockUnbilled(id);
            String deletedExternalId = get(id).externalId();
            writer.delete(id);
            return deletedExternalId;
        }));
        for (Shard shard : shards.identityShards(externalId)) {
            try {
                shards.inTransaction(shard, () -> identity.forget(externalId, id));
            } catch (RuntimeException e) {
                log.warn("Could not remove the external_id record of deleted transaction {} on {}", id, shard, e);
            }
        }
    }

    /** Call inside a transaction on the shard these ids live on. */
    public List<Transaction> lockForInvoicing(List<Long> ids) {
        List<Long> lockedIds = writer.lockByIds(ids.stream().sorted().toList());
        if (lockedIds.size() != ids.size()) {
            throw missingTransactions(ids, lockedIds);
        }
        Map<Long, Transaction> byId = loader.findAllByIds(ids).stream()
                .collect(Collectors.toMap(Transaction::id, Function.identity()));
        return ids.stream().map(byId::get).toList();
    }

    private Shard shardOf(long id) {
        return shards.findForId(id).orElseThrow(() -> notFound(id));
    }

    private void rejectIfTaken(String externalId) {
        for (Shard shard : shards.identityShards(externalId)) {
            Optional<Long> recorded = shards.read(shard, () -> identity.findTransactionId(externalId));
            if (recorded.isEmpty()) {
                continue;
            }
            long existingId = recorded.get();
            if (exists(existingId)) {
                throw new DuplicateTransactionException(externalId, existingId);
            }
            log.info("Removing the external_id record of transaction {}, which no longer exists", existingId);
            shards.inTransaction(shard, () -> identity.forget(externalId, existingId));
        }
    }

    private boolean exists(long id) {
        return shards.findForId(id)
                .map(shard -> shards.read(shard, () -> loader.findById(id)).isPresent())
                .orElse(false);
    }

    private Transaction insertOrRejectDuplicate(Shard owner, TransactionDraft draft) {
        try {
            return shards.inTransaction(owner, () -> {
                long id = writer.insert(draft);
                identity.register(draft.externalId(), id);
                return get(id);
            });
        } catch (DuplicateKeyException e) {
            if (!identity.isExternalIdConflict(e)) {
                throw e;
            }
            log.debug("Insert for external_id {} lost the race to a concurrent create", draft.externalId());
            long existingId = shards.read(owner, () -> identity.findTransactionId(draft.externalId()))
                    .orElseThrow(() -> new ConcurrencyFailureException(
                            "Conflicting insert for external_id is not visible yet", e));
            throw new DuplicateTransactionException(draft.externalId(), existingId);
        }
    }

    private void lockUnbilled(long id) {
        if (!writer.lockById(id)) {
            throw notFound(id);
        }
        if (invoiceUsage.isOnAnyInvoice(id)) {
            throw new ConflictException("transaction_invoiced",
                    "Transaction " + PublicIds.transaction(id) + " is on an invoice and can no longer change");
        }
    }

    private static NotFoundException missingTransactions(List<Long> requestedIds, List<Long> foundIds) {
        Set<Long> found = new HashSet<>(foundIds);
        String missing = requestedIds.stream()
                .filter(id -> !found.contains(id))
                .map(PublicIds::transaction)
                .collect(Collectors.joining(", "));
        return new NotFoundException("Transactions not found: " + missing);
    }

    private static NotFoundException notFound(long id) {
        return new NotFoundException("Transaction " + PublicIds.transaction(id) + " not found");
    }
}
