package com.navan.travel.invoice.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.navan.travel.common.ChildRow;
import com.navan.travel.common.Money;
import com.navan.travel.common.SqlTime;
import com.navan.travel.invoice.snapshot.InvoiceLine;
import com.navan.travel.invoice.snapshot.InvoiceSnapshot;
import com.navan.travel.transaction.FeeLine;
import com.navan.travel.transaction.TaxLine;
import com.navan.travel.transaction.TransactionType;
import com.navan.travel.transaction.metadata.FeeKind;

@Repository
public class InvoiceSnapshotRepository {

    private final JdbcClient jdbc;

    public InvoiceSnapshotRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(long invoiceId, InvoiceSnapshot snapshot) {
        for (int lineNo = 0; lineNo < snapshot.lines().size(); lineNo++) {
            InvoiceLine line = snapshot.lines().get(lineNo);
            long invoiceLineId = insertLine(invoiceId, lineNo, line);
            insertTaxLines("invoice_line_tax_lines", "invoice_line_id", invoiceLineId, line.taxLines());
            insertFeeLines("invoice_line_fee_lines", "invoice_line_id", invoiceLineId, line.feeLines());
        }
        insertTaxLines("invoice_tax_rollups", "invoice_id", invoiceId, snapshot.taxRollup());
        insertFeeLines("invoice_fee_rollups", "invoice_id", invoiceId, snapshot.feeRollup());
    }

    public void delete(long invoiceId) {
        jdbc.sql("DELETE FROM invoice_lines WHERE invoice_id = :id").param("id", invoiceId).update();
        jdbc.sql("DELETE FROM invoice_tax_rollups WHERE invoice_id = :id").param("id", invoiceId).update();
        jdbc.sql("DELETE FROM invoice_fee_rollups WHERE invoice_id = :id").param("id", invoiceId).update();
    }

    public InvoiceSnapshot find(long invoiceId, String currency, BigDecimal total) {
        List<StoredLine> storedLines = findLines(invoiceId);
        List<Long> lineIds = storedLines.stream().map(StoredLine::id).toList();
        Map<Long, List<TaxLine>> taxLinesByLine = findLineTaxLines(lineIds);
        Map<Long, List<FeeLine>> feeLinesByLine = findLineFeeLines(lineIds);

        List<InvoiceLine> lines = storedLines.stream()
                .map(stored -> stored.toInvoiceLine(
                        scaleTaxes(taxLinesByLine.getOrDefault(stored.id(), List.of()), currency),
                        scaleFees(feeLinesByLine.getOrDefault(stored.id(), List.of()), currency)))
                .toList();
        List<TaxLine> taxRollup = scaleTaxes(findTaxRollup(invoiceId), currency);
        List<FeeLine> feeRollup = scaleFees(findFeeRollup(invoiceId), currency);
        return new InvoiceSnapshot(currency, total, lines, taxRollup, feeRollup);
    }

