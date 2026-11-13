package com.navan.travel.transaction.metadata;

import java.util.Arrays;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonValue;

public enum FeeKind {
    TRIP_FEE("trip_fee"),
    AGENT_CALL_FEE("agent_call_fee");

    private final String code;

    FeeKind(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public static Optional<FeeKind> fromCode(String code) {
        return Arrays.stream(values()).filter(kind -> kind.code.equals(code)).findFirst();
    }
}
