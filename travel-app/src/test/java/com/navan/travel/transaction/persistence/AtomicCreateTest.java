package com.navan.travel.transaction.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.navan.travel.support.ApiTestBase;
import com.navan.travel.support.Fixtures;
import com.navan.travel.transaction.metadata.FlightMetadata;

class AtomicCreateTest extends ApiTestBase {

    @MockitoSpyBean
    private FlightDetailsStore flightDetailsStore;

    @Test
    void failureWhileWritingDetailsLeavesNoPartialTransaction() {
        doThrow(new DataIntegrityViolationException("simulated failure"))
                .when(flightDetailsStore).insert(anyLong(), any(FlightMetadata.class));

        int status = api.post("/transactions", Fixtures.seed("flight")).status();

        assertThat(status).isEqualTo(500);
        assertThat(countRows("transactions")).isZero();
        assertThat(countRows("transaction_tax_lines")).isZero();
        assertThat(countRows("transaction_fee_lines")).isZero();
    }
}
