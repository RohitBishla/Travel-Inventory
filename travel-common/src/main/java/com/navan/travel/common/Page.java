package com.navan.travel.common;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public record Page<T>(List<T> items, int limit, PagePosition next) {

    /**
     * Merges pages read from several shards. Each shard returns up to {@code fetchSize} rows older than the cursor,
     * newest first; the newest {@code fetchSize} of all of them are the rows one table would return. One row beyond
     * {@code limit} means another page exists.
     */
    public static <T> Page<T> mergeOverfetched(List<List<T>> rowsPerShard,
                                               PageQuery query,
                                               Function<T, PagePosition> positionOf) {
        List<T> newestFirst = rowsPerShard.stream()
                .flatMap(List::stream)
                .sorted(Comparator.comparing(positionOf).reversed())
                .limit(query.fetchSize())
                .toList();
        if (newestFirst.size() <= query.limit()) {
            return new Page<>(newestFirst, query.limit(), null);
        }
        List<T> items = newestFirst.subList(0, query.limit());
        return new Page<>(List.copyOf(items), query.limit(), positionOf.apply(items.getLast()));
    }

    public <R> Page<R> map(Function<T, R> mapper) {
        return new Page<>(items.stream().map(mapper).toList(), limit, next);
    }
}
