package com.wms.outbound.adapter.in.web.advice;

import static com.example.common.persistence.DataIntegrityViolations.isUniqueViolation;

import com.example.web.dto.ErrorResponse;
import com.example.web.exception.CommonGlobalExceptionHandler;
import com.wms.outbound.domain.exception.ExternalServiceUnavailableException;
import com.wms.outbound.domain.exception.OrderNoDuplicateException;
import com.wms.outbound.domain.exception.OrderNotFoundException;
import com.wms.outbound.domain.exception.OutboundDomainException;
import com.wms.outbound.domain.exception.PackingUnitNotFoundException;
import com.wms.outbound.domain.exception.PickingRequestNotFoundException;
import com.wms.outbound.domain.exception.ShipmentNotFoundException;
import com.wms.outbound.domain.exception.TenantScopeDeniedException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps outbound domain exceptions to the flat {@code {code, message, timestamp}}
 * error body declared in {@code specs/contracts/http/outbound-service-api.md}
 * § Error Envelope.
 *
 * <p>Domain exceptions extend {@link OutboundDomainException} and each
 * override {@link OutboundDomainException#errorCode()} with the
 * contract-defined string from {@code outbound-service-api.md} § Error Codes.
 * This handler reads {@code exception.errorCode()} so the response
 * {@code code} is always the granular contract-defined string.
 *
 * <p><strong>ADR-MONO-058 § D2 (TASK-BE-567)</strong>: the non-domain / framework
 * arms (404 {@code NoResourceFound} / {@code NoHandlerFound}, 405
 * {@code MethodNotSupported} incl. the RFC 7231 {@code Allow} header, 415
 * {@code MediaTypeNotSupported}, 400 {@code @Valid} / {@code IllegalArgument} /
 * malformed-body / missing-header / missing-parameter, and the catch-all 500) are
 * inherited from {@code libs/java-web-servlet}'s
 * {@link CommonGlobalExceptionHandler} instead of being hand-copied here. That is
 * wire-preserving for this service because its former {@code ApiErrorEnvelope}
 * was a component-for-component duplicate of {@code libs/java-web}'s
 * {@link ErrorResponse} ({@code code}, {@code message}, pre-formatted ISO-8601
 * {@code timestamp} string) — unlike {@code master-service} / {@code admin-service},
 * whose envelope nests everything under a top-level {@code error} key and which
 * therefore compose the shared handler instead of extending it.
 *
 * <p>Only the arms below are genuinely outbound-owned policy and stay service-side.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends CommonGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Domain exception → HTTP status override. Most {@link OutboundDomainException}s are
     * business-rule violations → 422 (the default in {@link #handleDomain}, covering e.g.
     * {@code OrderAlreadyShippedException}); this table lists only the exceptions that map
     * elsewhere (404 lookups, 409 duplicates). {@link ExternalServiceUnavailableException}
     * is handled separately (503, with vendor logging). Keyed on the exact concrete class.
     */
    private static final Map<Class<? extends OutboundDomainException>, HttpStatus> DOMAIN_STATUS = Map.of(
            OrderNotFoundException.class, HttpStatus.NOT_FOUND,
            PickingRequestNotFoundException.class, HttpStatus.NOT_FOUND,
            PackingUnitNotFoundException.class, HttpStatus.NOT_FOUND,
            ShipmentNotFoundException.class, HttpStatus.NOT_FOUND,
            OrderNoDuplicateException.class, HttpStatus.CONFLICT,
            // Cross-tenant access denial (TASK-MONO-304 / ADR-MONO-022 § D9) → 403.
            TenantScopeDeniedException.class, HttpStatus.FORBIDDEN);

    @ExceptionHandler(OutboundDomainException.class)
    public ResponseEntity<ErrorResponse> handleDomain(OutboundDomainException e) {
        HttpStatus status = DOMAIN_STATUS.getOrDefault(e.getClass(), HttpStatus.UNPROCESSABLE_ENTITY);
        return body(status, e.errorCode(), e.getMessage());
    }

    /**
     * External vendor (e.g. ERP webhook out) unreachable / circuit-open /
     * retry-exhausted. Mapped to 503 per {@code platform/error-handling.md}
     * (registered globally for {@code integration-heavy} trait). Specific subtype
     * handler — takes precedence over {@link #handleDomain} (which would default it to 422).
     * Defensive fallback for any path that lets it escape.
     */
    @ExceptionHandler(ExternalServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleExternalUnavailable(ExternalServiceUnavailableException e) {
        log.warn("external_service_unavailable vendor={} reason={}", e.getVendor(), e.getMessage());
        return body(HttpStatus.SERVICE_UNAVAILABLE, e.errorCode(), e.getMessage());
    }

    /**
     * Optimistic-lock collision. Declared on Spring's <em>broader</em>
     * {@link OptimisticLockingFailureException} — the shared base only covers the
     * narrower {@link ObjectOptimisticLockingFailureException} subtype, so this arm
     * is not a duplicate of it and stays service-local.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleConflict(OptimisticLockingFailureException e) {
        return body(HttpStatus.CONFLICT, "CONFLICT",
                "Optimistic lock conflict — retry with fresh state");
    }

    /**
     * Routes the narrower JPA-raised subtype back to {@link #handleConflict} so that both
     * subtypes answer with outbound's published message rather than the shared base's
     * different wording. Overriding (same signature) rather than declaring a second
     * {@code @ExceptionHandler} for the same type is required — the latter is a boot-time
     * {@code Ambiguous @ExceptionHandler method} failure.
     */
    @Override
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
        return handleConflict(e);
    }

    /**
     * DB constraint-violation backstop (no domain-specific handler claimed it). The status is
     * decided at runtime, so the mapping is selective (TASK-MONO-450 / TASK-BE-542):
     *
     * <ul>
     *   <li><b>Unique violation</b> (SQLSTATE 23505) → 409 CONFLICT: a duplicate is a
     *       client-visible conflict (e.g. a re-sent {@code orderNo}).</li>
     *   <li><b>FK / NOT NULL / CHECK violation</b> → 500 INTERNAL_ERROR: these are
     *       <em>server</em> defects, not client conflicts. Deliberately left at 500 with
     *       {@code log.error} so they stay loud in logs and alerting rather than being masked
     *       as a 409 that monitoring never sees.</li>
     * </ul>
     *
     * <p>The discriminant is {@link com.example.common.persistence.DataIntegrityViolations}
     * (SQLSTATE-based, pure JDK): the exception <em>type</em> cannot distinguish a unique
     * violation and message matching breaks on driver upgrade.
     *
     * <p>Not covered by the shared base — stays service-local.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException e) {
        if (isUniqueViolation(e)) {
            log.debug("unique constraint violation → 409: {}", e.getMessage());
            return body(HttpStatus.CONFLICT, "CONFLICT",
                    "Resource already exists or violates a unique constraint");
        }
        // FK / NOT NULL / CHECK violations are server defects — keep them loud (500), not a 409.
        log.error("Non-unique data integrity violation", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Internal server error");
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ErrorResponse> handleForbidden(RuntimeException e) {
        return body(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "Insufficient privileges for this operation");
    }

    /**
     * Path-variable / query-parameter type mismatch (e.g. a non-UUID {@code {id}}).
     * Not covered by the shared base — without this arm it would fall through to the
     * inherited catch-all and regress the contract's documented 400 into a 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", e.getMessage());
    }

    private static ResponseEntity<ErrorResponse> body(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(code, message));
    }
}
