package com.example.fanplatform.artist.adapter.in.web.advice;

import com.example.fanplatform.artist.adapter.in.web.dto.response.ApiErrorBody;
import com.example.fanplatform.artist.config.RedisCacheConfig;
import com.example.fanplatform.artist.domain.artist.Artist;
import com.example.fanplatform.artist.domain.artist.ArtistStatus;
import com.example.web.dto.ErrorResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-FAN-BE-038 (ADR-MONO-058 § D2) — pins the error-envelope contract that survived
 * the swap to {@code libs/java-web-servlet}'s {@code CommonGlobalExceptionHandler}.
 *
 * <p>artist-service is the only fan service that <strong>overrides</strong> an inherited
 * arm: {@code artist-api.md} publishes "422 VALIDATION_ERROR | malformed JSON / unknown
 * enum value (request body)" where the shared base — and every other adopter — answers
 * 400. {@code malformedBody_returns422} is the guard on that override; without it the
 * adoption would silently move a documented 422 to 400.
 *
 * <p><strong>Changed, on purpose</strong> — a missing required request parameter and a
 * missing required header previously fell through the catch-all
 * {@code @ExceptionHandler(Exception.class)} and answered <em>500 INTERNAL_ERROR</em>;
 * the shared base maps both to 400 VALIDATION_ERROR, matching the validation family
 * {@code artist-api.md} already documents at 400.
 */
