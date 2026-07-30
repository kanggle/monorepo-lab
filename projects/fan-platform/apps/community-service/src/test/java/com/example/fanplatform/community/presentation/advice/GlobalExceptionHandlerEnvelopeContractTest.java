package com.example.fanplatform.community.presentation.advice;

import com.example.fanplatform.community.application.exception.MembershipRequiredException;
import com.example.fanplatform.community.domain.post.PostVisibility;
import com.example.fanplatform.community.domain.post.status.ActorType;
import com.example.fanplatform.community.domain.post.status.InvalidStateTransitionException;
import com.example.fanplatform.community.presentation.dto.ApiErrorBody;
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
import java.util.ArrayList;
import java.util.List;
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
 * the swap to {@code libs/java-web-servlet}'s {@code CommonGlobalExceptionHandler}, the
 * {@code details} extension that is deliberately NOT collapsed into it, and the one
 * behaviour that deliberately changed.
 *
 * <p><strong>Preserved</strong> — 422 for {@code @Valid} constraint violations and for a
 * controller-boundary {@code IllegalArgumentException} (shared base defaults to 400,
 * {@code community-api.md} publishes 422); the {@code details} payloads of
 * {@code MEMBERSHIP_REQUIRED} and {@code POST_STATUS_TRANSITION_INVALID}.
 *
 * <p><strong>Changed, on purpose</strong> — a missing required request parameter and a
 * missing required header previously fell through the catch-all
 * {@code @ExceptionHandler(Exception.class)} and answered <em>500 INTERNAL_ERROR</em>;
 * the shared base maps both to 400 VALIDATION_ERROR, which is what
 * {@code community-api.md} already documents for the validation family.
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

    // -----------------------------------------------------------------
    // Preserved: fan-platform's 422 validation family
    // -----------------------------------------------------------------

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
    @DisplayName("경로변수 타입 불일치 → 400 VALIDATION_ERROR (서비스 로컬 arm — 삭제 시 500 으로 회귀)")
    void typeMismatch_returns400() throws Exception {
        mockMvc.perform(get("/__test/by-id/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // -----------------------------------------------------------------
    // Preserved: the two documented `details` arms (community-api.md)
    // -----------------------------------------------------------------

    @Test
    @DisplayName("MEMBERSHIP_REQUIRED → 403 + details.requiredTier (community-api.md 문서화 필드)")
    void membershipRequired_carriesDetails() throws Exception {
        mockMvc.perform(get("/__test/membership-required"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBERSHIP_REQUIRED"))
                .andExpect(jsonPath("$.details.requiredTier").value("MEMBERS_ONLY"))
                // ISO-8601 string regardless of the harness's ObjectMapper — the envelope
                // pre-formats it rather than relying on WRITE_DATES_AS_TIMESTAMPS.
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    @DisplayName("POST_STATUS_TRANSITION_INVALID → 422 + details{from,to,actor}")
    void postStatusTransitionInvalid_carriesDetails() throws Exception {
        mockMvc.perform(get("/__test/invalid-transition"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("POST_STATUS_TRANSITION_INVALID"))
                .andExpect(jsonPath("$.details.from").value("PUBLISHED"))
                .andExpect(jsonPath("$.details.to").value("DRAFT"))
                .andExpect(jsonPath("$.details.actor").value("AUTHOR"));
    }

    // -----------------------------------------------------------------
    // Changed on purpose: 500 → 400
    // -----------------------------------------------------------------

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
    @DisplayName("본문 파싱 실패 → 400 VALIDATION_ERROR (공유 base 상속, 기존 로컬 arm 과 동일 메시지)")
    void malformedBody_returns400() throws Exception {
        mockMvc.perform(post("/__test/valid-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    // -----------------------------------------------------------------
    // AC-6: the load-bearing wire-shape claim behind decision 1
    // -----------------------------------------------------------------

    /**
     * The load-bearing evidence for TASK-FAN-BE-038's decision 1: moving an arm from the
     * service-local {@code ApiErrorBody} to the shared {@code ErrorResponse} is invisible
     * to a client, so it is not a contract change.
     *
     * <p><strong>It is asserted against two different mappers on purpose.</strong> The
     * first draft of this test held {@code ApiErrorBody.timestamp} as an {@code Instant}
     * and assumed Jackson would render it ISO-8601. It does not: {@code Instant} → ISO
     * string is neither a Jackson nor a {@code spring-web} default — only Spring Boot's
     * {@code JacksonAutoConfiguration} disables
     * {@code SerializationFeature.WRITE_DATES_AS_TIMESTAMPS}. A bare mapper writes
     * {@code "timestamp": 1.785370282333E9}, and a service that contributes its own
     * {@code ObjectMapper} bean silently gets that bare behaviour (artist-service does).
     * So the envelope now pre-formats the timestamp to a {@code String} at construction,
     * and this test pins that it is mapper-independent by running the comparison through
     * a Boot-auto-configured mapper <em>and</em> a bare {@code new ObjectMapper()}.
     */
    @Test
    @DisplayName("details 없는 ApiErrorBody 와 공유 ErrorResponse 는 완전히 동일한 JSON — Boot ObjectMapper 와 맨 ObjectMapper 양쪽에서 (mapper 설정 비의존)")
    void detailslessApiErrorBodyAndErrorResponseSerialiseIdentically() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .run(context -> {
                    assertEnvelopesMatch(context.getBean(ObjectMapper.class));
                    // A service that registers its own ObjectMapper bean makes Boot's
                    // auto-configured one back off; the envelope must not depend on which
                    // one wins.
                    assertEnvelopesMatch(new ObjectMapper());
                });
    }

    private void assertEnvelopesMatch(ObjectMapper mapper) throws Exception {
        String at = Instant.parse("2026-07-30T00:11:22.333Z").toString();
        JsonNode shared = mapper.readTree(mapper.writeValueAsString(
                new ErrorResponse("POST_NOT_FOUND", "Post not found", at)));
        JsonNode extension = mapper.readTree(mapper.writeValueAsString(
                new ApiErrorBody("POST_NOT_FOUND", "Post not found", null, at)));

        assertThat(extension).isEqualTo(shared);

        List<String> keys = new ArrayList<>();
        extension.fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactlyInAnyOrder("code", "message", "timestamp");
        assertThat(extension.get("timestamp").isTextual()).isTrue();
        assertThat(extension.get("timestamp").asText()).isEqualTo("2026-07-30T00:11:22.333Z");
        assertThat(extension.has("details")).isFalse();
    }

    @Test
    @DisplayName("details 있는 ApiErrorBody 는 네 번째 키를 더할 뿐 — platform/error-handling.md 가 허용하는 봉투 '확장'")
    void detailsCarryingBodyIsTheThreeFieldEnvelopePlusDetails() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        JsonNode node = mapper.readTree(mapper.writeValueAsString(ApiErrorBody.withDetails(
                "MEMBERSHIP_REQUIRED", "Membership tier required: MEMBERS_ONLY",
                Map.of("requiredTier", "MEMBERS_ONLY"))));

        List<String> keys = new ArrayList<>();
        node.fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactlyInAnyOrder("code", "message", "details", "timestamp");
        assertThat(node.get("details").get("requiredTier").asText()).isEqualTo("MEMBERS_ONLY");
        assertThat(node.get("timestamp").isTextual()).isTrue();
    }

    @Test
    @DisplayName("에러 봉투는 정확히 {code, message, timestamp} — details 없는 arm 은 details 키를 내보내지 않는다")
    void errorEnvelopeKeySetIsUnchanged() throws Exception {
        mockMvc.perform(get("/__test/illegal-argument"))
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").isString())   // ErrorResponse.timestamp is a String field
                .andExpect(jsonPath("$.details").doesNotExist());
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

        @GetMapping("/__test/membership-required")
        String membershipRequired() {
            throw new MembershipRequiredException(PostVisibility.MEMBERS_ONLY);
        }

        @GetMapping("/__test/invalid-transition")
        String invalidTransition() {
            throw new InvalidStateTransitionException(
                    com.example.fanplatform.community.domain.post.status.PostStatus.PUBLISHED,
                    com.example.fanplatform.community.domain.post.status.PostStatus.DRAFT,
                    ActorType.AUTHOR);
        }
    }
}
