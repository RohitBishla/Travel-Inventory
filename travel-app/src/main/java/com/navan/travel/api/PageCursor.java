package com.navan.travel.api;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import com.navan.travel.common.PagePosition;
import com.navan.travel.common.ValidationException;

/** Opaque to clients: created_at (epoch micros) and id of the last row they saw. */
final class PageCursor {

    private static final String PREFIX = "after:";

    private PageCursor() {
    }

    static String encode(PagePosition position) {
        if (position == null) {
            return null;
        }
        long epochMicros = ChronoUnit.MICROS.between(Instant.EPOCH, position.createdAt());
        byte[] raw = (PREFIX + epochMicros + ":" + position.id()).getBytes(StandardCharsets.UTF_8);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    static PagePosition decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (raw.startsWith(PREFIX)) {
                String[] parts = raw.substring(PREFIX.length()).split(":", 2);
                Instant createdAt = Instant.EPOCH.plus(Long.parseLong(parts[0]), ChronoUnit.MICROS);
                return new PagePosition(createdAt, Long.parseLong(parts[1]));
            }
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            throw invalid();
        }
        throw invalid();
    }

    private static ValidationException invalid() {
        return new ValidationException("cursor is invalid; use next_cursor from a previous page");
    }
}
