package com.kanggle.platformconsole.bff.adapter.inbound.web;

import com.kanggle.platformconsole.bff.adapter.outbound.http.MissingTenantException;
import com.kanggle.platformconsole.bff.application.usecase.UnknownNotificationDomainException;
import com.kanggle.platformconsole.bff.application.usecase.UpstreamUnauthorizedException;
import com.kanggle.platformconsole.bff.domain.credential.MissingCredentialException;
import com.example.web.dto.ErrorResponse;
import com.example.web.exception.CommonGlobalExceptionHandler;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;

/**
 * Global error envelope for console-bff inbound web controllers.
 *
 * <p>Error shape: {@code { "code": "...", "message": "...", "timestamp": "..." }}.
 * No stack traces in responses (platform/error-handling.md).
 *
 * <p><b>Scope intentionally limited to {@code adapter.inbound.web}.</b> Without
 * an explicit {@code basePackages}, {@code @RestControllerAdvice} applies to
 * every controller in the application context — including Spring Boot
 * Actuator endpoints (e.g. {@code /actuator/prometheus}). When an actuator
 * endpoint throws an exception (which it can for legitimate reasons during
 * metric registry scrape composition), a wide
 * {@code @ExceptionHandler(Exception.class)} would swallow the throwable and
 * convert it to a generic {@code INTERNAL_ERROR} envelope — both
 * (a) breaking observability ({@code /actuator/prometheus} cannot return the
 * Prometheus exposition format) and (b) hiding the original stack trace from
 * diagnostics. Limiting to the inbound-web package lets actuator exceptions
 * propagate to Spring Boot's default error handling, which is correct for
 * those endpoints. (CI surface: PR #669 first three runs surfaced this as
 * "/actuator/prometheus 500 INTERNAL_SERVER_ERROR".)
 *
 * <p><b>ADR-MONO-058 § D2 (TASK-PC-BE-016)</b> — the generic (non-domain)
 * exception-handler tail this class used to implement locally (405, 415, and
 * the catch-all 500) was fleet-duplicated boilerplate. It now extends
 * {@link CommonGlobalExceptionHandler} ({@code libs/java-web-servlet}), which
 * supplies that tail plus 404 handling for unmapped routes
 * ({@code NoResourceFoundException} / {@code NoHandlerFoundException}) that
 * this class never had — those previously fell into the local catch-all as a
 * bare {@code 500}. The {@code basePackages} scoping above is preserved
 * because it is declared on this subclass's own {@code @RestControllerAdvice}
 * annotation — inheriting {@code @ExceptionHandler} methods does not inherit
 * or require the parent to carry any controller-advice annotation itself
 * ({@link CommonGlobalExceptionHandler} carries none; adopters supply their
 * own advice scope). All domain-specific handlers below are unchanged in
 * behavior — {@link #handleIllegalArgument} is overridden only because the
 * shared default emits a different {@code code} string ({@code
 * VALIDATION_ERROR}) than console-bff's own published {@code BAD_REQUEST};
 * the override's return type must match the parent method's {@code
 * ResponseEntity<ErrorResponse>} exactly (Java override covariance), so it
 * uses {@link ErrorResponse} instead of this class's local {@code ObjectNode}
 * builder — the serialized JSON shape is identical either way.
 */
@RestControllerAdvice(basePackages = "com.kanggle.platformconsole.bff.adapter.inbound.web")
public class GlobalExceptionHandler extends CommonGlobalExceptionHandler {

