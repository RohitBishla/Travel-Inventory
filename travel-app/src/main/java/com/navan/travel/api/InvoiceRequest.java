package com.navan.travel.api;

import java.util.List;

import com.navan.travel.common.ValidationException;

public record InvoiceRequest(String transactionId, List<String> transactionIds) {

    List<String> requestedTransactionIds() {
        if (transactionId != null && transactionIds != null) {
            throw new ValidationException("Send either transaction_id or transaction_ids, not both");
        }
        if (transactionId != null) {
            return List.of(transactionId);
        }
        if (transactionIds == null) {
            throw new ValidationException("transaction_id or transaction_ids is required");
        }
        return transactionIds;
    }
}
