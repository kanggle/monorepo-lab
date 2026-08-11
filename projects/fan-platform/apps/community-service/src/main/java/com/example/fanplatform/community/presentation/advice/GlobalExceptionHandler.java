package com.example.fanplatform.community.presentation.advice;

import com.example.fanplatform.community.application.UpdatePostUseCase;
import com.example.fanplatform.community.application.exception.AlreadyFollowingException;
import com.example.fanplatform.community.application.exception.CommentNotFoundException;
import com.example.fanplatform.community.application.exception.MembershipRequiredException;
import com.example.fanplatform.community.application.exception.NotFollowingException;
import com.example.fanplatform.community.application.exception.PermissionDeniedException;
import com.example.fanplatform.community.application.exception.PostNotFoundException;
import com.example.fanplatform.community.application.exception.SelfFollowForbiddenException;
import com.example.fanplatform.community.application.exception.UnknownArtistAccountException;
import com.example.fanplatform.community.domain.post.status.InvalidStateTransitionException;
import com.example.fanplatform.community.presentation.dto.ApiErrorBody;
import com.example.web.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
 *
 * <p><strong>Envelope (ADR-MONO-058 § D2)</strong>: arms that carry no structured
 * context return {@code libs/java-web}'s shared {@link ErrorResponse}
 * ({@code {code, message, timestamp}}). The two arms whose {@code details} payload is
 * documented in {@code community-api.md} — {@code MEMBERSHIP_REQUIRED} and
 * {@code POST_STATUS_TRANSITION_INVALID} — return {@link ApiErrorBody}, the
 * {@code details}-carrying extension {@code platform/error-handling.md § Error Response
 * Format} explicitly permits. A {@code details}-less {@code ApiErrorBody} and an
 * {@code ErrorResponse} serialise to the same three keys, so the split is invisible on
 * the wire.
 *
 * <p>Cross-cutting handlers are inherited from {@link AbstractDomainExceptionHandler}
 * (fan-platform policy: data-integrity, type-mismatch, illegal-state, JPA optimistic
 * lock, the 422 validation status) and, above it, {@code CommonGlobalExceptionHandler}
 * (framework arms: 400 / 404 / 405 / 409 / 415 / 500).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends AbstractDomainExceptionHandler {

    @ExceptionHandler(PostNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePostNotFound(PostNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("POST_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(CommentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCommentNotFound(CommentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("COMMENT_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<ErrorResponse> handlePermission(PermissionDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("PERMISSION_DENIED", e.getMessage()));
    }

    /** Contract: {@code community-api.md} — 403 with {@code details.requiredTier}. */
    @ExceptionHandler(MembershipRequiredException.class)
    public ResponseEntity<ApiErrorBody> handleMembership(MembershipRequiredException e) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("requiredTier", e.requiredTier().name());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorBody.withDetails("MEMBERSHIP_REQUIRED",
                        "Membership tier required: " + e.requiredTier(), details));
    }

    @ExceptionHandler(AlreadyFollowingException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyFollowing(AlreadyFollowingException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("ALREADY_FOLLOWING", "Already following this artist"));
    }

    @ExceptionHandler(NotFollowingException.class)
    public ResponseEntity<ErrorResponse> handleNotFollowing(NotFollowingException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("NOT_FOLLOWING", "Not currently following this artist"));
    }

    @ExceptionHandler(SelfFollowForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleSelfFollow(SelfFollowForbiddenException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("SELF_FOLLOW_FORBIDDEN", "An account cannot follow itself"));
    }

    /**
     * TASK-FAN-BE-045 AC-6. 422 rather than 404: the follow target is a rejected
     * <em>input</em>, and a 404 would say the follow resource is missing. It is
     * also raised when artist-service could not be reached — the check is
     * fail-closed, so "could not ask" and "not an artist" answer identically on
     * purpose (a distinguishable outage answer would be an oracle for probing).
     */
    @ExceptionHandler(UnknownArtistAccountException.class)
    public ResponseEntity<ErrorResponse> handleUnknownArtistAccount(UnknownArtistAccountException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("UNKNOWN_ARTIST_ACCOUNT",
                        "Follow target is not a known artist account in this tenant"));
    }

    /** Contract: {@code community-api.md} — 422 with {@code details {from, to, actor}}. */
    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ApiErrorBody> handleInvalidTransition(InvalidStateTransitionException e) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", e.from().name());
        details.put("to", e.to().name());
        details.put("actor", e.actor().name());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.withDetails("POST_STATUS_TRANSITION_INVALID",
                        "Invalid post status transition", details));
    }

    @ExceptionHandler(UpdatePostUseCase.EditWindowExpiredException.class)
    public ResponseEntity<ErrorResponse> handleEditWindow(UpdatePostUseCase.EditWindowExpiredException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("EDIT_WINDOW_EXPIRED",
                        "PUBLISHED post is past the edit window"));
    }

    // HttpMessageNotReadableException (malformed body) → 400 VALIDATION_ERROR
    // "Malformed request body" is inherited verbatim from CommonGlobalExceptionHandler;
    // the local copy this class carried was byte-identical (ADR-MONO-058 § D2).
}
