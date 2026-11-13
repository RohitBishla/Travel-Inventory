package com.navan.travel.transaction.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.navan.travel.common.Money;
import com.navan.travel.common.Page;
import com.navan.travel.common.PageQuery;
import com.navan.travel.transaction.FeeLine;
import com.navan.travel.transaction.TaxLine;
import com.navan.travel.transaction.Transaction;
import com.navan.travel.transaction.TransactionType;
import com.navan.travel.transaction.metadata.TransactionMetadata;

@Component
public class TransactionLoader {

    private final TransactionRepository transactions;
    private final ChargeLineRepository chargeLines;
    private final MetadataRepository metadata;

    TransactionLoader(TransactionRepository transactions, ChargeLineRepository chargeLines,
                      MetadataRepository metadata) {
        this.transactions = transactions;
        this.chargeLines = chargeLines;
        this.metadata = metadata;
    }

    public Optional<Transaction> findById(long id) {
        return findAllByIds(List.of(id)).stream().findFirst();
    }

    public List<Transaction> findAllByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return hydrate(transactions.findHeaders(ids));
    }

    public Page<Transaction> findPage(PageQuery query) {
        List<Transaction> rows = hydrate(transactions.findHeaderPage(query));
        return Page.fromOverfetched(rows, query, Transaction::id);
    }

    private List<Transaction> hydrate(List<TransactionHeader> headers) {
        if (headers.isEmpty()) {
            return List.of();
        }
        List<Long> ids = headers.stream().map(TransactionHeader::id).toList();
        Map<TransactionType, List<Long>> idsByType = headers.stream().collect(Collectors.groupingBy(
                TransactionHeader::type,
                Collectors.mapping(TransactionHeader::id, Collectors.toList())));

        Map<Long, List<TaxLine>> taxLines = chargeLines.findTaxLines(ids);
        Map<Long, List<FeeLine>> feeLines = chargeLines.findFeeLines(ids);
        Map<Long, TransactionMetadata> metadataById = metadata.findByType(idsByType);

        return headers.stream()
                .map(header -> assemble(header,
                        taxLines.getOrDefault(header.id(), List.of()),
                        feeLines.getOrDefault(header.id(), List.of()),
                        metadataById.get(header.id())))
                .toList();
    }

    private Transaction assemble(TransactionHeader header, List<TaxLine> taxLines, List<FeeLine> feeLines,
                                 TransactionMetadata metadata) {
        if (metadata == null) {
            throw new IllegalStateException("Transaction " + header.id() + " has no " + header.type().code() + " details");
        }
        String currency = header.currency();
        return new Transaction(
                header.id(),
                header.type(),
                header.externalId(),
                currency,
                header.occurredAt(),
                Money.toCurrencyScale(header.total(), currency),
                taxLines.stream()
                        .map(line -> new TaxLine(line.name(), line.rate(), Money.toCurrencyScale(line.amount(), currency)))
                        .toList(),
                feeLines.stream()
                        .map(line -> new FeeLine(line.name(), Money.toCurrencyScale(line.amount(), currency)))
                        .toList(),
                metadata.mapAmounts(amount -> Money.toCurrencyScale(amount, currency)),
                header.version(),
                header.createdAt(),
                header.updatedAt());
    }
}
