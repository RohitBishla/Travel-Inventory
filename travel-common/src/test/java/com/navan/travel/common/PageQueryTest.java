package com.navan.travel.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

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
    void firstPageStartsAfterEveryRowAndLaterPagesAfterTheCursor() {
        PagePosition cursor = new PagePosition(Instant.parse("2026-03-11T06:40:00Z"), 42);

        assertThat(PageQuery.of(10, null).upperBound()).isEqualTo(PagePosition.BEFORE_EVERYTHING);
        assertThat(PageQuery.of(10, cursor).upperBound()).isEqualTo(cursor);
    }

    @Test
    void mergedShardPagesKeepTheNewestRowsAndPointAtTheLastOne() {
        PagePosition oldest = new PagePosition(Instant.parse("2026-01-01T00:00:00Z"), 1);
        PagePosition middle = new PagePosition(Instant.parse("2026-01-02T00:00:00Z"), 4);
        PagePosition newest = new PagePosition(Instant.parse("2026-01-03T00:00:00Z"), 3);

        Page<PagePosition> page = Page.mergeOverfetched(
                List.of(List.of(newest, oldest), List.of(middle)), PageQuery.of(2, null), position -> position);

        assertThat(page.items()).containsExactly(newest, middle);
        assertThat(page.next()).isEqualTo(middle);
    }
}
