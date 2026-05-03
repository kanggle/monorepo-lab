package com.example.fanplatform.artist.adapter.in.web.advice;

import com.example.fanplatform.artist.adapter.in.web.dto.response.ApiErrorBody;
import com.example.fanplatform.artist.application.exception.AdminRoleRequiredException;
import com.example.fanplatform.artist.application.exception.AlreadyMemberException;
import com.example.fanplatform.artist.application.exception.ArtistArchivedException;
import com.example.fanplatform.artist.application.exception.ArtistGroupNotFoundException;
import com.example.fanplatform.artist.application.exception.ArtistNotFoundException;
import com.example.fanplatform.artist.application.exception.ArtistNotPublishedException;
import com.example.fanplatform.artist.application.exception.FandomAlreadyExistsException;
import com.example.fanplatform.artist.application.exception.FandomNotFoundException;
import com.example.fanplatform.artist.application.exception.GroupNameConflictException;
import com.example.fanplatform.artist.application.exception.StageNameConflictException;
import com.example.fanplatform.artist.domain.artist.Artist.IllegalStateTransitionException;
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
 * code conventions follow TASK-FAN-BE-003 § Acceptance Criteria:
 *
 * <ul>
 *   <li>401 — UNAUTHORIZED (handled by Spring Security entry point)</li>
 *   <li>403 — TENANT_FORBIDDEN / FORBIDDEN (admin-only)</li>
 *   <li>404 — ARTIST_NOT_FOUND / ARTIST_GROUP_NOT_FOUND / FANDOM_NOT_FOUND</li>
 *   <li>409 — STAGE_NAME_CONFLICT / GROUP_NAME_CONFLICT / CONFLICT (optimistic lock)</li>
 *   <li>422 — STATE_TRANSITION_INVALID / ALREADY_MEMBER / FANDOM_ALREADY_EXISTS /
 *             ARTIST_NOT_PUBLISHED / ARTIST_ARCHIVED / VALIDATION_ERROR</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ArtistNotFoundException.class)
    public ResponseEntity<ApiErrorBody> handleArtistNotFound(ArtistNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorBody.of("ARTIST_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(ArtistGroupNotFoundException.class)
    public ResponseEntity<ApiErrorBody> handleGroupNotFound(ArtistGroupNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorBody.of("ARTIST_GROUP_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(FandomNotFoundException.class)
    public ResponseEntity<ApiErrorBody> handleFandomNotFound(FandomNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorBody.of("FANDOM_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(StageNameConflictException.class)
    public ResponseEntity<ApiErrorBody> handleStageNameConflict(StageNameConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorBody.of("STAGE_NAME_CONFLICT", e.getMessage()));
    }

    @ExceptionHandler(GroupNameConflictException.class)
    public ResponseEntity<ApiErrorBody> handleGroupNameConflict(GroupNameConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorBody.of("GROUP_NAME_CONFLICT", e.getMessage()));
    }

    @ExceptionHandler(AlreadyMemberException.class)
    public ResponseEntity<ApiErrorBody> handleAlreadyMember(AlreadyMemberException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("ALREADY_MEMBER", e.getMessage()));
    }

    @ExceptionHandler(FandomAlreadyExistsException.class)
    public ResponseEntity<ApiErrorBody> handleFandomExists(FandomAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("FANDOM_ALREADY_EXISTS", e.getMessage()));
    }

    @ExceptionHandler(ArtistNotPublishedException.class)
    public ResponseEntity<ApiErrorBody> handleArtistNotPublished(ArtistNotPublishedException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("ARTIST_NOT_PUBLISHED", e.getMessage()));
    }

    @ExceptionHandler(ArtistArchivedException.class)
    public ResponseEntity<ApiErrorBody> handleArtistArchived(ArtistArchivedException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("ARTIST_ARCHIVED", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<ApiErrorBody> handleStateTransition(IllegalStateTransitionException e) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", e.from().name());
        details.put("to", e.to().name());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("STATE_TRANSITION_INVALID",
                        "Invalid artist status transition", details));
    }

    @ExceptionHandler(AdminRoleRequiredException.class)
    public ResponseEntity<ApiErrorBody> handleAdminRoleRequired(AdminRoleRequiredException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorBody.of("FORBIDDEN", e.getMessage()));
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
        // Includes Jackson enum deserialization failures (e.g. role=FORMER_MEMBER
        // against the controller-boundary AddRole enum). Per artist-api.md the
        // contract surface for these cases is 422 VALIDATION_ERROR.
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
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
