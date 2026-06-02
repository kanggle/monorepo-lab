package com.example.scmplatform.inventoryvisibility.adapter.inbound.web.advice;

import com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto.ApiErrorBody;
import com.example.scmplatform.inventoryvisibility.domain.error.NodeNotFoundException;
import com.example.scmplatform.inventoryvisibility.domain.error.NodeUnreachableException;
import com.example.scmplatform.inventoryvisibility.domain.error.ReadModelCorruptException;
import com.example.scmplatform.inventoryvisibility.domain.error.SnapshotStaleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorBody> handleGeneral(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorBody.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
