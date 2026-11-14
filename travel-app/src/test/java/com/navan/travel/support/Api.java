package com.navan.travel.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;

public final class Api {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String baseUrl;

    public Api(int port) {
        this.baseUrl = "http://localhost:" + port;
    }

    public ApiResponse get(String path) {
        return send("GET", path, BodyPublishers.noBody());
    }

    public ApiResponse post(String path, Object body) {
        return send("POST", path, json(body));
    }

    public ApiResponse patch(String path, Object body) {
        return send("PATCH", path, json(body));
    }

    public ApiResponse delete(String path) {
        return send("DELETE", path, BodyPublishers.noBody());
    }

    public String createTransaction(JsonNode body) {
        ApiResponse response = post("/transactions", body);
        assertThat(response.status()).as("create transaction: %s", response.body()).isEqualTo(201);
        return response.transactionId();
    }

    public ApiResponse createInvoice(List<String> transactionIds) {
        return post("/invoices", Map.of("transaction_ids", transactionIds));
    }

    private ApiResponse send(String method, String path, BodyPublisher body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .method(method, body)
                .build();
        try {
            var response = http.send(request, BodyHandlers.ofString());
            JsonNode responseBody = response.body().isBlank() ? null : TestJson.MAPPER.readTree(response.body());
            return new ApiResponse(response.statusCode(), responseBody, response.headers());
        } catch (IOException e) {
            throw new IllegalStateException(method + " " + path + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(method + " " + path + " was interrupted", e);
        }
    }

    private static BodyPublisher json(Object body) {
        String content = body instanceof String raw ? raw : TestJson.MAPPER.writeValueAsString(body);
        return BodyPublishers.ofString(content);
    }
}
