package com.navan.travel.support;

import java.nio.file.Path;
import java.util.List;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

public final class Fixtures {

    public static final List<String> TRIP_SEEDS = List.of("flight", "hotel", "rail", "trip_fee", "agent_call_fee");

    private static final Path FIXTURE_DIR = Path.of(System.getProperty("fixtures.dir", "../fixtures/task-b"));
    private static final JsonNode SEEDS = read("seed-payloads.json");
    private static final JsonNode EXPECTED_INVOICE = read("expected-invoice.json");

    private Fixtures() {
    }

    public static ObjectNode seed(String name) {
        return (ObjectNode) SEEDS.get(name).deepCopy();
    }

    public static ObjectNode seed(String name, String externalId) {
        ObjectNode seed = seed(name);
        seed.put("external_id", externalId);
        return seed;
    }

    public static JsonNode expectedInvoice() {
        return EXPECTED_INVOICE;
    }

    public static ObjectNode navanFee(String externalId, String currency) {
        ObjectNode fee = seed("trip_fee", externalId);
        fee.put("currency", currency);
        return fee;
    }

    private static JsonNode read(String fileName) {
        return TestJson.MAPPER.readTree(FIXTURE_DIR.resolve(fileName).toFile());
    }
}
