package com.navan.travel.transaction.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.navan.travel.common.ChildRow;
import com.navan.travel.common.Money;
import com.navan.travel.transaction.FeeLine;
import com.navan.travel.transaction.TaxLine;

@Repository
class ChargeLineRepository {

    private final JdbcClient jdbc;

    ChargeLineRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insertTaxLines(long transactionId, List<TaxLine> taxLines) {
        for (int lineNo = 0; lineNo < taxLines.size(); lineNo++) {
            TaxLine line = taxLines.get(lineNo);
            jdbc.sql("""
                            INSERT INTO transaction_tax_lines (transaction_id, line_no, name, rate, amount)
                            VALUES (:transactionId, :lineNo, :name, :rate, :amount)
                            """)
                    .param("transactionId", transactionId)
                    .param("lineNo", lineNo)
                    .param("name", line.name().trim())
                    .param("rate", line.rate())
                    .param("amount", line.amount())
                    .update();
        }
    }

    void insertFeeLines(long transactionId, List<FeeLine> feeLines) {
        for (int lineNo = 0; lineNo < feeLines.size(); lineNo++) {
            FeeLine line = feeLines.get(lineNo);
            jdbc.sql("""
                            INSERT INTO transaction_fee_lines (transaction_id, line_no, name, amount)
                            VALUES (:transactionId, :lineNo, :name, :amount)
                            """)
                    .param("transactionId", transactionId)
                    .param("lineNo", lineNo)
                    .param("name", line.name().trim())
                    .param("amount", line.amount())
                    .update();
        }
    }

    void replaceTaxLines(long transactionId, List<TaxLine> taxLines) {
        jdbc.sql("DELETE FROM transaction_tax_lines WHERE transaction_id = :id").param("id", transactionId).update();
        insertTaxLines(transactionId, taxLines);
    }

    void replaceFeeLines(long transactionId, List<FeeLine> feeLines) {
        jdbc.sql("DELETE FROM transaction_fee_lines WHERE transaction_id = :id").param("id", transactionId).update();
        insertFeeLines(transactionId, feeLines);
    }

    Map<Long, List<TaxLine>> findTaxLines(Collection<Long> transactionIds) {
        List<ChildRow<TaxLine>> rows = jdbc.sql("""
                        SELECT transaction_id, name, rate, amount
                        FROM transaction_tax_lines
                        WHERE transaction_id IN (:ids)
                        ORDER BY transaction_id, line_no
                        """)
                .param("ids", transactionIds)
                .query((rs, rowNum) -> new ChildRow<>(rs.getLong("transaction_id"), new TaxLine(
                        rs.getString("name"),
                        Money.normalizeRate(rs.getBigDecimal("rate")),
                        rs.getBigDecimal("amount"))))
                .list();
        return ChildRow.groupByParent(rows);
    }

    Map<Long, List<FeeLine>> findFeeLines(Collection<Long> transactionIds) {
        List<ChildRow<FeeLine>> rows = jdbc.sql("""
                        SELECT transaction_id, name, amount
                        FROM transaction_fee_lines
                        WHERE transaction_id IN (:ids)
                        ORDER BY transaction_id, line_no
                        """)
                .param("ids", transactionIds)
                .query((rs, rowNum) -> new ChildRow<>(rs.getLong("transaction_id"), new FeeLine(
                        rs.getString("name"),
                        rs.getBigDecimal("amount"))))
                .list();
        return ChildRow.groupByParent(rows);
    }
}
