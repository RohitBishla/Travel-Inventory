package com.navan.travel.common;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Timestamps are stored as {@code DATETIME(6)} holding UTC wall-clock time, so no session time zone is involved.
 */
public final class SqlTime {

    private SqlTime() {
    }

    public static LocalDateTime toSql(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public static Instant read(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