@DisplayName("GlobalExceptionHandler 에러 봉투/상태코드 계약 테스트 (ADR-MONO-058 D2)")
class GlobalExceptionHandlerEnvelopeContractTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BoundaryController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("본문 파싱 실패 → 422 VALIDATION_ERROR (artist-api.md 의 의도적 divergence — 공유 base 는 400)")
    void malformedBody_returns422() throws Exception {
        mockMvc.perform(post("/__test/valid-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    @DisplayName("@Valid 제약 위반 → 422 VALIDATION_ERROR (공유 base 기본값 400 이 아님)")
    void validConstraintViolation_returns422() throws Exception {
        mockMvc.perform(post("/__test/valid-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("컨트롤러 경계 IllegalArgumentException → 422 VALIDATION_ERROR")
    void illegalArgument_returns422() throws Exception {
        mockMvc.perform(get("/__test/illegal-argument"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("boundary rejected the argument"));
    }

    @Test
    @DisplayName("IllegalStateException → 422 ILLEGAL_STATE (서비스 로컬 arm, 공유 base 미보유)")
    void illegalState_returns422() throws Exception {
        mockMvc.perform(get("/__test/illegal-state"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE"));
    }

    @Test
    @DisplayName("경로변수 타입 불일치 → 400 VALIDATION_ERROR (artist-api.md L54, 서비스 로컬 arm)")
    void typeMismatch_returns400() throws Exception {
        mockMvc.perform(get("/__test/by-id/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("STATE_TRANSITION_INVALID → 422 + details{from,to} (artist-api.md 문서화 필드)")
    void stateTransitionInvalid_carriesDetails() throws Exception {
        mockMvc.perform(get("/__test/invalid-transition"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("STATE_TRANSITION_INVALID"))
                .andExpect(jsonPath("$.details.from").value("ARCHIVED"))
                .andExpect(jsonPath("$.details.to").value("PUBLISHED"));
    }

    @Test
    @DisplayName("필수 요청 파라미터 누락 → 400 VALIDATION_ERROR (이전에는 catch-all 500)")
    void missingRequiredParam_returns400() throws Exception {
        mockMvc.perform(get("/__test/needs-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Missing required parameter: accountId"));
    }

    @Test
    @DisplayName("필수 헤더 누락 → 400 VALIDATION_ERROR (이전에는 catch-all 500)")
    void missingRequiredHeader_returns400() throws Exception {
        mockMvc.perform(get("/__test/needs-header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Missing required header: X-Trace-Id"));
    }

    @Test
    @DisplayName("에러 봉투는 정확히 {code, message, timestamp} — details 없는 arm 은 details 키를 내보내지 않는다")
    void errorEnvelopeKeySetIsUnchanged() throws Exception {
        mockMvc.perform(get("/__test/illegal-argument"))
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    /**
     * artist-service is the one fan service whose effective {@code ObjectMapper} is NOT
     * Spring Boot's: {@code config/RedisCacheConfig} contributes an {@code ObjectMapper}
     * {@code @Bean}, and Boot's {@code JacksonAutoConfiguration} bean is
     * {@code @ConditionalOnMissingBean}, so it backs off. That mapper leaves
     * {@code WRITE_DATES_AS_TIMESTAMPS} enabled — measured, an {@code Instant} renders as
     * {@code 1785370282.333000000}. Before TASK-FAN-BE-038 the error envelope held its
     * timestamp as an {@code Instant}, so <strong>artist-service was emitting a numeric
     * {@code timestamp}</strong>, contradicting {@code platform/error-handling.md}
     * ("timestamp: string (ISO 8601)"), {@code artist-api.md}'s own envelope example
     * ({@code "timestamp": "2026-05-03T00:00:00Z"}), and the frontend's
     * {@code ApiErrorBody.timestamp?: string} type.
     *
     * <p>The envelope now pre-formats the timestamp to a {@code String}, so it is correct
     * under either mapper. This test drives the <em>real</em> artist mapper — resolved
     * from {@code RedisCacheConfig} exactly as the running service resolves it — rather
     * than a hand-built one, so it stays honest if that config changes.
     *
     * <p>Note this fixes only the <em>error envelope</em>. The underlying mapper shadowing
     * still affects any other artist response DTO holding a raw {@code java.time} value
     * and the Redis directory-cache payload; the Kafka event contract is unaffected
     * because {@code ArtistEventPublisherAdapter} already pre-formats {@code occurredAt}
     * with {@code .toString()}. Repairing the mapper itself is out of this task's scope
     * (it would change the cache payload format) and is left as a separate finding.
     */
    @Test
    @DisplayName("artist 실 ObjectMapper(RedisCacheConfig 가 Boot 것을 밀어냄) 하에서도 timestamp 는 ISO-8601 문자열 — 숫자 아님")
    void envelopeTimestampIsIsoStringUnderArtistsOwnObjectMapper() {
        new ApplicationContextRunner()
                .withUserConfiguration(RedisCacheConfig.class)
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .run(context -> {
                    ObjectMapper effective = context.getBean(ObjectMapper.class);

                    // Guard the premise: this really is the shadowing mapper, i.e. one
                    // that would have rendered a raw Instant numerically.
                    assertThat(effective.writeValueAsString(Map.of("t", Instant.parse("2026-07-30T00:11:22.333Z"))))
                            .doesNotContain("2026-07-30T00:11:22.333Z");

                    JsonNode node = effective.readTree(effective.writeValueAsString(
                            ApiErrorBody.withDetails("STATE_TRANSITION_INVALID",
                                    "Invalid artist status transition",
                                    Map.of("from", "ARCHIVED", "to", "PUBLISHED"))));

                    assertThat(node.get("timestamp").isTextual()).isTrue();
                    assertThat(node.get("timestamp").asText()).endsWith("Z");

                    JsonNode shared = effective.readTree(effective.writeValueAsString(
                            ErrorResponse.of("ARTIST_NOT_FOUND", "Artist not found")));
                    assertThat(shared.get("timestamp").isTextual()).isTrue();
                });
    }

    record Body(@NotBlank String name) {
    }

    @RestController
    static class BoundaryController {

        @PostMapping(value = "/__test/valid-body", consumes = MediaType.APPLICATION_JSON_VALUE)
        String validBody(@Valid @RequestBody Body body) {
            return body.name();
        }

        @GetMapping("/__test/illegal-argument")
        String illegalArgument() {
            throw new IllegalArgumentException("boundary rejected the argument");
        }

        @GetMapping("/__test/illegal-state")
        String illegalState() {
            throw new IllegalStateException("aggregate invariant violated");
        }

        @GetMapping("/__test/by-id/{id}")
        String byId(@PathVariable UUID id) {
            return id.toString();
        }

        @GetMapping("/__test/needs-param")
        String needsParam(@RequestParam("accountId") String accountId) {
            return accountId;
        }

        @GetMapping("/__test/needs-header")
        String needsHeader(@RequestHeader("X-Trace-Id") String traceId) {
            return traceId;
        }

        @GetMapping("/__test/invalid-transition")
        String invalidTransition() {
            throw new Artist.IllegalStateTransitionException(ArtistStatus.ARCHIVED, ArtistStatus.PUBLISHED);
        }
    }
}
