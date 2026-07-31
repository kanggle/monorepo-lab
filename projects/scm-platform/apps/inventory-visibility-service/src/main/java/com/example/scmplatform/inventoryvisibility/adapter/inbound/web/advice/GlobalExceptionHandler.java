package com.example.scmplatform.inventoryvisibility.adapter.inbound.web.advice;

import com.example.scmplatform.inventoryvisibility.domain.error.NodeNotFoundException;
import com.example.scmplatform.inventoryvisibility.domain.error.NodeTypeConflictException;
import com.example.scmplatform.inventoryvisibility.domain.error.NodeUnreachableException;
import com.example.scmplatform.inventoryvisibility.domain.error.ReadModelCorruptException;
import com.example.scmplatform.inventoryvisibility.domain.error.SnapshotStaleException;
import com.example.web.dto.ErrorResponse;
import com.example.web.exception.CommonGlobalExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps domain exceptions to the platform error envelope.
 * Error codes follow rules/domains/scm.md Inventory Visibility section.
 *
 * <p><strong>ADR-MONO-058 § D2 (TASK-SCM-BE-055)</strong>: the framework/non-domain arms
 * (404 {@code NoResourceFound}/{@code NoHandlerFound}, 405 {@code MethodNotSupported}
 * incl. the RFC 7231 {@code Allow} header, 415 {@code MediaTypeNotSupported}, 400
 * malformed-body / missing-header / missing-parameter, 409 optimistic lock,
 * {@code @Valid} violations, and the catch-all 500) are inherited from
 * {@code libs/java-web-servlet}'s {@link CommonGlobalExceptionHandler} instead of being
 * hand-copied here. Only genuinely service-owned policy stays below.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends CommonGlobalExceptionHandler {

    /**
     * scm publishes <strong>422</strong> for {@code @Valid} constraint violations and for
     * {@code IllegalArgumentException} at the controller boundary
     * ({@code inventory-visibility-api.md} § Error codes — "{@code VALIDATION_ERROR} |
     * 400/422"; the 422 rows at {@code POST /nodes} and {@code /observed-stock}), where
     * the shared default is 400. One override moves both inherited arms; see
     * {@link CommonGlobalExceptionHandler#validationFailureStatus()}.
     */
    @Override
    protected HttpStatus validationFailureStatus() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }

    @ExceptionHandler(NodeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNodeNotFound(NodeNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("NODE_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(NodeUnreachableException.class)
    public ResponseEntity<ErrorResponse> handleNodeUnreachable(NodeUnreachableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of("NODE_UNREACHABLE", e.getMessage()));
    }

    /**
     * TASK-SCM-BE-046 — a registration request's externalId is already registered
     * under a different node type (e.g. a wms auto-registered warehouse). A
     * repeat registration of the SAME type never reaches this handler — that
     * path is idempotent (find-or-register, no-op) in
     * {@code RegisterThirdPartyLogisticsNodeService}.
     */
    @ExceptionHandler(NodeTypeConflictException.class)
    public ResponseEntity<ErrorResponse> handleNodeTypeConflict(NodeTypeConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("NODE_TYPE_CONFLICT", e.getMessage()));
    }

    @ExceptionHandler(SnapshotStaleException.class)
    public ResponseEntity<ErrorResponse> handleSnapshotStale(SnapshotStaleException e) {
        // 200 with stale warning (not an error — eventual consistency is expected, S5)
        return ResponseEntity.status(HttpStatus.OK)
                .body(ErrorResponse.of("SNAPSHOT_STALE", e.getMessage()));
    }

    /**
     * Corrupt persisted read-model data (e.g. a non-UUID id column) is a
     * server-side data-integrity fault — 500, and logged so it leaves a
     * diagnostic trail. TASK-SCM-BE-021 / TASK-MONO-171: previously this escaped
     * as a bare {@link IllegalArgumentException} → a misleading silent 422.
     */
    @ExceptionHandler(ReadModelCorruptException.class)
    public ResponseEntity<ErrorResponse> handleReadModelCorrupt(ReadModelCorruptException e) {
        log.error("Read-model data integrity fault", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }

    /**
     * <strong>Overrides</strong> the shared base only to keep the diagnostic log line —
     * the status and body come straight from {@code super}, i.e. 422 via
     * {@link #validationFailureStatus()}. The log was added deliberately by
     * TASK-SCM-BE-021: "its absence made TASK-MONO-171 hard to diagnose". Server
     * data-integrity faults take the 500 path above.
     */
    @Override
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Rejecting request with 422 VALIDATION_ERROR: {}", e.getMessage());
        return super.handleIllegalArgument(e);
    }

    /**
     * Not covered by {@link CommonGlobalExceptionHandler} — without this arm a non-UUID
     * path variable would fall through to the catch-all and regress the documented 400
     * into a 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", "Invalid parameter: " + e.getName()));
    }
}
