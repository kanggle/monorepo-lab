package com.example.scmplatform.procurement.presentation.advice;

import com.example.scmplatform.procurement.domain.error.PoStatusTransitionInvalidException;
import com.example.scmplatform.procurement.domain.po.status.ActorType;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
import com.example.scmplatform.procurement.presentation.dto.ApiErrorBody;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
 * TASK-SCM-BE-055 (ADR-MONO-058 § D2) — pins the error-envelope contract that survived
 * the swap to {@code libs/java-web-servlet}'s {@code CommonGlobalExceptionHandler}, the
 * {@code details} extension that is deliberately NOT collapsed into it, the two overrides
 * that stop the shared base from silently renaming a published error code, and the one
 * behaviour that deliberately changed.
 *
 * <p><strong>Preserved</strong> — 422 for {@code @Valid} constraint violations and for a
 * controller-boundary {@code IllegalArgumentException} ({@code procurement-api.md}
 * publishes "{@code VALIDATION_ERROR} | 400/422"); the {@code details {from,to,actor}}
 * payload of {@code PO_STATUS_TRANSITION_INVALID}; the {@code IDEMPOTENCY_KEY_REQUIRED}
 * special case of the missing-header arm; {@code CONCURRENT_MODIFICATION} (not the shared
 * base's {@code CONFLICT}) for an optimistic-lock collision.
 *
 * <p><strong>Changed, on purpose</strong> — a missing required request parameter
 * previously fell through the catch-all {@code @ExceptionHandler(Exception.class)} and
 * answered <em>500 INTERNAL_ERROR</em>; the shared base maps it to 400 VALIDATION_ERROR,
 * which is what {@code procurement-api.md} already documents for the validation family.
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
    // Preserved: scm's 422 validation family (validationFailureStatus override)
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
    // Preserved: the overrides that stop the base renaming a published code
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Idempotency-Key 헤더 누락 → 400 IDEMPOTENCY_KEY_REQUIRED (공유 base 의 일반 VALIDATION_ERROR 로 흡수되지 않는다)")
    void missingIdempotencyKeyHeader_keepsDomainCode() throws Exception {
        mockMvc.perform(post("/__test/needs-idempotency-key"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    @DisplayName("그 외 필수 헤더 누락 → 400 VALIDATION_ERROR (공유 base 메시지 형태 그대로)")
    void missingOtherHeader_fallsBackToSharedBase() throws Exception {
        mockMvc.perform(get("/__test/needs-header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Missing required header: X-Trace-Id"));
    }

    /**
     * The shared base maps {@code ObjectOptimisticLockingFailureException} to 409
     * {@code CONFLICT}. procurement publishes 409 {@code CONCURRENT_MODIFICATION} for the
     * same case ({@code procurement-api.md} § Error codes) precisely because it means
     * something different from its own {@code CONFLICT} row (retry-OK vs. change-state-first).
     * Inheriting the base arm unchanged would have renamed a published code with no
     * compile error and no contract edit — this test is what makes that RED.
     */
    @Test
    @DisplayName("낙관적 락 충돌 → 409 CONCURRENT_MODIFICATION (공유 base 의 CONFLICT 로 조용히 바뀌지 않는다)")
    void optimisticLock_keepsConcurrentModificationCode() throws Exception {
        mockMvc.perform(get("/__test/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    // -----------------------------------------------------------------
    // Preserved: the one documented `details` arm (procurement-api.md)
    // -----------------------------------------------------------------

    @Test
    @DisplayName("PO_STATUS_TRANSITION_INVALID → 422 + details{from,to,actor} (procurement-api.md 문서화 필드)")
    void poStatusTransitionInvalid_carriesDetails() throws Exception {
        mockMvc.perform(get("/__test/invalid-transition"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PO_STATUS_TRANSITION_INVALID"))
                .andExpect(jsonPath("$.details.from").value("DRAFT"))
                .andExpect(jsonPath("$.details.to").value("RECEIVED"))
                .andExpect(jsonPath("$.details.actor").value("SYSTEM"))
                .andExpect(jsonPath("$.timestamp").isString());
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
                .andExpect(jsonPath("$.message").value("Missing required parameter: skuCode"));
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
    // The load-bearing wire-shape claim behind design decision 1
    // -----------------------------------------------------------------

    /**
     * The load-bearing evidence for TASK-SCM-BE-055's decision 1: moving an arm from the
     * service-local {@code ApiErrorBody} to the shared {@code ErrorResponse} is invisible
     * to a client, so it is not a contract change.
     *
     * <p><strong>Asserted against two different mappers on purpose.</strong> Before this
     * task {@code ApiErrorBody.timestamp} was an {@code Instant}, and it is tempting to
     * assume Jackson renders that as ISO-8601. It does not: {@code Instant} → ISO string
     * is neither a Jackson nor a {@code spring-web} default — only Spring Boot's
     * {@code JacksonAutoConfiguration} disables
     * {@code SerializationFeature.WRITE_DATES_AS_TIMESTAMPS}, and a service that
     * contributes its own {@code ObjectMapper} bean silently loses it (a sibling scm
     * service, {@code inventory-visibility-service}, does contribute one). The envelope
     * now pre-formats the timestamp to a {@code String} at construction; this test pins
     * that it is mapper-independent by running the comparison through a Boot
     * auto-configured mapper <em>and</em> a bare {@code new ObjectMapper()}.
     */
    @Test
    @DisplayName("details 없는 ApiErrorBody 와 공유 ErrorResponse 는 완전히 동일한 JSON — Boot ObjectMapper 와 맨 ObjectMapper 양쪽에서 (mapper 설정 비의존)")
    void detaillessApiErrorBodyAndErrorResponseSerialiseIdentically() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .run(context -> {
                    assertEnvelopesMatch(context.getBean(ObjectMapper.class));
                    assertEnvelopesMatch(new ObjectMapper());
                });
    }

    private void assertEnvelopesMatch(ObjectMapper mapper) throws Exception {
        String at = Instant.parse("2026-07-30T00:11:22.333Z").toString();
        JsonNode shared = mapper.readTree(mapper.writeValueAsString(
                new ErrorResponse("PO_NOT_FOUND", "PO not found: po-001", at)));
        JsonNode extension = mapper.readTree(mapper.writeValueAsString(
                new ApiErrorBody("PO_NOT_FOUND", "PO not found: po-001", null, at)));

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
                "PO_STATUS_TRANSITION_INVALID", "Invalid PO status transition",
                Map.of("from", "DRAFT", "to", "RECEIVED", "actor", "SYSTEM"))));

        List<String> keys = new ArrayList<>();
        node.fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactlyInAnyOrder("code", "message", "details", "timestamp");
        assertThat(node.get("details").get("from").asText()).isEqualTo("DRAFT");
        assertThat(node.get("timestamp").isTextual()).isTrue();
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
        String needsParam(@RequestParam("skuCode") String skuCode) {
            return skuCode;
        }

        @GetMapping("/__test/needs-header")
        String needsHeader(@RequestHeader("X-Trace-Id") String traceId) {
            return traceId;
        }

        @PostMapping("/__test/needs-idempotency-key")
        String needsIdempotencyKey(@RequestHeader("Idempotency-Key") String key) {
            return key;
        }

        @GetMapping("/__test/optimistic-lock")
        String optimisticLock() {
            throw new ObjectOptimisticLockingFailureException("PurchaseOrder", "po-001");
        }

        @GetMapping("/__test/invalid-transition")
        String invalidTransition() {
            throw new PoStatusTransitionInvalidException(
                    PoStatus.DRAFT, PoStatus.RECEIVED, ActorType.SYSTEM);
        }
    }
}
