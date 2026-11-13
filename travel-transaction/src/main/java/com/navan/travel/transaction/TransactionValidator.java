package com.navan.travel.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.navan.travel.common.Money;
import com.navan.travel.common.ValidationException;
import com.navan.travel.transaction.metadata.FlightCoupon;
import com.navan.travel.transaction.metadata.FlightMetadata;
import com.navan.travel.transaction.metadata.HotelAddress;
import com.navan.travel.transaction.metadata.HotelExtra;
import com.navan.travel.transaction.metadata.HotelMetadata;
import com.navan.travel.transaction.metadata.NavanFeeMetadata;
import com.navan.travel.transaction.metadata.RailLeg;
import com.navan.travel.transaction.metadata.RailMetadata;

@Component
class TransactionValidator {

    private static final int MAX_CHARGE_LINES = 50;
    private static final int MAX_SEGMENTS = 20;

    void validate(TransactionDraft draft) {
        Checks checks = new Checks(Money.minorUnits(draft.currency()).orElse(null));

        checks.text(draft.externalId(), "external_id", 128);
        if (!checks.hasKnownCurrency()) {
            checks.fail("currency must be a supported ISO 4217 code, e.g. EUR");
        }
        checks.present(draft.occurredAt(), "occurred_at");
        checks.amount(draft.total(), "total");
        validateTaxLines(draft.taxLines(), checks);
        validateFeeLines(draft.feeLines(), checks);

        switch (draft.metadata()) {
            case FlightMetadata flight -> validateFlight(flight, checks);
            case HotelMetadata hotel -> validateHotel(hotel, checks);
            case RailMetadata rail -> validateRail(rail, checks);
            case NavanFeeMetadata navanFee -> validateNavanFee(navanFee, checks);
        }

        checks.throwIfAny();
    }

    private void validateTaxLines(List<TaxLine> taxLines, Checks checks) {
        if (checks.list(taxLines, "tax_lines", MAX_CHARGE_LINES, false)) {
            for (int i = 0; i < taxLines.size(); i++) {
                TaxLine line = taxLines.get(i);
                String path = "tax_lines[" + i + "]";
                if (checks.present(line, path)) {
                    checks.text(line.name(), path + ".name", 128);
                    checks.rate(line.rate(), path + ".rate");
                    checks.amount(line.amount(), path + ".amount");
                }
            }
        }
    }

    private void validateFeeLines(List<FeeLine> feeLines, Checks checks) {
        if (checks.list(feeLines, "fee_lines", MAX_CHARGE_LINES, false)) {
            for (int i = 0; i < feeLines.size(); i++) {
                FeeLine line = feeLines.get(i);
                String path = "fee_lines[" + i + "]";
                if (checks.present(line, path)) {
                    checks.text(line.name(), path + ".name", 128);
                    checks.amount(line.amount(), path + ".amount");
                }
            }
        }
    }

    private void validateFlight(FlightMetadata flight, Checks checks) {
        checks.text(flight.origin(), "metadata.origin", 64);
        checks.text(flight.destination(), "metadata.destination", 64);
        checks.amount(flight.fare(), "metadata.fare");
        if (checks.list(flight.coupons(), "metadata.coupons", MAX_SEGMENTS, true)) {
            for (int i = 0; i < flight.coupons().size(); i++) {
                FlightCoupon coupon = flight.coupons().get(i);
                String path = "metadata.coupons[" + i + "]";
                if (checks.present(coupon, path)) {
                    checks.optionalText(coupon.carrier(), path + ".carrier", 8);
                    checks.text(coupon.flightNumber(), path + ".flight_number", 16);
                    checks.text(coupon.from(), path + ".from", 64);
                    checks.text(coupon.to(), path + ".to", 64);
                    checks.timeRange(coupon.departure(), coupon.arrival(), path);
                }
            }
        }
    }

