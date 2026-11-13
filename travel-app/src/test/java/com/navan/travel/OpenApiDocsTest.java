package com.navan.travel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.navan.travel.support.ApiResponse;
import com.navan.travel.support.ApiTestBase;

import tools.jackson.databind.JsonNode;

class OpenApiDocsTest extends ApiTestBase {

    @Test
    void specListsEveryEndpointWithSnakeCaseFields() {
        ApiResponse response = api.get("/v3/api-docs");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().get("paths").propertyNames())
                .containsExactlyInAnyOrder("/transactions", "/transactions/{id}", "/invoices", "/invoices/{id}");

        JsonNode createRequest = response.body().at("/components/schemas/CreateTransactionRequest/properties");
        assertThat(createRequest.propertyNames()).contains("external_id", "occurred_at", "tax_lines", "metadata");
        assertThat(createRequest.at("/metadata/oneOf")).hasSize(4);
    }

    @Test
    void swaggerUiPointsAtTheSpec() {
        ApiResponse config = api.get("/v3/api-docs/swagger-config");

        assertThat(config.status()).isEqualTo(200);
        assertThat(config.body().get("url").asString()).isEqualTo("/v3/api-docs");
    }
}
