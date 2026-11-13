package com.navan.travel.transaction.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.navan.travel.common.ChildRow;
import com.navan.travel.common.SqlTime;
import com.navan.travel.transaction.metadata.FlightCoupon;
import com.navan.travel.transaction.metadata.FlightMetadata;

@Repository
class FlightDetailsStore implements TypeDetailsStore<FlightMetadata> {

    private final JdbcClient jdbc;

    FlightDetailsStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(long transactionId, FlightMetadata flight) {
        jdbc.sql("""
                        INSERT INTO flight_details (transaction_id, origin, destination, fare)
                        VALUES (:transactionId, :origin, :destination, :fare)
                        """)
                .param("transactionId", transactionId)
                .param("origin", flight.origin())
                .param("destination", flight.destination())
                .param("fare", flight.fare())
                .update();

        for (int couponNo = 0; couponNo < flight.coupons().size(); couponNo++) {
            FlightCoupon coupon = flight.coupons().get(couponNo);
            jdbc.sql("""
                            INSERT INTO flight_coupons (transaction_id, coupon_no, carrier, flight_number,
                                                        from_location, to_location, departure_at, arrival_at)
                            VALUES (:transactionId, :couponNo, :carrier, :flightNumber,
                                    :from, :to, :departure, :arrival)
                            """)
                    .param("transactionId", transactionId)
                    .param("couponNo", couponNo)
                    .param("carrier", coupon.carrier())
                    .param("flightNumber", coupon.flightNumber())
                    .param("from", coupon.from())
                    .param("to", coupon.to())
                    .param("departure", SqlTime.toSql(coupon.departure()))
                    .param("arrival", SqlTime.toSql(coupon.arrival()))
                    .update();
        }
    }

    @Override
    public void delete(long transactionId) {
        jdbc.sql("DELETE FROM flight_coupons WHERE transaction_id = :id").param("id", transactionId).update();
        jdbc.sql("DELETE FROM flight_details WHERE transaction_id = :id").param("id", transactionId).update();
    }

    @Override
    public Map<Long, FlightMetadata> findByTransactionIds(Collection<Long> transactionIds) {
        Map<Long, List<FlightCoupon>> couponsByTransaction = findCoupons(transactionIds);
        return jdbc.sql("""
                        SELECT transaction_id, origin, destination, fare
                        FROM flight_details
                        WHERE transaction_id IN (:ids)
                        """)
                .param("ids", transactionIds)
                .query((rs, rowNum) -> {
                    long transactionId = rs.getLong("transaction_id");
                    return new ChildRow<>(transactionId, new FlightMetadata(
                            rs.getString("origin"),
                            rs.getString("destination"),
                            rs.getBigDecimal("fare"),
                            couponsByTransaction.getOrDefault(transactionId, List.of())));
                })
                .list()
                .stream()
                .collect(Collectors.toMap(ChildRow::parentId, ChildRow::value));
    }

    private Map<Long, List<FlightCoupon>> findCoupons(Collection<Long> transactionIds) {
        List<ChildRow<FlightCoupon>> rows = jdbc.sql("""
                        SELECT transaction_id, carrier, flight_number, from_location, to_location,
                               departure_at, arrival_at
                        FROM flight_coupons
                        WHERE transaction_id IN (:ids)
                        ORDER BY transaction_id, coupon_no
                        """)
                .param("ids", transactionIds)
                .query((rs, rowNum) -> new ChildRow<>(rs.getLong("transaction_id"), new FlightCoupon(
                        rs.getString("carrier"),
                        rs.getString("flight_number"),
                        rs.getString("from_location"),
                        rs.getString("to_location"),
                        SqlTime.read(rs, "departure_at"),
                        SqlTime.read(rs, "arrival_at"))))
                .list();
        return ChildRow.groupByParent(rows);
    }
}
