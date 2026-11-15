package com.navan.travel;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.navan.travel.common.shard.Shard;
import com.navan.travel.common.shard.Shards;
import com.navan.travel.support.Api;
import com.navan.travel.support.ApiResponse;
import com.navan.travel.support.Fixtures;
import com.navan.travel.transaction.identity.IdentityRebalancer;

/**
 * The README procedure for adding a shard, run end to end on three databases: two shards with data, then three
 * shards with previous-ring set, then a rebalance, then three shards on their own.
 */
class AddShardTest {

    private static final String HOST = System.getenv().getOrDefault("TEST_DATABASE_HOST", "localhost");
    private static final String DATABASE_PREFIX = "travel_test_reshard_";
    private static final int FEES = 40;

    @Test
    void existingKeysStayUniqueAndReadableWhileAndAfterAThirdShardJoins() throws SQLException {
        dropDatabases();
        Map<String, String> idsByExternalId = new LinkedHashMap<>();
        String invoiceId;

        try (ConfigurableApplicationContext twoShards = start(List.of(0, 1), List.of())) {
            Api api = api(twoShards);
            for (String seed : Fixtures.TRIP_SEEDS) {
                idsByExternalId.put(Fixtures.seed(seed).get("external_id").stringValue(), api.createTransaction(Fixtures.seed(seed)));
            }
            for (int i = 0; i < FEES; i++) {
                idsByExternalId.put("fee-" + i, api.createTransaction(Fixtures.navanFee("fee-" + i, "EUR")));
            }
            invoiceId = api.createInvoice(List.copyOf(idsByExternalId.values()).subList(0, Fixtures.TRIP_SEEDS.size())).id();
        }

        try (ConfigurableApplicationContext joining = start(List.of(0, 1, 2), List.of(0, 1))) {
            Shards shards = joining.getBean(Shards.class);
            assertThat(idsByExternalId.keySet().stream().filter(key -> shards.forExternalId(key).id() == 2))
                    .as("some existing keys now belong to the new shard").isNotEmpty();

            assertEveryKeyIsTakenByItsOriginalTransaction(api(joining), idsByExternalId);

            int moved = joining.getBean(IdentityRebalancer.class).rebalance();
            assertThat(moved).isPositive();
            assertEveryRecordSitsOnItsOwner(shards);
            assertThat(joining.getBean(IdentityRebalancer.class).rebalance()).as("a second run has nothing to do").isZero();
        }

        try (ConfigurableApplicationContext threeShards = start(List.of(0, 1, 2), List.of())) {
            Api api = api(threeShards);
            Shards shards = threeShards.getBean(Shards.class);

            assertEveryKeyIsTakenByItsOriginalTransaction(api, idsByExternalId);
            idsByExternalId.values().forEach(id -> assertThat(api.get("/transactions/" + id).status()).isEqualTo(200));
            assertThat(api.get("/invoices/" + invoiceId).body().get("total").decimalValue()).isEqualByComparingTo("685.70");

            String movedKey = idsByExternalId.keySet().stream()
                    .filter(key -> key.startsWith("fee-") && shards.forExternalId(key).id() == 2)
                    .findFirst().orElseThrow();
            assertThat(api.delete("/transactions/" + idsByExternalId.get(movedKey)).status()).isEqualTo(204);
            String recreated = api.createTransaction(Fixtures.navanFee(movedKey, "EUR"));
            assertThat(shards.forId(Long.parseLong(recreated.substring(4))).id()).isEqualTo(2);

            List<Integer> newShards = IntStream.range(0, 30)
                    .mapToObj(i -> api.createTransaction(Fixtures.navanFee("after-" + i, "EUR")))
                    .map(id -> shards.forId(Long.parseLong(id.substring(4))).id())
                    .distinct().toList();
            assertThat(newShards).as("new keys spread over all three shards").containsExactlyInAnyOrder(0, 1, 2);
        }
    }

    private static void assertEveryKeyIsTakenByItsOriginalTransaction(Api api, Map<String, String> idsByExternalId) {
        idsByExternalId.forEach((externalId, id) -> {
            ApiResponse duplicate = api.post("/transactions", Fixtures.navanFee(externalId, "EUR"));
            assertThat(duplicate.status()).as(externalId).isEqualTo(409);
            assertThat(duplicate.transactionId()).as(externalId).isEqualTo(id);
        });
    }

    private static void assertEveryRecordSitsOnItsOwner(Shards shards) {
        for (Shard shard : shards.all()) {
            List<String> keys = shard.jdbc().sql("SELECT external_id FROM external_ids").query(String.class).list();
            assertThat(keys).allSatisfy(key -> assertThat(shards.forExternalId(key)).isEqualTo(shard));
        }
    }

    private static ConfigurableApplicationContext start(List<Integer> shardIds, List<Integer> previousRing) {
        List<String> arguments = new ArrayList<>(List.of("--server.port=0", "--travel.sharding.lock-wait-timeout-seconds=1"));
        for (int i = 0; i < shardIds.size(); i++) {
            arguments.add("--travel.sharding.shards[" + i + "].id=" + shardIds.get(i));
            arguments.add("--travel.sharding.shards[" + i + "].url=" + url(shardIds.get(i)));
        }
        arguments.add("--travel.sharding.previous-ring=" + previousRing.stream().map(String::valueOf).collect(Collectors.joining(",")));
        return new SpringApplicationBuilder(TravelInvoiceApplication.class).run(arguments.toArray(String[]::new));
    }

    private static Api api(ConfigurableApplicationContext context) {
        return new Api(context.getEnvironment().getRequiredProperty("local.server.port", Integer.class));
    }

    private static String url(int shardId) {
        return "jdbc:mariadb://" + HOST + ":3306/" + DATABASE_PREFIX + shardId + "?createDatabaseIfNotExist=true";
    }

    private static void dropDatabases() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:mariadb://" + HOST + ":3306/", "travel", "travel");
             Statement statement = connection.createStatement()) {
            for (int shardId = 0; shardId < 3; shardId++) {
                statement.execute("DROP DATABASE IF EXISTS " + DATABASE_PREFIX + shardId);
            }
        }
    }
}
