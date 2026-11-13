package com.navan.travel.transaction.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.navan.travel.common.ChildRow;
import com.navan.travel.common.SqlTime;
import com.navan.travel.transaction.metadata.RailLeg;
import com.navan.travel.transaction.metadata.RailMetadata;

@Repository
class RailDetailsStore implements TypeDetailsStore<RailMetadata> {

    private final JdbcClient jdbc;

    RailDetailsStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(long transactionId, RailMetadata rail) {
        for (int legNo = 0; legNo < rail.legs().size(); legNo++) {
            RailLeg leg = rail.legs().get(legNo);
            jdbc.sql("""
                            INSERT INTO rail_legs (transaction_id, leg_no, origin, destination,
                                                   departure_at, arrival_at, train_number, travel_class)
                            VALUES (:transactionId, :legNo, :origin, :destination,
                                    :departure, :arrival, :trainNumber, :travelClass)
                            """)
                    .param("transactionId", transactionId)
                    .param("legNo", legNo)
                    .param("origin", leg.origin())
                    .param("destination", leg.destination())
                    .param("departure", SqlTime.toSql(leg.departure()))
                    .param("arrival", SqlTime.toSql(leg.arrival()))
                    .param("trainNumber", leg.trainNumber())
                    .param("travelClass", leg.travelClass())
                    .update();
        }
    }

    @Override
    public void delete(long transactionId) {
        jdbc.sql("DELETE FROM rail_legs WHERE transaction_id = :id").param("id", transactionId).update();
    }

    @Override
    public Map<Long, RailMetadata> findByTransactionIds(Collection<Long> transactionIds) {
        List<ChildRow<RailLeg>> rows = jdbc.sql("""
                        SELECT transaction_id, origin, destination, departure_at, arrival_at,
                               train_number, travel_class
                        FROM rail_legs
                        WHERE transaction_id IN (:ids)
                        ORDER BY transaction_id, leg_no
                        """)
                .param("ids", transactionIds)
                .query((rs, rowNum) -> new ChildRow<>(rs.getLong("transaction_id"), new RailLeg(
                        rs.getString("origin"),
                        rs.getString("destination"),
                        SqlTime.read(rs, "departure_at"),
                        SqlTime.read(rs, "arrival_at"),
                        rs.getString("train_number"),
                        rs.getString("travel_class"))))
                .list();
        return ChildRow.groupByParent(rows).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> new RailMetadata(entry.getValue())));
    }
}
