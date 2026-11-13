package com.navan.travel.transaction;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.navan.travel.common.ConflictException;
import com.navan.travel.common.NotFoundException;
import com.navan.travel.common.Page;
import com.navan.travel.common.PageQuery;
import com.navan.travel.common.PublicIds;
import com.navan.travel.common.TransientFailureRetry;
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
    private final TransactionTemplate transactionTemplate;
    private final TransientFailureRetry retry;

    TransactionService(TransactionDraftFactory draftFactory,
                       TransactionWriter writer,
                       TransactionLoader loader,
                       ExternalIdentity identity,
                       InvoiceUsage invoiceUsage,
                       TransactionTemplate transactionTemplate,
                       TransientFailureRetry retry) {
        this.draftFactory = draftFactory;
        this.writer = writer;
        this.loader = loader;
        this.identity = identity;
        this.invoiceUsage = invoiceUsage;
        this.transactionTemplate = transactionTemplate;
        this.retry = retry;
    }

    public Transaction create(NewTransaction input) {
        identity.rejectIfTaken(input.externalId());
        TransactionDraft draft = draftFactory.fromNew(input);
        return retry.run(() -> insertOrRejectDuplicate(draft));
    }

    public Transaction get(long id) {
        return loader.findById(id).orElseThrow(() -> notFound(id));
    }

    public Page<Transaction> list(PageQuery query) {
        return loader.findPage(query);
    }

    public Transaction update(long id, TransactionChanges changes) {
        return retry.run(() -> transactionTemplate.execute(status -> {
            lockUnbilled(id);
            Transaction current = get(id);
            writer.replace(id, draftFactory.withChanges(current, changes));
            return get(id);
        }));
    }

    public void delete(long id) {
        retry.runWithoutResult(() -> transactionTemplate.executeWithoutResult(status -> {
            lockUnbilled(id);
            writer.delete(id);
        }));
    }

    public List<Transaction> lockForInvoicing(List<Long> ids) {
        List<Long> lockedIds = writer.lockByIds(ids.stream().sorted().toList());
        if (lockedIds.size() != ids.size()) {
            throw missingTransactions(ids, lockedIds);
        }
        Map<Long, Transaction> byId = loader.findAllByIds(ids).stream()
                .collect(Collectors.toMap(Transaction::id, Function.identity()));
        return ids.stream().map(byId::get).toList();
    }

    private Transaction insertOrRejectDuplicate(TransactionDraft draft) {
        try {
            return transactionTemplate.execute(status -> get(writer.insert(draft)));
        } catch (DuplicateKeyException e) {
            if (!identity.isExternalIdConflict(e)) {
                throw e;
            }
            log.debug("Insert for external_id {} lost the race to a concurrent create", draft.externalId());
            long existingId = identity.findTransactionId(draft.externalId())
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
