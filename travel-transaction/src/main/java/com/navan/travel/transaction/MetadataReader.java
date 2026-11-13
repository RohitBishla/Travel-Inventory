package com.navan.travel.transaction;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.navan.travel.common.ValidationException;
import com.navan.travel.transaction.metadata.FeeKind;
import com.navan.travel.transaction.metadata.FlightMetadata;
import com.navan.travel.transaction.metadata.HotelMetadata;
import com.navan.travel.transaction.metadata.NavanFeeMetadata;
import com.navan.travel.transaction.metadata.RailMetadata;
import com.navan.travel.transaction.metadata.TransactionMetadata;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class MetadataReader {

    private static final String SUPPORTED_FEE_KINDS = Arrays.stream(FeeKind.values())
            .map(FeeKind::code)
            .collect(Collectors.joining(", "));

    private final ObjectMapper objectMapper;

    MetadataReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    TransactionMetadata read(TransactionType type, JsonNode metadata) {
        if (metadata == null || !metadata.isObject()) {
            throw new ValidationException("metadata must be a JSON object");
        }
        if (type == TransactionType.NAVAN_FEE) {
            requireKnownFeeKind(metadata.get("fee_kind"));
        }
        Class<? extends TransactionMetadata> metadataClass = switch (type) {
            case FLIGHT -> FlightMetadata.class;
            case HOTEL -> HotelMetadata.class;
            case RAIL -> RailMetadata.class;
            case NAVAN_FEE -> NavanFeeMetadata.class;
        };
        try {
            return objectMapper.treeToValue(metadata, metadataClass);
        } catch (JacksonException e) {
            throw new ValidationException("metadata is malformed: " + e.getOriginalMessage());
        }
    }

    private void requireKnownFeeKind(JsonNode feeKind) {
        if (feeKind == null || feeKind.isNull()) {
            throw new ValidationException("metadata.fee_kind is required");
        }
        if (!feeKind.isString() || FeeKind.fromCode(feeKind.stringValue()).isEmpty()) {
            throw new ValidationException("metadata.fee_kind must be one of: " + SUPPORTED_FEE_KINDS);
        }
    }
}
