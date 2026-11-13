package com.navan.travel.api;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.navan.travel.common.ValidationException;

final class PageCursor {

    private static final String PREFIX = "before:";

    private PageCursor() {
    }

    static String encode(Long beforeId) {
        if (beforeId == null) {
            return null;
        }
        byte[] raw = (PREFIX + beforeId).getBytes(StandardCharsets.UTF_8);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    static Long decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (raw.startsWith(PREFIX)) {
                return Long.parseLong(raw.substring(PREFIX.length()));
            }
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        throw invalid();
    }

    private static ValidationException invalid() {
        return new ValidationException("cursor is invalid; use next_cursor from a previous page");
    }
}
