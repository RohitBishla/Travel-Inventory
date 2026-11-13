package com.navan.travel.transaction.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.navan.travel.common.ChildRow;
import com.navan.travel.transaction.metadata.HotelAddress;
import com.navan.travel.transaction.metadata.HotelExtra;
import com.navan.travel.transaction.metadata.HotelMetadata;

@Repository
class HotelDetailsStore implements TypeDetailsStore<HotelMetadata> {

    private final JdbcClient jdbc;

    HotelDetailsStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(long transactionId, HotelMetadata hotel) {
        jdbc.sql("""
                        INSERT INTO hotel_details (transaction_id, name, address_street, address_city,
                                                   address_country, check_in, check_out, nights,
                                                   room_rate_per_night)
                        VALUES (:transactionId, :name, :street, :city,
                                :country, :checkIn, :checkOut, :nights,
                                :roomRatePerNight)
                        """)
                .param("transactionId", transactionId)
                .param("name", hotel.name())
                .param("street", hotel.address().street())
                .param("city", hotel.address().city())
                .param("country", hotel.address().country())
                .param("checkIn", hotel.checkIn())
                .param("checkOut", hotel.checkOut())
                .param("nights", hotel.nights())
                .param("roomRatePerNight", hotel.roomRatePerNight())
                .update();

        for (int extraNo = 0; extraNo < hotel.extras().size(); extraNo++) {
            HotelExtra extra = hotel.extras().get(extraNo);
            jdbc.sql("""
                            INSERT INTO hotel_extras (transaction_id, extra_no, name, amount)
                            VALUES (:transactionId, :extraNo, :name, :amount)
                            """)
                    .param("transactionId", transactionId)
                    .param("extraNo", extraNo)
                    .param("name", extra.name())
                    .param("amount", extra.amount())
                    .update();
        }
    }

    @Override
    public void delete(long transactionId) {
        jdbc.sql("DELETE FROM hotel_extras WHERE transaction_id = :id").param("id", transactionId).update();
        jdbc.sql("DELETE FROM hotel_details WHERE transaction_id = :id").param("id", transactionId).update();
    }

    @Override
    public Map<Long, HotelMetadata> findByTransactionIds(Collection<Long> transactionIds) {
        Map<Long, List<HotelExtra>> extrasByTransaction = findExtras(transactionIds);
        return jdbc.sql("""
                        SELECT transaction_id, name, address_street, address_city, address_country,
                               check_in, check_out, nights, room_rate_per_night
                        FROM hotel_details
                        WHERE transaction_id IN (:ids)
                        """)
                .param("ids", transactionIds)
                .query((rs, rowNum) -> {
                    long transactionId = rs.getLong("transaction_id");
                    return new ChildRow<>(transactionId, new HotelMetadata(
                            rs.getString("name"),
                            new HotelAddress(
                                    rs.getString("address_street"),
                                    rs.getString("address_city"),
                                    rs.getString("address_country")),
                            rs.getObject("check_in", LocalDate.class),
                            rs.getObject("check_out", LocalDate.class),
                            rs.getInt("nights"),
                            rs.getBigDecimal("room_rate_per_night"),
                            extrasByTransaction.getOrDefault(transactionId, List.of())));
                })
                .list()
                .stream()
                .collect(Collectors.toMap(ChildRow::parentId, ChildRow::value));
    }

    private Map<Long, List<HotelExtra>> findExtras(Collection<Long> transactionIds) {
        List<ChildRow<HotelExtra>> rows = jdbc.sql("""
                        SELECT transaction_id, name, amount
                        FROM hotel_extras
                        WHERE transaction_id IN (:ids)
                        ORDER BY transaction_id, extra_no
                        """)
                .param("ids", transactionIds)
                .query((rs, rowNum) -> new ChildRow<>(rs.getLong("transaction_id"), new HotelExtra(
                        rs.getString("name"),
                        rs.getBigDecimal("amount"))))
                .list();
        return ChildRow.groupByParent(rows);
    }
}
