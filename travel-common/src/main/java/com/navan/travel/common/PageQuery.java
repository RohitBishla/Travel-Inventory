package com.navan.travel.common;

/** @param after last row of the previous page, or null for the first page */
public record PageQuery(int limit, PagePosition after) {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;

    public static PageQuery of(Integer requestedLimit, PagePosition after) {
        int limit = requestedLimit == null ? DEFAULT_LIMIT : requestedLimit;
        if (limit < 1) {
            throw new ValidationException("limit must be at least 1");
        }
        return new PageQuery(Math.min(limit, MAX_LIMIT), after);
    }

    public PagePosition upperBound() {
        return after == null ? PagePosition.BEFORE_EVERYTHING : after;
    }

    public int fetchSize() {
        return limit + 1;
    }
}
