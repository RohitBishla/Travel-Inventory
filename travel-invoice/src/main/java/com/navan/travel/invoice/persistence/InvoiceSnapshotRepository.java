package com.navan.travel.invoice.persistence;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.navan.travel.common.Money;
import com.navan.travel.common.SqlTime;
import com.navan.travel.invoice.snapshot.InvoiceLine;
import com.navan.travel.invoice.snapshot.InvoiceSnapshot;
import com.navan.travel.transaction.FeeLine;
import com.navan.travel.transaction.TaxLine;
import com.navan.travel.transaction.TransactionType;
import com.navan.travel.transaction.metadata.FeeKind;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * One row per billed transaction. Its tax lines, fee lines and metadata are frozen JSON copies: they never change
 * and are always read whole, so they don't need tables of their own.
 */
@Repository
public class InvoiceSnapshotRepository {

    private static final TypeReference<List<TaxLine>> TAX_LINES = new TypeReference<>() {
    };
    private static final TypeReference<List<FeeLine>> FEE_LINES = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public InvoiceSnapshotRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void insert(long invoiceId, InvoiceSnapshot snapshot) {
        for (int lineNo = 0; lineNo < snapshot.lines().size(); lineNo++) {
            InvoiceLine line = snapshot.lines().get(lineNo);
            jdbc.sql("""
                            INSERT INTO invoice_lines (invoice_id, line_no, transaction_id, external_id, type, category,
                                                       currency, occurred_at, total, transaction_version,
                                                       tax_lines, fee_lines, metadata)
                            VALUES (:invoiceId, :lineNo, :transactionId, :externalId, :type, :category,
                                    :currency, :occurredAt, :total, :transactionVersion,
                                    :taxLines, :feeLines, :metadata)
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
                    .param("taxLines", objectMapper.writeValueAsString(line.taxLines()))
                    .param("feeLines", objectMapper.writeValueAsString(line.feeLines()))
                    .param("metadata", line.metadataJson())
                    .update();
        }
    }

    public void delete(long invoiceId) {
        jdbc.sql("DELETE FROM invoice_lines WHERE invoice_id = :id").param("id", invoiceId).update();
    }

    public List<Long> transactionIds(long invoiceId) {
        return jdbc.sql("SELECT transaction_id FROM invoice_lines WHERE invoice_id = :id ORDER BY line_no")
                .param("id", invoiceId)
                .query(Long.class)
                .list();
    }

    /** Locking read: waits for an invoice that is still committing instead of missing it. */
    public boolean billsTransaction(long invoiceId, long transactionId) {
        return jdbc.sql("""
                        SELECT 1 FROM invoice_lines
                        WHERE invoice_id = :invoiceId AND transaction_id = :transactionId
                        LOCK IN SHARE MODE
                        """)
                .param("invoiceId", invoiceId)
                .param("transactionId", transactionId)
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    public InvoiceSnapshot find(long invoiceId, String currency, BigDecimal total) {
        List<InvoiceLine> lines = jdbc.sql("""
                        SELECT transaction_id, external_id, type, category, currency, occurred_at, total,
                               transaction_version, tax_lines, fee_lines, metadata
                        FROM invoice_lines
                        WHERE invoice_id = :invoiceId
                        ORDER BY line_no
                        """)
                .param("invoiceId", invoiceId)
                .query((rs, rowNum) -> {
                    TransactionType type = TransactionType.fromCode(rs.getString("type")).orElseThrow();
                    String category = rs.getString("category");
                    return new InvoiceLine(
                            rs.getLong("transaction_id"),
                            rs.getString("external_id"),
                            type,
                            type == TransactionType.NAVAN_FEE ? FeeKind.fromCode(category).orElseThrow() : null,
                            rs.getString("currency"),
                            SqlTime.read(rs, "occurred_at"),
                            Money.toCurrencyScale(rs.getBigDecimal("total"), currency),
                            rs.getLong("transaction_version"),
                            taxLines(rs.getString("tax_lines"), currency),
                            feeLines(rs.getString("fee_lines"), currency),
                            rs.getString("metadata"));
                })
                .list();
        return new InvoiceSnapshot(currency, total, lines);
    }

    private List<TaxLine> taxLines(String json, String currency) {
        return objectMapper.readValue(json, TAX_LINES).stream()
                .map(line -> new TaxLine(line.name(), Money.normalizeRate(line.rate()),
                        Money.toCurrencyScale(line.amount(), currency)))
                .toList();
    }

    private List<FeeLine> feeLines(String json, String currency) {
        return objectMapper.readValue(json, FEE_LINES).stream()
                .map(line -> new FeeLine(line.name(), Money.toCurrencyScale(line.amount(), currency)))
                .toList();
    }
}
