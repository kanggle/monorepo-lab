package com.wms.master.adapter.in.web.advice;

import com.wms.master.adapter.in.web.dto.response.ApiErrorEnvelope;
import com.wms.master.domain.exception.ConcurrencyConflictException;
import com.wms.master.domain.exception.ImmutableFieldException;
import com.wms.master.domain.exception.InvalidStateTransitionException;
import com.wms.master.domain.exception.MasterDomainException;
import com.wms.master.domain.exception.ValidationException;
import com.wms.master.domain.exception.WarehouseCodeDuplicateException;
import com.wms.master.domain.exception.WarehouseNotFoundException;
import com.wms.master.domain.exception.ZoneCodeDuplicateException;
import com.wms.master.domain.exception.ZoneNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(WarehouseNotFoundException.class)
    public ResponseEntity<ApiErrorEnvelope> handleNotFound(WarehouseNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(WarehouseCodeDuplicateException.class)
    public ResponseEntity<ApiErrorEnvelope> handleCodeDuplicate(WarehouseCodeDuplicateException ex) {
        return build(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(ZoneNotFoundException.class)
    public ResponseEntity<ApiErrorEnvelope> handleZoneNotFound(ZoneNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(ZoneCodeDuplicateException.class)
    public ResponseEntity<ApiErrorEnvelope> handleZoneCodeDuplicate(ZoneCodeDuplicateException ex) {
        return build(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(ConcurrencyConflictException.class)
    public ResponseEntity<ApiErrorEnvelope> handleConflict(ConcurrencyConflictException ex) {
        return build(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ApiErrorEnvelope> handleInvalidTransition(InvalidStateTransitionException ex) {
        return build(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(ImmutableFieldException.class)
    public ResponseEntity<ApiErrorEnvelope> handleImmutableField(ImmutableFieldException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiErrorEnvelope> handleDomainValidation(ValidationException ex) {
        return build(HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler(MasterDomainException.class)
    public ResponseEntity<ApiErrorEnvelope> handleDomain(MasterDomainException ex) {
        log.warn("Unmapped domain exception: {} — {}", ex.getCode(), ex.getMessage());
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorEnvelope> handleBeanValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        Map<String, Object> details = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err ->
                details.put(err.getField(), err.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(ApiErrorEnvelope.of("VALIDATION_ERROR", message, details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorEnvelope> handleMalformed(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ApiErrorEnvelope.of("VALIDATION_ERROR", "Malformed request body"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorEnvelope> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
                .body(ApiErrorEnvelope.of("VALIDATION_ERROR",
                        "Missing required parameter: " + ex.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorEnvelope> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(ApiErrorEnvelope.of("VALIDATION_ERROR",
                        "Invalid value for parameter: " + ex.getName()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorEnvelope> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorEnvelope.of("INTERNAL_ERROR", "Internal server error"));
    }

    private static ResponseEntity<ApiErrorEnvelope> build(HttpStatus status, MasterDomainException ex) {
        return ResponseEntity.status(status)
                .body(ApiErrorEnvelope.of(ex.getCode(), ex.getMessage()));
    }
}
