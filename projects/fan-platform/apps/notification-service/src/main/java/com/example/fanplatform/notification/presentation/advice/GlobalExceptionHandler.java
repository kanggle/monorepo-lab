package com.example.fanplatform.notification.presentation.advice;

import com.example.fanplatform.notification.domain.notification.NotificationNotFoundException;
import com.example.web.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps notification-service domain exceptions to the platform error envelope
 * ({@code libs/java-web}'s {@code ErrorResponse} — {@code {code, message, timestamp}}).
 *
 * <ul>
 *   <li>404 — NOTIFICATION_NOT_FOUND (missing / cross-account / cross-tenant —
 *       no existence leak)</li>
 * </ul>
 *
 * <p>Cross-cutting handlers are inherited from {@link AbstractDomainExceptionHandler}
 * (fan-platform policy: data-integrity, type-mismatch, illegal-state, JPA optimistic
 * lock) and, above it, {@code libs/java-web-servlet}'s {@code CommonGlobalExceptionHandler}
 * (framework arms: 400 / 404 / 405 / 409 / 415 / 500) — ADR-MONO-058 § D2.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends AbstractDomainExceptionHandler {

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotificationNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("NOTIFICATION_NOT_FOUND", e.getMessage()));
    }
}
