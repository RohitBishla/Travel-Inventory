package com.navan.travel.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PageQueryTest {

    @Test
    void defaultsToFiftyAndCapsAtOneHundred() {
        assertThat(PageQuery.of(null, null).limit()).isEqualTo(50);
        assertThat(PageQuery.of(1000, null).limit()).isEqualTo(100);
        assertThat(PageQuery.of(25, null).limit()).isEqualTo(25);
    }

    @Test
    void rejectsLimitsBelowOne() {
        assertThatThrownBy(() -> PageQuery.of(0, null)).isInstanceOf(ValidationException.class);
    }

    @Test
    void fetchesOneExtraRowToDetectTheNextPage() {
        assertThat(PageQuery.of(10, null).fetchSize()).isEqualTo(11);
    }

    @Test
    void firstPageHasNoUpperBound() {
        assertThat(PageQuery.of(10, null).idUpperBound()).isEqualTo(Long.MAX_VALUE);
        assertThat(PageQuery.of(10, 42L).idUpperBound()).isEqualTo(42L);
    }
}
