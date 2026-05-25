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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
 *
 * <p>Cross-cutting handlers (optimistic lock, integrity, validation,
 * type-mismatch, illegal-argument, illegal-state, general) are inherited from
 * {@link AbstractDomainExceptionHandler}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends AbstractDomainExceptionHandler {

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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorBody> handleMalformed(HttpMessageNotReadableException e) {
        // Includes Jackson enum deserialization failures (e.g. role=FORMER_MEMBER
        // against the controller-boundary AddRole enum). Per artist-api.md the
        // contract surface for these cases is 422 VALIDATION_ERROR.
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("VALIDATION_ERROR", "Malformed request body"));
    }
}
