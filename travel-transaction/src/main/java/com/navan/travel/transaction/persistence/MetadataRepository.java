package com.navan.travel.transaction.persistence;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import com.navan.travel.transaction.TransactionType;
import com.navan.travel.transaction.metadata.FlightMetadata;
import com.navan.travel.transaction.metadata.HotelMetadata;
import com.navan.travel.transaction.metadata.NavanFeeMetadata;
import com.navan.travel.transaction.metadata.RailMetadata;
import com.navan.travel.transaction.metadata.TransactionMetadata;

@Repository
class MetadataRepository {

    private final FlightDetailsStore flights;
    private final HotelDetailsStore hotels;
    private final RailDetailsStore rails;
    private final NavanFeeDetailsStore navanFees;

    MetadataRepository(FlightDetailsStore flights, HotelDetailsStore hotels,
                       RailDetailsStore rails, NavanFeeDetailsStore navanFees) {
        this.flights = flights;
        this.hotels = hotels;
        this.rails = rails;
        this.navanFees = navanFees;
    }

    void insert(long transactionId, TransactionMetadata metadata) {
        switch (metadata) {
            case FlightMetadata flight -> flights.insert(transactionId, flight);
            case HotelMetadata hotel -> hotels.insert(transactionId, hotel);
            case RailMetadata rail -> rails.insert(transactionId, rail);
            case NavanFeeMetadata navanFee -> navanFees.insert(transactionId, navanFee);
        }
    }

    void replace(long transactionId, TransactionMetadata metadata) {
        storeFor(metadata.type()).delete(transactionId);
        insert(transactionId, metadata);
    }

    Map<Long, TransactionMetadata> findByType(Map<TransactionType, List<Long>> transactionIdsByType) {
        Map<Long, TransactionMetadata> metadataById = new HashMap<>();
        transactionIdsByType.forEach((type, ids) -> metadataById.putAll(storeFor(type).findByTransactionIds(ids)));
        return metadataById;
    }

    private TypeDetailsStore<? extends TransactionMetadata> storeFor(TransactionType type) {
        return switch (type) {
            case FLIGHT -> flights;
            case HOTEL -> hotels;
            case RAIL -> rails;
            case NAVAN_FEE -> navanFees;
        };
    }
}
