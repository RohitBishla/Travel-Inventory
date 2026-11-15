package com.navan.travel.api;

import java.util.List;
import java.util.function.Function;

import com.navan.travel.common.Page;

public record PageResponse<T>(List<T> items, int limit, String nextCursor) {

    static <D, T> PageResponse<T> from(Page<D> page, Function<D, T> toResponse) {
        return new PageResponse<>(
                page.items().stream().map(toResponse).toList(),
                page.limit(),
                PageCursor.encode(page.next()));
    }
}
