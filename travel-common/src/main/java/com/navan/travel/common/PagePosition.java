package com.navan.travel.common;

import java.time.Instant;
import java.util.Comparator;

/**
 * Where a row sits in a newest-first list. {@code created_at} orders rows across shards, whose ids interleave;
 * the id breaks ties.
 */
public record PagePosition(Instant createdAt, long id) implements Comparable<PagePosition> {

    public static final PagePosition BEFORE_EVERYTHING = new PagePosition(Instant.parse("9999-12-31T23:59:59Z"), Long.MAX_VALUE);

    private static final Comparator<PagePosition> ORDER =
            Comparator.comparing(PagePosition::createdAt).thenComparingLong(PagePosition::id);

    @Override
    public int compareTo(PagePosition other) {
        return ORDER.compare(this, other);
    }
}
