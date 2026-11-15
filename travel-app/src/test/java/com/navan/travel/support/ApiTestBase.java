package com.navan.travel.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.navan.travel.common.shard.Shard;
import com.navan.travel.common.shard.Shards;

/** Two fresh test shards on the MariaDB at TEST_DATABASE_HOST (default localhost). */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "travel.sharding.shards[0].id=0",
                "travel.sharding.shards[0].url=jdbc:mariadb://${TEST_DATABASE_HOST:localhost}:3306/travel_test_shard_0?createDatabaseIfNotExist=true",
                "travel.sharding.shards[1].id=1",
                "travel.sharding.shards[1].url=jdbc:mariadb://${TEST_DATABASE_HOST:localhost}:3306/travel_test_shard_1?createDatabaseIfNotExist=true",
                "travel.sharding.fresh-schema=true",
                "travel.sharding.lock-wait-timeout-seconds=1"
        })
public abstract class ApiTestBase {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    protected Shards shards;

    protected Api api;

    @BeforeEach
    void resetDatabase() {
        api = new Api(port);
        for (Shard shard : shards.all()) {
            shard.jdbc().sql("DELETE FROM invoice_links").update();
            shard.jdbc().sql("DELETE FROM invoices").update();
            shard.jdbc().sql("DELETE FROM transactions").update();
            shard.jdbc().sql("DELETE FROM external_ids").update();
        }
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
        return shards.all().stream().mapToLong(shard -> countRows(shard, table)).sum();
    }

    protected static long countRows(Shard shard, String table) {
        return shard.jdbc().sql("SELECT COUNT(*) FROM " + table).query(Long.class).single();
    }

    protected long countTransactionsWithExternalId(String externalId) {
        return shards.all().stream()
                .mapToLong(shard -> shard.jdbc()
                        .sql("SELECT COUNT(*) FROM transactions WHERE external_id = :externalId")
                        .param("externalId", externalId)
                        .query(Long.class)
                        .single())
                .sum();
    }

    protected Shard shardOf(String publicId) {
        return shards.forId(numericId(publicId));
    }

    protected JdbcClient jdbcOnShardOf(String publicId) {
        return shardOf(publicId).jdbc();
    }

    protected static long numericId(String publicId) {
        return Long.parseLong(publicId.substring(publicId.indexOf('_') + 1));
    }
}
