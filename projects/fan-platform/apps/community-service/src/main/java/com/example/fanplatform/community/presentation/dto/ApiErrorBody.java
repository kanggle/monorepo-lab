package com.example.fanplatform.community.presentation.dto;

import com.example.web.dto.ErrorResponse;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * The {@code details}-carrying <strong>extension</strong> of the shared error envelope.
 *
 * <p>{@code platform/error-handling.md § Error Response Format} fixes the envelope at
 * {@code {code, message, timestamp}} — which is exactly {@code libs/java-web}'s
 * {@link ErrorResponse} — and explicitly permits a service to <em>extend</em> it with
 * structured context:
 *
 * <blockquote>"Services that return additional context (trace/request ids, structured
 * {@code details}) are permitted to extend this envelope, but the three fields above
 * must always be present."</blockquote>
 *
 * <p>This record is that extension and <strong>nothing else</strong>. Since
 * ADR-MONO-058 § D2 (TASK-FAN-BE-038) every arm that carries no {@code details} returns
 * the shared {@link ErrorResponse} directly; only the arms whose {@code details} payload
 * is documented in {@code specs/contracts/http/community-api.md} use this type. The
 * 2-argument {@code of(code, message)} factory was therefore removed — with it this
 * record was a full duplicate of {@link ErrorResponse}, which is the duplication D2
 * exists to close. The surviving {@link #withDetails} factory <em>requires</em> a
 * {@code details} argument, so the type cannot drift back into that role.
 *
 * <p><strong>{@code timestamp} is a pre-formatted {@code String}, not an
 * {@link Instant}</strong> — same as {@link ErrorResponse}, and deliberately so. Held as
 * an {@code Instant} its rendering depends on the {@code ObjectMapper}'s
 * {@code WRITE_DATES_AS_TIMESTAMPS} setting, which is <em>not</em> a Jackson or
 * {@code spring-web} default: only Spring Boot's {@code JacksonAutoConfiguration}
 * disables it, and a service that contributes its own {@code ObjectMapper} bean loses
 * that (artist-service does — see its copy of this class). Formatting at construction
 * makes the envelope mapper-independent and identical to {@link ErrorResponse} by
 * construction rather than by configuration (TASK-FAN-BE-038).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorBody(String code, String message, Map<String, Object> details, String timestamp) {

    /**
     * @param details structured context documented by the HTTP contract for this error
     *                code. An arm with nothing to add must return {@link ErrorResponse}
     *                instead of calling this with an empty map — this factory does not
     *                validate, deliberately: throwing from inside an
     *                {@code @ExceptionHandler} loses the envelope entirely and turns a
     *                4xx into a container 500.
     */
    public static ApiErrorBody withDetails(String code, String message, Map<String, Object> details) {
        return new ApiErrorBody(code, message, details, Instant.now().toString());
    }
}
