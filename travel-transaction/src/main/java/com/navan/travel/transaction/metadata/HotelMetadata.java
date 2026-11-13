package com.navan.travel.transaction.metadata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.UnaryOperator;

import com.navan.travel.transaction.TransactionType;

public record HotelMetadata(
        String name,
        HotelAddress address,
        LocalDate checkIn,
        LocalDate checkOut,
        Integer nights,
        BigDecimal roomRatePerNight,
        List<HotelExtra> extras) implements TransactionMetadata {

    public HotelMetadata {
        extras = extras == null ? List.of() : extras;
    }

    @Override
    public TransactionType type() {
        return TransactionType.HOTEL;
    }

    @Override
    public HotelMetadata mapAmounts(UnaryOperator<BigDecimal> amountMapper) {
        List<HotelExtra> mappedExtras = extras.stream()
                .map(extra -> new HotelExtra(extra.name(), amountMapper.apply(extra.amount())))
                .toList();
        return new HotelMetadata(name, address, checkIn, checkOut, nights,
                amountMapper.apply(roomRatePerNight), mappedExtras);
    }
}
