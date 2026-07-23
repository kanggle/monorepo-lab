package com.example.scmplatform.logistics.adapter.inbound.web.advice;

import com.example.scmplatform.logistics.adapter.inbound.web.dto.ApiErrorBody;
import com.example.scmplatform.logistics.domain.error.CarrierUnroutableException;
import com.example.scmplatform.logistics.domain.error.DispatchNotFoundException;
import com.example.scmplatform.logistics.domain.error.IllegalDispatchTransitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Set;

/**
 * Maps domain exceptions to the scm platform error envelope {@code { code, message }}.
 * Logistics codes: {@code DISPATCH_NOT_FOUND} (404), {@code CARRIER_UNROUTABLE} (422),
 * {@code DISPATCH_ALREADY_COMPLETED} (409) — registered in {@code platform/error-handling.md}
 * + {@code rules/domains/scm.md}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DispatchNotFoundException.class)
    public ResponseEntity<ApiErrorBody> handleDispatchNotFound(DispatchNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorBody.of("DISPATCH_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(IllegalDispatchTransitionException.class)
    public ResponseEntity<ApiErrorBody> handleIllegalTransition(IllegalDispatchTransitionException e) {
        // A completed dispatch cannot be re-driven into a non-terminal state (S1).
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorBody.of("DISPATCH_ALREADY_COMPLETED", e.getMessage()));
    }

    @ExceptionHandler(CarrierUnroutableException.class)
    public ResponseEntity<ApiErrorBody> handleCarrierUnroutable(CarrierUnroutableException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("CARRIER_UNROUTABLE", e.getMessage()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorBody> handleOptimisticLock(OptimisticLockingFailureException e) {
        log.warn("Concurrent modification on dispatch: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorBody.of("CONCURRENT_MODIFICATION",
                        "The dispatch was modified concurrently; retry the request"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorBody> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorBody.of("VALIDATION_ERROR", "Invalid parameter: " + e.getName()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorBody> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Rejecting request with 422 VALIDATION_ERROR: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("VALIDATION_ERROR", e.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorBody> handleNoResourceFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorBody.of("NOT_FOUND", "The requested resource was not found"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorBody> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        Set<HttpMethod> supported = e.getSupportedHttpMethods();
        if (supported != null && !supported.isEmpty()) {
            builder.allow(supported.toArray(new HttpMethod[0]));
        }
        return builder.body(ApiErrorBody.of("METHOD_NOT_ALLOWED",
                "HTTP method not supported for this endpoint"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorBody> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiErrorBody.of("UNSUPPORTED_MEDIA_TYPE",
                        "Request Content-Type is not supported by this endpoint"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorBody> handleGeneral(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorBody.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
