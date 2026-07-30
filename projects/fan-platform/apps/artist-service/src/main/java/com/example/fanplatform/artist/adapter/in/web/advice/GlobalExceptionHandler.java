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
import com.example.web.dto.ErrorResponse;
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
 * <p><strong>Envelope (ADR-MONO-058 § D2)</strong>: arms that carry no structured
 * context return {@code libs/java-web}'s shared {@link ErrorResponse}
 * ({@code {code, message, timestamp}}). The one arm whose {@code details} payload is
 * documented in {@code artist-api.md} — {@code STATE_TRANSITION_INVALID}
 * ({@code details.from}, {@code details.to}) — returns {@link ApiErrorBody}, the
 * {@code details}-carrying extension {@code platform/error-handling.md § Error Response
 * Format} explicitly permits.
 *
 * <p>Cross-cutting handlers are inherited from {@link AbstractDomainExceptionHandler}
 * (fan-platform policy) and, above it, {@code CommonGlobalExceptionHandler}
 * (framework arms: 400 / 404 / 405 / 409 / 415 / 500).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends AbstractDomainExceptionHandler {

    @ExceptionHandler(ArtistNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleArtistNotFound(ArtistNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("ARTIST_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(ArtistGroupNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleGroupNotFound(ArtistGroupNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("ARTIST_GROUP_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(FandomNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFandomNotFound(FandomNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("FANDOM_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(StageNameConflictException.class)
    public ResponseEntity<ErrorResponse> handleStageNameConflict(StageNameConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("STAGE_NAME_CONFLICT", e.getMessage()));
    }

    @ExceptionHandler(GroupNameConflictException.class)
    public ResponseEntity<ErrorResponse> handleGroupNameConflict(GroupNameConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("GROUP_NAME_CONFLICT", e.getMessage()));
    }

    @ExceptionHandler(AlreadyMemberException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyMember(AlreadyMemberException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("ALREADY_MEMBER", e.getMessage()));
    }

    @ExceptionHandler(FandomAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleFandomExists(FandomAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("FANDOM_ALREADY_EXISTS", e.getMessage()));
    }

    @ExceptionHandler(ArtistNotPublishedException.class)
    public ResponseEntity<ErrorResponse> handleArtistNotPublished(ArtistNotPublishedException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("ARTIST_NOT_PUBLISHED", e.getMessage()));
    }

    @ExceptionHandler(ArtistArchivedException.class)
    public ResponseEntity<ErrorResponse> handleArtistArchived(ArtistArchivedException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("ARTIST_ARCHIVED", e.getMessage()));
    }

    /** Contract: {@code artist-api.md} — 422 with {@code details.from} / {@code details.to}. */
    @ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<ApiErrorBody> handleStateTransition(IllegalStateTransitionException e) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", e.from().name());
        details.put("to", e.to().name());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.withDetails("STATE_TRANSITION_INVALID",
                        "Invalid artist status transition", details));
    }

    @ExceptionHandler(AdminRoleRequiredException.class)
    public ResponseEntity<ErrorResponse> handleAdminRoleRequired(AdminRoleRequiredException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("FORBIDDEN", e.getMessage()));
    }

    /**
     * <strong>Deliberate divergence from the shared base, which answers 400 here.</strong>
     * Includes Jackson enum deserialization failures (e.g. {@code role=FORMER_MEMBER}
     * against the controller-boundary {@code AddRole} enum). Per {@code artist-api.md}
     * ("422 VALIDATION_ERROR | malformed JSON / unknown enum value (request body)") the
     * contract surface for these cases is 422.
     *
     * <p>This is an {@code @Override} of
     * {@link com.example.web.exception.CommonGlobalExceptionHandler#handleMalformedRequest}
     * and not a second, differently-named handler: two distinct methods mapping
     * {@code HttpMessageNotReadableException} in one advice is an
     * <em>Ambiguous @ExceptionHandler</em> failure at context startup. Only this one
     * service diverges, so the divergence is carried here rather than as extra shared
     * surface.
     */
    @Override
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedRequest(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("VALIDATION_ERROR", "Malformed request body"));
    }
}
