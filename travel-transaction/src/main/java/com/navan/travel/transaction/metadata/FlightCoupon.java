package com.navan.travel.transaction.metadata;

import java.time.Instant;

public record FlightCoupon(
        String carrier,
        String flightNumber,
        String from,
        String to,
        Instant departure,
        Instant arrival) {
}
