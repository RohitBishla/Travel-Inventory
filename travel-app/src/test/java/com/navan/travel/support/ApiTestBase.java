package com.navan.travel.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:file:./target/test-db/${random.uuid};LOCK_TIMEOUT=1000")
public abstract class ApiTestBase {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    protected JdbcClient jdbc;

    protected Api api;

    @BeforeEach
    void resetDatabase() {
        api = new Api(port);
        jdbc.sql("DELETE FROM invoices").update();
        jdbc.sql("DELETE FROM transactions").update();
    }

    protected Map<String, String> createTripTransactions() {
        Map<String, String> idsBySeed = new LinkedHashMap<>();
        for (String seed : Fixtures.TRIP_SEEDS) {
            idsBySeed.put(seed, api.createTransaction(Fixtures.seed(seed)));
        }
        return idsBySeed;
    }

    protected String createTripInvoice(Map<String, String> transactionIds) {
        ApiResponse response = api.createInvoice(List.copyOf(transactionIds.values()));
        if (response.status() != 201) {
            throw new AssertionError("Invoice creation failed: " + response.body());
        }
        return response.id();
    }

    protected long countRows(String table) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table).query(Long.class).single();
    }

    protected long countTransactionsWithExternalId(String externalId) {
        return jdbc.sql("SELECT COUNT(*) FROM transactions WHERE external_id = :externalId")
                .param("externalId", externalId)
                .query(Long.class)
                .single();
    }

    protected static long numericId(String publicId) {
        return Long.parseLong(publicId.substring(publicId.indexOf('_') + 1));
    }
}
