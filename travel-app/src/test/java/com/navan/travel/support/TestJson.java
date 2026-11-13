package com.navan.travel.support;

import java.math.BigDecimal;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class TestJson {

    public static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    private TestJson() {
    }

    public static BigDecimal decimal(JsonNode node) {
        return node.decimalValue();
    }

    public static String text(JsonNode node, String field) {
        return node.get(field).stringValue();
    }
}
