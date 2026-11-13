package com.navan.travel.common;

import java.util.List;

public class ValidationException extends RuntimeException {

    private final List<String> violations;

    public ValidationException(List<String> violations) {
        super("Request is invalid: " + String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public ValidationException(String violation) {
        this(List.of(violation));
    }

    public List<String> violations() {
        return violations;
    }
}
