package com.navan.travel.transaction.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Component;

import com.navan.travel.transaction.TransactionDraft;

@Component
public class TransactionWriter {

    private final TransactionRepository transactions;
    private final ChargeLineRepository chargeLines;
    private final MetadataRepository metadata;

    TransactionWriter(TransactionRepository transactions, ChargeLineRepository chargeLines,
                      MetadataRepository metadata) {
        this.transactions = transactions;
        this.chargeLines = chargeLines;
        this.metadata = metadata;
    }

    public long insert(TransactionDraft draft) {
        long id = transactions.insert(draft);
        chargeLines.insertTaxLines(id, draft.taxLines());
        chargeLines.insertFeeLines(id, draft.feeLines());
        metadata.insert(id, draft.metadata());
        return id;
    }

    public void replace(long id, TransactionDraft updated) {
        transactions.updateHeader(id, updated);
        chargeLines.replaceTaxLines(id, updated.taxLines());
        chargeLines.replaceFeeLines(id, updated.feeLines());
        metadata.replace(id, updated.metadata());
    }

    public void delete(long id) {
        transactions.delete(id);
    }

    public boolean lockById(long id) {
        return transactions.lockById(id);
    }

    public List<Long> lockByIds(Collection<Long> ids) {
        return transactions.lockByIds(ids);
    }
}
