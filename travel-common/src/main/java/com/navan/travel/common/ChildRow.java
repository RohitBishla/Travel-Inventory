package com.navan.travel.common;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record ChildRow<T>(long parentId, T value) {

    public static <T> Map<Long, List<T>> groupByParent(List<ChildRow<T>> rows) {
        return rows.stream().collect(Collectors.groupingBy(
                ChildRow::parentId,
                Collectors.mapping(ChildRow::value, Collectors.toList())));
    }
}
