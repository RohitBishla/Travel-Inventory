package com.navan.travel.common;

import java.time.Instant;
import java.util.Comparator;

/** Sort key for lists: created_at works across shards (ids don't), and the id breaks ties. */
public record PagePosition(Instant createdAt, long id) implements Comparable<PagePosition> {

    public static final PagePosition BEFORE_EVERYTHING = new PagePosition(Instant.parse("9999-12-31T23:59:59Z"), Long.MAX_VALUE);

    private static final Comparator<PagePosition> ORDER =
            Comparator.comparing(PagePosition::createdAt).thenComparingLong(PagePosition::id);

    @Override
    public int compareTo(PagePosition other) {
        return ORDER.compare(this, other);
    }
}
