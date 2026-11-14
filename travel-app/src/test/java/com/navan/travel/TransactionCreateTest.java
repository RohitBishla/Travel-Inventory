package com.navan.travel;

import static com.navan.travel.support.TestJson.decimal;
import static com.navan.travel.support.TestJson.text;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.navan.travel.support.ApiResponse;
import com.navan.travel.support.ApiTestBase;
import com.navan.travel.support.Fixtures;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

class TransactionCreateTest extends ApiTestBase {

    @Test
    void createsEveryTypeFromSeedsAndReadsBackTheTypedStructure() {
        Map<String, String> ids = createTripTransactions();

        JsonNode flight = api.get("/transactions/" + ids.get("flight")).body();
        assertThat(text(flight, "type")).isEqualTo("flight");
        assertThat(decimal(flight.get("total"))).isEqualByComparingTo("220.00");
        assertThat(text(flight.get("metadata"), "origin")).isEqualTo("TXL");
        assertThat(text(flight.get("metadata"), "destination")).isEqualTo("LHR");
        JsonNode coupon = flight.get("metadata").get("coupons").get(0);
        assertThat(text(coupon, "flight_number")).isEqualTo("BA982");
        assertThat(text(coupon, "carrier")).isEqualTo("BA");
        assertThat(text(coupon, "departure")).isEqualTo("2026-03-11T07:00:00Z");
        assertThat(text(flight.get("tax_lines").get(0), "name")).isEqualTo("Airport tax");
        assertThat(text(flight.get("fee_lines").get(0), "name")).isEqualTo("YQ");

        JsonNode hotel = api.get("/transactions/" + ids.get("hotel")).body();
        JsonNode hotelMetadata = hotel.get("metadata");
        assertThat(text(hotelMetadata, "name")).isEqualTo("Hotel Mitte London");
        assertThat(text(hotelMetadata.get("address"), "street")).isEqualTo("12 Kingsway");
        assertThat(text(hotelMetadata.get("address"), "city")).isEqualTo("London");
        assertThat(text(hotelMetadata.get("address"), "country")).isEqualTo("GB");
        assertThat(hotelMetadata.get("nights").asInt()).isEqualTo(2);
        assertThat(text(hotelMetadata.get("extras").get(0), "name")).isEqualTo("Breakfast");
        assertThat(decimal(hotel.get("tax_lines").get(0).get("rate"))).isEqualByComparingTo("0.07");

        JsonNode railLegs = api.get("/transactions/" + ids.get("rail")).body().get("metadata").get("legs");
        assertThat(railLegs).hasSize(2);
        assertThat(text(railLegs.get(0), "destination")).isEqualTo("Lille Europe");
        assertThat(text(railLegs.get(1), "origin")).isEqualTo("Lille Europe");
        assertThat(text(railLegs.get(1), "train_number")).isEqualTo("EST9122");
        assertThat(text(railLegs.get(1), "class")).isEqualTo("standard");

        JsonNode tripFee = api.get("/transactions/" + ids.get("trip_fee")).body();
        assertThat(text(tripFee, "type")).isEqualTo("navan_fee");
        assertThat(text(tripFee.get("metadata"), "fee_kind")).isEqualTo("trip_fee");
        assertThat(text(tripFee.get("metadata"), "related_external_id")).isEqualTo("flight-TXL-LHR-2026-03-11");

        JsonNode agentCallFee = api.get("/transactions/" + ids.get("agent_call_fee")).body();
        assertThat(text(agentCallFee.get("metadata"), "fee_kind")).isEqualTo("agent_call_fee");
    }

    @Test
    void returnsTheStoredTransactionWithServerIdAndLocation() {
        ApiResponse response = api.post("/transactions", Fixtures.seed("rail"));

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.transactionId()).startsWith("txn_");
        assertThat(response.body().has("id")).isFalse();
        assertThat(response.headers().firstValue("Location")).hasValue("/transactions/" + response.transactionId());
        assertThat(api.get("/transactions/" + response.transactionId()).body()).isEqualTo(response.body());
    }

    @Test
    void rejectsUnknownType() {
        ObjectNode car = Fixtures.seed("flight", "car-1");
        car.put("type", "car");

        assertRejected(car);
    }

    @Test
    void rejectsUnknownNavanFeeKind() {
        ObjectNode fee = Fixtures.seed("trip_fee", "fee-unknown-kind");
        ((ObjectNode) fee.get("metadata")).put("fee_kind", "lounge_fee");

        ApiResponse response = assertRejected(fee);
        assertThat(response.body().get("details").toString()).contains("fee_kind");
    }

    @Test
    void rejectsEmptyMetadata() {
        ObjectNode flight = Fixtures.seed("flight", "flight-empty-metadata");
        flight.set("metadata", flight.objectNode());

        assertRejected(flight);
    }

    @Test
    void rejectsFlightCarryingHotelMetadata() {
        ObjectNode flight = Fixtures.seed("flight", "flight-as-hotel");
        flight.set("metadata", Fixtures.seed("hotel").get("metadata"));

        assertRejected(flight);
    }

    @Test
    void rejectsMoreDecimalPlacesThanTheCurrencyAllows() {
        ObjectNode rail = Fixtures.seed("rail", "rail-three-decimals");
        rail.put("total", new BigDecimal("94.001"));

        ApiResponse response = assertRejected(rail);
        assertThat(response.body().get("details").toString()).contains("total must have at most 2 decimal places");
    }

    @Test
    void returnsNotFoundForUnknownOrMalformedIds() {
        assertThat(api.get("/transactions/txn_999999").status()).isEqualTo(404);
        assertThat(api.get("/transactions/not-an-id").status()).isEqualTo(404);
    }

    private ApiResponse assertRejected(JsonNode body) {
        ApiResponse response = api.post("/transactions", body);
        assertThat(response.status()).as("%s", response.body()).isEqualTo(400);
        assertThat(countRows("transactions")).isZero();
        return response;
    }
}
