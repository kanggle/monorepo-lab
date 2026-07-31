package com.example.erp.notification.presentation.advice;

import com.example.erp.notification.domain.error.NotificationNotFoundException;
import com.example.erp.notification.presentation.security.ReadAccessDeniedException;
import com.example.web.dto.ErrorResponse;
import com.example.web.exception.CommonGlobalExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps notification-service domain / presentation exceptions to the
 * notification-api.md error envelope ({@code { code, message, timestamp }}).
 * Codes per platform/error-handling.md erp Notification section.
 *
 * <p><strong>ADR-MONO-058 § D2 (TASK-ERP-BE-038)</strong> — the non-domain arms
 * (404 {@code NoResourceFound}/{@code NoHandlerFound}, 405 {@code MethodNotSupported}
 * incl. the RFC 7231 {@code Allow} header, 415 {@code MediaTypeNotSupported}, 400
 * {@code @Valid} / {@code IllegalArgumentException} / malformed-body / missing-header /
 * missing-parameter, 409 {@code ObjectOptimisticLockingFailureException}, and the
 * catch-all 500) are inherited from {@code libs/java-web-servlet}'s
 * {@link CommonGlobalExceptionHandler} rather than hand-copied here. Only the three
 * arms below are genuinely erp-owned.
 *
 * <p><strong>Envelope</strong> — this service emits the shared
 * {@link ErrorResponse} on every arm. Its local {@code ApiErrorBody} was deleted:
 * the 4th {@code details} field was never populated by any code path, and
 * {@code notification-api.md} documents no code as carrying it (unlike
 * masterdata/approval, whose contracts do — those two keep a {@code details}-carrying
 * envelope). {@code @JsonInclude(NON_NULL)} always dropped the null {@code details},
 * so the wire shape is unchanged.
 *
 * <p><strong>Validation status</strong> — notification-api.md publishes
 * {@code 400 VALIDATION_ERROR}, which is already
 * {@link CommonGlobalExceptionHandler#validationFailureStatus()}'s default; no override.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends CommonGlobalExceptionHandler {

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotificationNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(NotificationNotFoundException.CODE, e.getMessage()));
    }

    @ExceptionHandler(ReadAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handlePermissionDenied(ReadAccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(ReadAccessDeniedException.CODE, e.getMessage()));
    }

    /**
     * Not covered by the shared base — without this arm a non-numeric {@code page} /
     * {@code size} query parameter would fall through to the catch-all and regress the
     * documented 400 into a 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", "Invalid parameter: " + e.getName()));
    }
}