    private void validateHotel(HotelMetadata hotel, Checks checks) {
        checks.text(hotel.name(), "metadata.name", 256);
        HotelAddress address = hotel.address();
        if (checks.present(address, "metadata.address")) {
            checks.text(address.street(), "metadata.address.street", 256);
            checks.text(address.city(), "metadata.address.city", 128);
            checks.text(address.country(), "metadata.address.country", 64);
        }
        boolean hasCheckIn = checks.present(hotel.checkIn(), "metadata.check_in");
        boolean hasCheckOut = checks.present(hotel.checkOut(), "metadata.check_out");
        boolean hasNights = checks.present(hotel.nights(), "metadata.nights");
        if (hasCheckIn && hasCheckOut && hasNights) {
            long nightsBetween = ChronoUnit.DAYS.between(hotel.checkIn(), hotel.checkOut());
            if (nightsBetween < 1) {
                checks.fail("metadata.check_out must be after metadata.check_in");
            } else if (hotel.nights() != nightsBetween) {
                checks.fail("metadata.nights must equal the days between check_in and check_out");
            }
        }
        checks.amount(hotel.roomRatePerNight(), "metadata.room_rate_per_night");
        if (checks.list(hotel.extras(), "metadata.extras", MAX_CHARGE_LINES, false)) {
            for (int i = 0; i < hotel.extras().size(); i++) {
                HotelExtra extra = hotel.extras().get(i);
                String path = "metadata.extras[" + i + "]";
                if (checks.present(extra, path)) {
                    checks.text(extra.name(), path + ".name", 128);
                    checks.amount(extra.amount(), path + ".amount");
                }
            }
        }
    }

    private void validateRail(RailMetadata rail, Checks checks) {
        if (checks.list(rail.legs(), "metadata.legs", MAX_SEGMENTS, true)) {
            for (int i = 0; i < rail.legs().size(); i++) {
                RailLeg leg = rail.legs().get(i);
                String path = "metadata.legs[" + i + "]";
                if (checks.present(leg, path)) {
                    checks.text(leg.origin(), path + ".origin", 128);
                    checks.text(leg.destination(), path + ".destination", 128);
                    checks.timeRange(leg.departure(), leg.arrival(), path);
                    checks.optionalText(leg.trainNumber(), path + ".train_number", 32);
                    checks.optionalText(leg.travelClass(), path + ".class", 32);
                }
            }
        }
    }

    private void validateNavanFee(NavanFeeMetadata navanFee, Checks checks) {
        checks.present(navanFee.feeKind(), "metadata.fee_kind");
        checks.optionalText(navanFee.description(), "metadata.description", 512);
        checks.optionalText(navanFee.relatedExternalId(), "metadata.related_external_id", 128);
    }

    private static final class Checks {

        private final List<String> violations = new ArrayList<>();
        private final Integer currencyScale;

        Checks(Integer currencyScale) {
            this.currencyScale = currencyScale;
        }

        boolean hasKnownCurrency() {
            return currencyScale != null;
        }

        boolean present(Object value, String field) {
            if (value == null) {
                fail(field + " is required");
                return false;
            }
            return true;
        }

        void text(String value, String field, int maxLength) {
            if (value == null || value.isBlank()) {
                fail(field + " is required");
            } else {
                optionalText(value, field, maxLength);
            }
        }

        void optionalText(String value, String field, int maxLength) {
            if (value != null && value.length() > maxLength) {
                fail(field + " must be at most " + maxLength + " characters");
            }
        }

        boolean list(List<?> values, String field, int maxSize, boolean requireOne) {
            if (!present(values, field)) {
                return false;
            }
            if (requireOne && values.isEmpty()) {
                fail(field + " must contain at least one entry");
                return false;
            }
            if (values.size() > maxSize) {
                fail(field + " must contain at most " + maxSize + " entries");
                return false;
            }
            return true;
        }

        void amount(BigDecimal value, String field) {
            if (!present(value, field)) {
                return;
            }
            if (value.signum() < 0) {
                fail(field + " must not be negative");
            }
            if (hasKnownCurrency() && value.stripTrailingZeros().scale() > currencyScale) {
                fail(field + " must have at most " + currencyScale + " decimal places");
            }
        }

        void rate(BigDecimal rate, String field) {
            if (rate == null) {
                return;
            }
            if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
                fail(field + " must be a fraction between 0 and 1, e.g. 0.07");
            } else if (rate.stripTrailingZeros().scale() > Money.RATE_SCALE) {
                fail(field + " must have at most " + Money.RATE_SCALE + " decimal places");
            }
        }

        void timeRange(Instant departure, Instant arrival, String path) {
            boolean hasDeparture = present(departure, path + ".departure");
            boolean hasArrival = present(arrival, path + ".arrival");
            if (hasDeparture && hasArrival && arrival.isBefore(departure)) {
                fail(path + ".arrival must not be before departure");
            }
        }

        void fail(String violation) {
            violations.add(violation);
        }

        void throwIfAny() {
            if (!violations.isEmpty()) {
                throw new ValidationException(violations);
            }
        }
    }
}
