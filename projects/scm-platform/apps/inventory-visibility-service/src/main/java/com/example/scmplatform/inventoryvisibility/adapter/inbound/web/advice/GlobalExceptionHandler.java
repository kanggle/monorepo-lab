package com.example.scmplatform.inventoryvisibility.adapter.inbound.web.advice;

import com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto.ApiErrorBody;
import com.example.scmplatform.inventoryvisibility.domain.error.NodeNotFoundException;
import com.example.scmplatform.inventoryvisibility.domain.error.NodeTypeConflictException;
import com.example.scmplatform.inventoryvisibility.domain.error.NodeUnreachableException;
import com.example.scmplatform.inventoryvisibility.domain.error.ReadModelCorruptException;
import com.example.scmplatform.inventoryvisibility.domain.error.SnapshotStaleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Set;

/**
 * Maps domain exceptions to the platform error envelope.
 * Error codes follow rules/domains/scm.md Inventory Visibility section.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NodeNotFoundException.class)
    public ResponseEntity<ApiErrorBody> handleNodeNotFound(NodeNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorBody.of("NODE_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(NodeUnreachableException.class)
    public ResponseEntity<ApiErrorBody> handleNodeUnreachable(NodeUnreachableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiErrorBody.of("NODE_UNREACHABLE", e.getMessage()));
    }

    /**
     * TASK-SCM-BE-046 — a registration request's externalId is already registered
     * under a different node type (e.g. a wms auto-registered warehouse). A
     * repeat registration of the SAME type never reaches this handler — that
     * path is idempotent (find-or-register, no-op) in
     * {@code RegisterThirdPartyLogisticsNodeService}.
     */
    @ExceptionHandler(NodeTypeConflictException.class)
    public ResponseEntity<ApiErrorBody> handleNodeTypeConflict(NodeTypeConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorBody.of("NODE_TYPE_CONFLICT", e.getMessage()));
    }

    @ExceptionHandler(SnapshotStaleException.class)
    public ResponseEntity<ApiErrorBody> handleSnapshotStale(SnapshotStaleException e) {
        // 200 with stale warning (not an error — eventual consistency is expected, S5)
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiErrorBody.of("SNAPSHOT_STALE", e.getMessage()));
    }

    /**
     * Corrupt persisted read-model data (e.g. a non-UUID id column) is a
     * server-side data-integrity fault — 500, and logged so it leaves a
     * diagnostic trail. TASK-SCM-BE-021 / TASK-MONO-171: previously this escaped
     * as a bare {@link IllegalArgumentException} → a misleading silent 422.
     */
    @ExceptionHandler(ReadModelCorruptException.class)
    public ResponseEntity<ApiErrorBody> handleReadModelCorrupt(ReadModelCorruptException e) {
        log.error("Read-model data integrity fault", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorBody.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorBody> handleIllegalArgument(IllegalArgumentException e) {
        // Client-boundary validation (e.g. a bad path/query value). Logged at warn
        // so even a genuine 422 leaves a trail — its absence made TASK-MONO-171
        // hard to diagnose. Server data-integrity faults take the 500 path above.
        log.warn("Rejecting request with 422 VALIDATION_ERROR: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("VALIDATION_ERROR", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorBody> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorBody.of("VALIDATION_ERROR", "Invalid parameter: " + e.getName()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorBody> handleNoResourceFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorBody.of("NOT_FOUND", "The requested resource was not found"));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiErrorBody> handleNoHandlerFound(NoHandlerFoundException e) {
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
