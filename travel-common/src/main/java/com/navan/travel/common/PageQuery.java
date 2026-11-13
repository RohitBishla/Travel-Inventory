package com.navan.travel.common;

public record PageQuery(int limit, Long beforeId) {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;

    public static PageQuery of(Integer requestedLimit, Long beforeId) {
        int limit = requestedLimit == null ? DEFAULT_LIMIT : requestedLimit;
        if (limit < 1) {
            throw new ValidationException("limit must be at least 1");
        }
        return new PageQuery(Math.min(limit, MAX_LIMIT), beforeId);
    }

    public long idUpperBound() {
        return beforeId == null ? Long.MAX_VALUE : beforeId;
    }

    public int fetchSize() {
        return limit + 1;
    }
}
