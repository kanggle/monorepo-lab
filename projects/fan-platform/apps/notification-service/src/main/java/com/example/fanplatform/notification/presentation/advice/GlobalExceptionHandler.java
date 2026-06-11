package com.example.fanplatform.notification.presentation.advice;

import com.example.fanplatform.notification.domain.notification.NotificationNotFoundException;
import com.example.fanplatform.notification.presentation.dto.ApiErrorBody;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps notification-service domain exceptions to the platform error envelope.
 *
 * <ul>
 *   <li>404 — NOTIFICATION_NOT_FOUND (missing / cross-account / cross-tenant —
 *       no existence leak)</li>
 * </ul>
 *
 * <p>Cross-cutting handlers (optimistic lock, integrity, validation,
 * type-mismatch, illegal-argument/state, general) are inherited from
 * {@link AbstractDomainExceptionHandler}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends AbstractDomainExceptionHandler {

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ApiErrorBody> handleNotFound(NotificationNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorBody.of("NOTIFICATION_NOT_FOUND", e.getMessage()));
    }
}
