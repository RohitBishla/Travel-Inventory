package com.navan.travel.transaction;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.navan.travel.common.ValidationException;
import com.navan.travel.transaction.metadata.TransactionMetadata;

@Component
class TransactionDraftFactory {

    private static final String SUPPORTED_TYPES = Arrays.stream(TransactionType.values())
            .map(TransactionType::code)
            .collect(Collectors.joining(", "));

    private final MetadataReader metadataReader;
    private final TransactionValidator validator;

    TransactionDraftFactory(MetadataReader metadataReader, TransactionValidator validator) {
        this.metadataReader = metadataReader;
        this.validator = validator;
    }

    TransactionDraft fromNew(NewTransaction input) {
        TransactionType type = parseType(input.type());
        TransactionDraft draft = new TransactionDraft(
                type,
                input.externalId(),
                input.currency(),
                input.occurredAt(),
                input.total(),
                input.taxLines(),
                input.feeLines(),
                metadataReader.read(type, input.metadata()));
        validator.validate(draft);
        return draft;
    }

    TransactionDraft withChanges(Transaction current, TransactionChanges changes) {
        if (changes.isEmpty()) {
            throw new ValidationException(
                    "PATCH body must contain at least one of: occurred_at, total, tax_lines, fee_lines, metadata");
        }
        TransactionMetadata changedMetadata = changes.metadata() == null
                ? null
                : metadataReader.read(current.type(), changes.metadata());
        TransactionDraft draft = current.withChanges(changes, changedMetadata);
        validator.validate(draft);
        return draft;
    }

    private static TransactionType parseType(String code) {
        if (code == null) {
            throw new ValidationException("type is required");
        }
        return TransactionType.fromCode(code)
                .orElseThrow(() -> new ValidationException("type must be one of: " + SUPPORTED_TYPES));
    }
}
