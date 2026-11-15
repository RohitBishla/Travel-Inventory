package com.navan.travel;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.navan.travel.support.ApiResponse;
import com.navan.travel.support.ApiTestBase;
import com.navan.travel.support.Fixtures;

class DatabaseContentionTest extends ApiTestBase {

    @Test
    void patchOnARowHeldByAnotherWriterFailsFastWithServiceUnavailable() throws Exception {
        String flightId = api.createTransaction(Fixtures.seed("flight"));

        try (Connection lockHolder = shardOf(flightId).dataSource().getConnection()) {
            lockHolder.setAutoCommit(false);
            try (PreparedStatement lock = lockHolder.prepareStatement(
                    "SELECT id FROM transactions WHERE id = ? FOR UPDATE")) {
                lock.setLong(1, numericId(flightId));
                lock.executeQuery().close();
            }

            Instant started = Instant.now();
            ApiResponse response = api.patch("/transactions/" + flightId, Map.of("total", 230.00));

            assertThat(response.status()).isEqualTo(503);
            assertThat(response.headers().firstValue("Retry-After")).hasValue("1");
            assertThat(Duration.between(started, Instant.now())).isLessThan(Duration.ofSeconds(15));
            lockHolder.rollback();
        }

        assertThat(api.patch("/transactions/" + flightId, Map.of("total", 230.00)).status()).isEqualTo(200);
    }
}