    @ExceptionHandler(MissingCredentialException.class)
    public ResponseEntity<ObjectNode> handleMissingCredential(MissingCredentialException ex) {
        return error(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", ex.getMessage());
    }

    @ExceptionHandler(MissingTenantException.class)
    public ResponseEntity<ObjectNode> handleMissingTenant(MissingTenantException ex) {
        // § 2.4.9.1 error envelope: 400 NO_ACTIVE_TENANT when X-Tenant-Id absent.
        return error(HttpStatus.BAD_REQUEST, "NO_ACTIVE_TENANT", ex.getMessage());
    }

    @ExceptionHandler(UpstreamUnauthorizedException.class)
    public ResponseEntity<ObjectNode> handleUpstreamUnauthorized(UpstreamUnauthorizedException ex) {
        // § 2.4.9.1 / § 2.4.4 D3: any outbound leg 401 → composition-level 401.
        return error(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", ex.getMessage());
    }

    @ExceptionHandler(UnknownNotificationDomainException.class)
    public ResponseEntity<ObjectNode> handleUnknownNotificationDomain(UnknownNotificationDomainException ex) {
        // ADR-MONO-043 P3a / contract § 2.3: an unknown owner domain is treated
        // as an unknown notification (no existence leak) → 404 NOTIFICATION_NOT_FOUND.
        return error(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", ex.getMessage());
    }

    /**
     * Overrides {@link CommonGlobalExceptionHandler#handleIllegalArgument} — console-bff's own
     * published {@code code} for this arm has always been {@code BAD_REQUEST}, not the shared
     * default's {@code VALIDATION_ERROR}. Preserved unchanged (TASK-PC-BE-016 Scope: "Preserve
     * all domain-specific @ExceptionHandler methods unchanged"). See class Javadoc for why the
     * return type had to move to {@link ErrorResponse}.
     */
    @Override
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("BAD_REQUEST", ex.getMessage()));
    }

    /**
     * A downstream producer returned a non-2xx status on a leg that <b>propagates</b> it (the
     * mark-read passthrough — contract § 4.5). The GET aggregate/overview legs degrade errors to a
     * 200 partial inside {@code CompositionEngine} and never reach here; only the single-domain
     * mark-read proxy lets a {@link HttpStatusCodeException} surface. Without this arm it would
     * fall to the inherited catch-all → a generic 500, so a producer 404 (mark-read on another
     * recipient's notification — § 4.5) or a 503 became an opaque 500 (then 502 at console-web),
     * turning a should-degrade into a hard error (TASK-PC-BE-011).
     *
     * <p>Maps the producer status through faithfully: 404 → {@code NOTIFICATION_NOT_FOUND}
     * (existence-leak-safe, passed through inline-actionably); 401 → {@code TOKEN_INVALID};
     * 403 → {@code PERMISSION_DENIED} (a non-eligible operator degrades the bell); any other
     * (incl. 5xx) → {@code 503 DOWNSTREAM_ERROR} (unavailable, never a bare 500).
     */
    @ExceptionHandler(HttpStatusCodeException.class)
    public ResponseEntity<ObjectNode> handleDownstreamStatus(HttpStatusCodeException ex) {
        int status = ex.getStatusCode().value();
        return switch (status) {
            case 404 -> error(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND",
                    "The notification was not found");
            case 401 -> error(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID",
                    "The downstream rejected the credential");
            case 403 -> error(HttpStatus.FORBIDDEN, "PERMISSION_DENIED",
                    "Not permitted for this notification");
            default -> error(HttpStatus.SERVICE_UNAVAILABLE, "DOWNSTREAM_ERROR",
                    "The notification service is unavailable");
        };
    }

    /**
     * A downstream call timed out / could not connect on a propagating leg (mark-read). Same
     * should-degrade-not-500 rationale as {@link #handleDownstreamStatus} — surface it as
     * {@code 503 DOWNSTREAM_ERROR}, not a generic 500 (TASK-PC-BE-011).
     */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ObjectNode> handleDownstreamUnavailable(ResourceAccessException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "DOWNSTREAM_ERROR",
                "The notification service is unavailable");
    }

    private ResponseEntity<ObjectNode> error(HttpStatus status, String code, String message) {
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put("code", code);
        body.put("message", message != null ? message : "");
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}
