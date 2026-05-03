package com.example.fanplatform.community.presentation.advice;

import com.example.fanplatform.community.application.UpdatePostUseCase;
import com.example.fanplatform.community.application.exception.AlreadyFollowingException;
import com.example.fanplatform.community.application.exception.CommentNotFoundException;
import com.example.fanplatform.community.application.exception.MembershipRequiredException;
import com.example.fanplatform.community.application.exception.NotFollowingException;
import com.example.fanplatform.community.application.exception.PermissionDeniedException;
import com.example.fanplatform.community.application.exception.PostNotFoundException;
import com.example.fanplatform.community.application.exception.SelfFollowForbiddenException;
import com.example.fanplatform.community.domain.post.status.InvalidStateTransitionException;
import com.example.fanplatform.community.presentation.dto.ApiErrorBody;
import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps domain/application exceptions to the platform error envelope. Status
 * code conventions follow TASK-FAN-BE-002 § Acceptance Criteria:
 *
 * <ul>
 *   <li>401 — UNAUTHORIZED (handled by Spring Security entry point)</li>
 *   <li>403 — TENANT_FORBIDDEN / PERMISSION_DENIED / MEMBERSHIP_REQUIRED</li>
 *   <li>404 — POST_NOT_FOUND / COMMENT_NOT_FOUND</li>
 *   <li>409 — ALREADY_FOLLOWING / CONFLICT (optimistic lock)</li>
 *   <li>422 — POST_STATUS_TRANSITION_INVALID / SELF_FOLLOW_FORBIDDEN /
 *             EDIT_WINDOW_EXPIRED / VALIDATION_ERROR</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PostNotFoundException.class)
    public ResponseEntity<ApiErrorBody> handlePostNotFound(PostNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorBody.of("POST_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(CommentNotFoundException.class)
    public ResponseEntity<ApiErrorBody> handleCommentNotFound(CommentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorBody.of("COMMENT_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<ApiErrorBody> handlePermission(PermissionDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorBody.of("PERMISSION_DENIED", e.getMessage()));
    }

    @ExceptionHandler(MembershipRequiredException.class)
    public ResponseEntity<ApiErrorBody> handleMembership(MembershipRequiredException e) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("requiredTier", e.requiredTier().name());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorBody.of("MEMBERSHIP_REQUIRED",
                        "Membership tier required: " + e.requiredTier(), details));
    }

    @ExceptionHandler(AlreadyFollowingException.class)
    public ResponseEntity<ApiErrorBody> handleAlreadyFollowing(AlreadyFollowingException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorBody.of("ALREADY_FOLLOWING", "Already following this artist"));
    }

    @ExceptionHandler(NotFollowingException.class)
    public ResponseEntity<ApiErrorBody> handleNotFollowing(NotFollowingException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorBody.of("NOT_FOLLOWING", "Not currently following this artist"));
    }

    @ExceptionHandler(SelfFollowForbiddenException.class)
    public ResponseEntity<ApiErrorBody> handleSelfFollow(SelfFollowForbiddenException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("SELF_FOLLOW_FORBIDDEN", "An account cannot follow itself"));
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ApiErrorBody> handleInvalidTransition(InvalidStateTransitionException e) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", e.from().name());
        details.put("to", e.to().name());
        details.put("actor", e.actor().name());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("POST_STATUS_TRANSITION_INVALID",
                        "Invalid post status transition", details));
    }

    @ExceptionHandler(UpdatePostUseCase.EditWindowExpiredException.class)
    public ResponseEntity<ApiErrorBody> handleEditWindow(UpdatePostUseCase.EditWindowExpiredException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("EDIT_WINDOW_EXPIRED",
                        "PUBLISHED post is past the edit window"));
    }

    @ExceptionHandler({OptimisticLockException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ApiErrorBody> handleOptimisticLock(Exception e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorBody.of("CONFLICT", "Concurrent modification detected. Please retry."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorBody> handleIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorBody.of("CONFLICT", "Data integrity violation"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorBody> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorBody> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("VALIDATION_ERROR", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorBody> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorBody.of("VALIDATION_ERROR", "Invalid parameter: " + e.getName()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorBody> handleMalformed(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorBody.of("VALIDATION_ERROR", "Malformed request body"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorBody> handleIllegalState(IllegalStateException e) {
        log.warn("illegal state at controller boundary", e);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("ILLEGAL_STATE", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorBody> handleGeneral(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorBody.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
