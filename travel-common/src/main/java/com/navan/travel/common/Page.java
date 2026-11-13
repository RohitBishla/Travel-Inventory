package com.navan.travel.common;

import java.util.List;
import java.util.function.Function;
import java.util.function.ToLongFunction;

public record Page<T>(List<T> items, int limit, Long nextBeforeId) {

    public static <T> Page<T> fromOverfetched(List<T> rows, PageQuery query, ToLongFunction<T> idOf) {
        if (rows.size() <= query.limit()) {
            return new Page<>(rows, query.limit(), null);
        }
        List<T> items = rows.subList(0, query.limit());
        return new Page<>(List.copyOf(items), query.limit(), idOf.applyAsLong(items.getLast()));
    }

    public <R> Page<R> map(Function<T, R> mapper) {
        return new Page<>(items.stream().map(mapper).toList(), limit, nextBeforeId);
    }
}
