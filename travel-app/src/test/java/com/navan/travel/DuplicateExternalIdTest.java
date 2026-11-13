package com.navan.travel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import com.navan.travel.support.ApiResponse;
import com.navan.travel.support.ApiTestBase;
import com.navan.travel.support.Fixtures;

import tools.jackson.databind.node.ObjectNode;

class DuplicateExternalIdTest extends ApiTestBase {

    private static final String FLIGHT_EXTERNAL_ID = "flight-TXL-LHR-2026-03-11";
    private static final int CONCURRENT_CLIENTS = 16;
    private static final int ROUNDS = 5;

    @Test
    void secondPostWithSameExternalIdReturnsConflictWithExistingId() {
        String flightId = api.createTransaction(Fixtures.seed("flight"));

        ApiResponse duplicate = api.post("/transactions", Fixtures.seed("duplicate_flight"));

        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.error()).isEqualTo("duplicate_external_id");
        assertThat(duplicate.id()).isEqualTo(flightId);
        assertThat(duplicate.headers().firstValue("Location")).hasValue("/transactions/" + flightId);
        assertThat(countTransactionsWithExternalId(FLIGHT_EXTERNAL_ID)).isEqualTo(1);
    }

    @Test
    void duplicateDoesNotChangeTheStoredTransaction() {
        String flightId = api.createTransaction(Fixtures.seed("flight"));
        ObjectNode differentBody = Fixtures.seed("flight");
        differentBody.put("total", 1.00);

        api.post("/transactions", differentBody);

        assertThat(api.get("/transactions/" + flightId).body().get("total").decimalValue())
                .isEqualByComparingTo("220.00");
    }

    @Test
    void overlappingPostsWithSameExternalIdLeaveExactlyOneRow() throws Exception {
        ExecutorService clients = Executors.newFixedThreadPool(CONCURRENT_CLIENTS);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                String externalId = "race-" + round;
                List<ApiResponse> responses = postConcurrently(clients, Fixtures.seed("flight", externalId));

                List<ApiResponse> created = responses.stream().filter(r -> r.status() == 201).toList();
                List<ApiResponse> conflicts = responses.stream().filter(r -> r.status() == 409).toList();
                assertThat(created).as("round %d", round).hasSize(1);
                assertThat(conflicts).hasSize(CONCURRENT_CLIENTS - 1);
                String winnerId = created.getFirst().id();
                assertThat(conflicts).allSatisfy(conflict -> assertThat(conflict.id()).isEqualTo(winnerId));
                assertThat(countTransactionsWithExternalId(externalId)).isEqualTo(1);
            }
            assertThat(countRows("flight_details")).isEqualTo(ROUNDS);
            assertThat(countRows("flight_coupons")).isEqualTo(ROUNDS);
            assertThat(countRows("transaction_tax_lines")).isEqualTo(ROUNDS);
        } finally {
            clients.shutdownNow();
        }
    }

    private List<ApiResponse> postConcurrently(ExecutorService clients, ObjectNode body) throws Exception {
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<ApiResponse>> futures = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_CLIENTS; i++) {
            futures.add(clients.submit(() -> {
                startGate.await();
                return api.post("/transactions", body);
            }));
        }
        startGate.countDown();
        List<ApiResponse> responses = new ArrayList<>();
        for (Future<ApiResponse> future : futures) {
            responses.add(future.get());
        }
        return responses;
    }
}