    private long insertLine(long invoiceId, int lineNo, InvoiceLine line) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.sql("""
                        INSERT INTO invoice_lines (invoice_id, line_no, transaction_id, external_id, type, category,
                                                   currency, occurred_at, total, transaction_version, metadata)
                        VALUES (:invoiceId, :lineNo, :transactionId, :externalId, :type, :category,
                                :currency, :occurredAt, :total, :transactionVersion, :metadata)
                        """)
                .param("invoiceId", invoiceId)
                .param("lineNo", lineNo)
                .param("transactionId", line.transactionId())
                .param("externalId", line.externalId())
                .param("type", line.type().code())
                .param("category", line.category())
                .param("currency", line.currency())
                .param("occurredAt", SqlTime.toSql(line.occurredAt()))
                .param("total", line.total())
                .param("transactionVersion", line.transactionVersion())
                .param("metadata", line.metadataJson())
                .update(keyHolder, "id");
        return keyHolder.getKeyAs(Long.class);
    }

    private void insertTaxLines(String table, String ownerColumn, long ownerId, List<TaxLine> taxLines) {
        for (int lineNo = 0; lineNo < taxLines.size(); lineNo++) {
            TaxLine line = taxLines.get(lineNo);
            jdbc.sql("INSERT INTO " + table + " (" + ownerColumn + ", line_no, name, rate, amount) "
                            + "VALUES (:ownerId, :lineNo, :name, :rate, :amount)")
                    .param("ownerId", ownerId)
                    .param("lineNo", lineNo)
                    .param("name", line.name())
                    .param("rate", line.rate())
                    .param("amount", line.amount())
                    .update();
        }
    }

    private void insertFeeLines(String table, String ownerColumn, long ownerId, List<FeeLine> feeLines) {
        for (int lineNo = 0; lineNo < feeLines.size(); lineNo++) {
            FeeLine line = feeLines.get(lineNo);
            jdbc.sql("INSERT INTO " + table + " (" + ownerColumn + ", line_no, name, amount) "
                            + "VALUES (:ownerId, :lineNo, :name, :amount)")
                    .param("ownerId", ownerId)
                    .param("lineNo", lineNo)
                    .param("name", line.name())
                    .param("amount", line.amount())
                    .update();
        }
    }

    private List<StoredLine> findLines(long invoiceId) {
        return jdbc.sql("""
                        SELECT id, transaction_id, external_id, type, category, currency, occurred_at, total,
                               transaction_version, metadata
                        FROM invoice_lines
                        WHERE invoice_id = :invoiceId
                        ORDER BY line_no
                        """)
                .param("invoiceId", invoiceId)
                .query((rs, rowNum) -> new StoredLine(
                        rs.getLong("id"),
                        rs.getLong("transaction_id"),
                        rs.getString("external_id"),
                        TransactionType.fromCode(rs.getString("type")).orElseThrow(),
                        rs.getString("category"),
                        rs.getString("currency"),
                        SqlTime.read(rs, "occurred_at"),
                        rs.getBigDecimal("total"),
                        rs.getLong("transaction_version"),
                        rs.getString("metadata")))
                .list();
    }

    private Map<Long, List<TaxLine>> findLineTaxLines(List<Long> lineIds) {
        if (lineIds.isEmpty()) {
            return Map.of();
        }
        List<ChildRow<TaxLine>> rows = jdbc.sql("""
                        SELECT invoice_line_id, name, rate, amount
                        FROM invoice_line_tax_lines
                        WHERE invoice_line_id IN (:lineIds)
                        ORDER BY invoice_line_id, line_no
                        """)
                .param("lineIds", lineIds)
                .query((rs, rowNum) -> new ChildRow<>(rs.getLong("invoice_line_id"), new TaxLine(
                        rs.getString("name"),
                        Money.normalizeRate(rs.getBigDecimal("rate")),
                        rs.getBigDecimal("amount"))))
                .list();
        return ChildRow.groupByParent(rows);
    }

    private Map<Long, List<FeeLine>> findLineFeeLines(List<Long> lineIds) {
        if (lineIds.isEmpty()) {
            return Map.of();
        }
        List<ChildRow<FeeLine>> rows = jdbc.sql("""
                        SELECT invoice_line_id, name, amount
                        FROM invoice_line_fee_lines
                        WHERE invoice_line_id IN (:lineIds)
                        ORDER BY invoice_line_id, line_no
                        """)
                .param("lineIds", lineIds)
                .query((rs, rowNum) -> new ChildRow<>(rs.getLong("invoice_line_id"), new FeeLine(
                        rs.getString("name"),
                        rs.getBigDecimal("amount"))))
                .list();
        return ChildRow.groupByParent(rows);
    }

    private List<TaxLine> findTaxRollup(long invoiceId) {
        return jdbc.sql("SELECT name, rate, amount FROM invoice_tax_rollups WHERE invoice_id = :id ORDER BY line_no")
                .param("id", invoiceId)
                .query((rs, rowNum) -> new TaxLine(
                        rs.getString("name"),
                        Money.normalizeRate(rs.getBigDecimal("rate")),
                        rs.getBigDecimal("amount")))
                .list();
    }

    private List<FeeLine> findFeeRollup(long invoiceId) {
        return jdbc.sql("SELECT name, amount FROM invoice_fee_rollups WHERE invoice_id = :id ORDER BY line_no")
                .param("id", invoiceId)
                .query((rs, rowNum) -> new FeeLine(rs.getString("name"), rs.getBigDecimal("amount")))
                .list();
    }

    private static List<TaxLine> scaleTaxes(List<TaxLine> taxLines, String currency) {
        return taxLines.stream()
                .map(line -> new TaxLine(line.name(), line.rate(), Money.toCurrencyScale(line.amount(), currency)))
                .toList();
    }

    private static List<FeeLine> scaleFees(List<FeeLine> feeLines, String currency) {
        return feeLines.stream()
                .map(line -> new FeeLine(line.name(), Money.toCurrencyScale(line.amount(), currency)))
                .toList();
    }

    private record StoredLine(
            long id,
            long transactionId,
            String externalId,
            TransactionType type,
            String category,
            String currency,
            Instant occurredAt,
            BigDecimal total,
            long transactionVersion,
            String metadataJson) {

        InvoiceLine toInvoiceLine(List<TaxLine> taxLines, List<FeeLine> feeLines) {
            FeeKind feeKind = type == TransactionType.NAVAN_FEE ? FeeKind.fromCode(category).orElseThrow() : null;
            return new InvoiceLine(transactionId, externalId, type, feeKind, currency, occurredAt,
                    Money.toCurrencyScale(total, currency), transactionVersion, taxLines, feeLines, metadataJson);
        }
    }
}
