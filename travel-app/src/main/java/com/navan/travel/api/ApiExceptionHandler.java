package com.navan.travel.api;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.navan.travel.common.ConflictException;
import com.navan.travel.common.NotFoundException;
import com.navan.travel.common.PublicIds;
import com.navan.travel.common.ValidationException;
import com.navan.travel.transaction.identity.DuplicateTransactionException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String RETRY_AFTER_SECONDS = "1";

    private final JsonMapper jsonMapper;

    public ApiExceptionHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @ExceptionHandler(ValidationException.class)
    ResponseEntity<ApiError> handleValidation(ValidationException e) {
        ApiError body = new ApiError("validation_failed", "Request is invalid", e.violations(), null);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException e) {
        if (e.getMostSpecificCause() instanceof MismatchedInputException mismatch
                && mismatch.getTargetType() != null && mismatch.getTargetType().isEnum()) {
            String violation = fieldPath(mismatch) + " must be one of: " + allowedValues(mismatch.getTargetType());
            return ResponseEntity.badRequest()
                    .body(new ApiError("validation_failed", "Request is invalid", List.of(violation), null));
        }
        String cause = String.valueOf(e.getMostSpecificCause().getMessage()).lines().findFirst().orElse("");
        return ResponseEntity.badRequest().body(ApiError.of("malformed_request",
                "Request body is not valid JSON or a field has the wrong type: " + cause));
    }

    private String allowedValues(Class<?> enumType) {
        return Arrays.stream(enumType.getEnumConstants())
                .map(constant -> jsonMapper.convertValue(constant, String.class))
                .collect(Collectors.joining(", "));
    }

    private static String fieldPath(MismatchedInputException mismatch) {
        StringBuilder path = new StringBuilder();
        for (JacksonException.Reference reference : mismatch.getPath()) {
            if (reference.getPropertyName() != null) {
                path.append(path.isEmpty() ? "" : ".").append(reference.getPropertyName());
            } else {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleBadParameter(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(ApiError.of("malformed_request",
                "Query parameter '" + e.getName() + "' has an invalid value"));
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of("not_found", e.getMessage()));
    }

    @ExceptionHandler(DuplicateTransactionException.class)
    ResponseEntity<ApiError> handleDuplicate(DuplicateTransactionException e) {
        ApiError body = new ApiError("duplicate_external_id", e.getMessage(), null,
                PublicIds.transaction(e.existingId()));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .location(TransactionController.location(e.existingId()))
                .body(body);
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiError> handleConflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(e.code(), e.getMessage()));
    }

    @ExceptionHandler({TransientDataAccessException.class, DataAccessResourceFailureException.class,
            CannotCreateTransactionException.class})
    ResponseEntity<ApiError> handleDatabaseUnavailable(RuntimeException e) {
        log.warn("Database unavailable or contended, asking client to retry", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(ApiError.of("temporarily_unavailable", "The service is busy, retry shortly"));
    }
}
