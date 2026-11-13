package com.navan.travel.transaction.metadata;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RailLeg(
        String origin,
        String destination,
        Instant departure,
        Instant arrival,
        String trainNumber,
        @JsonProperty("class") String travelClass) {
}
