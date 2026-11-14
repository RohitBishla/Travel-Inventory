package com.navan.travel.support;

import java.net.http.HttpHeaders;

import tools.jackson.databind.JsonNode;

public record ApiResponse(int status, JsonNode body, HttpHeaders headers) {

    public String id() {
        return TestJson.text(body, "id");
    }

    public String transactionId() {
        return TestJson.text(body, "transaction_id");
    }

    public String error() {
        return TestJson.text(body, "error");
    }
}
