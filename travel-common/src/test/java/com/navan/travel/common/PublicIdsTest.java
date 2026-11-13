package com.navan.travel.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PublicIdsTest {

    @Test
    void roundTripsPrefixedIds() {
        assertThat(PublicIds.parseTransaction(PublicIds.transaction(17))).hasValue(17);
        assertThat(PublicIds.parseInvoice(PublicIds.invoice(3))).hasValue(3);
    }

    @Test
    void rejectsWrongPrefixAndMalformedIds() {
        assertThat(PublicIds.parseTransaction("inv_1")).isEmpty();
        assertThat(PublicIds.parseTransaction("txn_abc")).isEmpty();
        assertThat(PublicIds.parseTransaction("txn_0")).isEmpty();
        assertThat(PublicIds.parseTransaction(null)).isEmpty();
    }
}
