package com.navan.travel.invoice.snapshot;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.navan.travel.common.ConflictException;
import com.navan.travel.transaction.FeeLine;
import com.navan.travel.transaction.TaxLine;
import com.navan.travel.transaction.Transaction;

import tools.jackson.databind.ObjectMapper;

@Component
public class InvoiceSnapshotBuilder {

    private final ObjectMapper objectMapper;

    public InvoiceSnapshotBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public InvoiceSnapshot build(List<Transaction> transactions) {
        String currency = sharedCurrency(transactions);
        List<InvoiceLine> lines = transactions.stream().map(this::toLine).toList();
        List<TaxLine> allTaxLines = lines.stream().flatMap(line -> line.taxLines().stream()).toList();
        List<FeeLine> allFeeLines = lines.stream().flatMap(line -> line.feeLines().stream()).toList();

        return new InvoiceSnapshot(
                currency,
                RollupCalculator.grandTotal(lines),
                lines,
                RollupCalculator.rollUpTaxes(allTaxLines),
                RollupCalculator.rollUpFees(allFeeLines));
    }

    private InvoiceLine toLine(Transaction transaction) {
        return new InvoiceLine(
                transaction.id(),
                transaction.externalId(),
                transaction.type(),
                transaction.feeKind(),
                transaction.currency(),
                transaction.occurredAt(),
                transaction.total(),
                transaction.version(),
                transaction.taxLines(),
                transaction.feeLines(),
                objectMapper.writeValueAsString(transaction.metadata()));
    }

    private static String sharedCurrency(List<Transaction> transactions) {
        Set<String> currencies = transactions.stream()
                .map(Transaction::currency)
                .collect(Collectors.toCollection(TreeSet::new));
        if (currencies.size() != 1) {
            throw new ConflictException("mixed_currency",
                    "All transactions on an invoice must share one currency, found: " + String.join(", ", currencies));
        }
        return currencies.iterator().next();
    }
}
