package com.navan.travel.api;

import java.util.List;

public record ApiError(String error, String message, List<String> details, String transactionId) {

    static ApiError of(String error, String message) {
        return new ApiError(error, message, null, null);
    }
}
