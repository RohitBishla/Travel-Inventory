package com.navan.travel.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.navan.travel.common.ValidationException;
import com.navan.travel.transaction.metadata.FlightCoupon;
import com.navan.travel.transaction.metadata.FlightMetadata;
import com.navan.travel.transaction.metadata.HotelAddress;
import com.navan.travel.transaction.metadata.HotelMetadata;
import com.navan.travel.transaction.metadata.RailMetadata;
import com.navan.travel.transaction.metadata.TransactionMetadata;

class TransactionValidatorTest {

    private static final Instant DEPARTURE = Instant.parse("2026-03-11T07:00:00Z");
    private static final Instant ARRIVAL = Instant.parse("2026-03-11T08:20:00Z");

    private final TransactionValidator validator = new TransactionValidator();

    @Test
    void acceptsAValidFlight() {
        assertThatCode(() -> validator.validate(draft(TransactionType.FLIGHT, "220.00", validFlight())))
                .doesNotThrowAnyException();
    }

    @Test
    void reportsEveryViolationAtOnce() {
        FlightMetadata emptyFlight = new FlightMetadata(null, null, null, List.of());

        List<String> violations = violationsOf(draft(TransactionType.FLIGHT, "220.001", emptyFlight));

        assertThat(violations).contains(
                "total must have at most 2 decimal places",
                "metadata.origin is required",
                "metadata.fare is required",
                "metadata.coupons must contain at least one entry");
    }

    @Test
    void rejectsArrivalBeforeDeparture() {
        FlightCoupon backwards = new FlightCoupon("BA", "BA982", "TXL", "LHR", ARRIVAL, DEPARTURE);
        FlightMetadata flight = new FlightMetadata("TXL", "LHR", new BigDecimal("180"), List.of(backwards));

        assertThat(violationsOf(draft(TransactionType.FLIGHT, "220", flight)))
                .containsExactly("metadata.coupons[0].arrival must not be before departure");
    }

    @Test
    void requiresNightsToMatchTheStayDates() {
        HotelMetadata hotel = new HotelMetadata("Hotel Mitte London",
                new HotelAddress("12 Kingsway", "London", "GB"),
                LocalDate.parse("2026-03-11"), LocalDate.parse("2026-03-13"), 3,
                new BigDecimal("140"), null);

        assertThat(violationsOf(draft(TransactionType.HOTEL, "331.70", hotel)))
                .containsExactly("metadata.nights must equal the days between check_in and check_out");
    }

    @Test
    void requiresAtLeastOneRailLeg() {
        assertThat(violationsOf(draft(TransactionType.RAIL, "94", new RailMetadata(List.of()))))
                .containsExactly("metadata.legs must contain at least one entry");
    }

    @Test
    void rejectsTaxRatesOutsideZeroToOne() {
        TransactionDraft draft = new TransactionDraft(TransactionType.FLIGHT, "flight-1", "EUR", DEPARTURE,
                new BigDecimal("220"), List.of(new TaxLine("VAT", new BigDecimal("7"), BigDecimal.ONE)),
                List.of(), validFlight());

        assertThat(violationsOf(draft)).containsExactly("tax_lines[0].rate must be a fraction between 0 and 1, e.g. 0.07");
    }

    private static FlightMetadata validFlight() {
        FlightCoupon coupon = new FlightCoupon("BA", "BA982", "TXL", "LHR", DEPARTURE, ARRIVAL);
        return new FlightMetadata("TXL", "LHR", new BigDecimal("180.00"), List.of(coupon));
    }

    private static TransactionDraft draft(TransactionType type, String total, TransactionMetadata metadata) {
        return new TransactionDraft(type, "external-1", "EUR", DEPARTURE, new BigDecimal(total),
                List.of(), List.of(), metadata);
    }

    private List<String> violationsOf(TransactionDraft draft) {
        return catchThrowableOfType(ValidationException.class, () -> validator.validate(draft)).violations();
    }
}
