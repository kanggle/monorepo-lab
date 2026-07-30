package com.example.fanplatform.membership.presentation.advice;

import com.example.fanplatform.membership.domain.membership.status.InvalidStateTransitionException;
import com.example.fanplatform.membership.domain.membership.status.MembershipStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-FAN-BE-038 (ADR-MONO-058 § D2) — pins the error-envelope contract that survived
 * the swap to {@code libs/java-web-servlet}'s {@code CommonGlobalExceptionHandler}.
 *
 * <p><strong>Preserved</strong> — 422 for {@code @Valid} constraint violations and for a
 * controller-boundary {@code IllegalArgumentException} ({@code membership-api.md}
 * publishes 422; the shared base defaults to 400); the {@code details {from,to}} payload
 * of {@code MEMBERSHIP_STATE_INVALID}; and the missing-{@code Idempotency-Key} guard,
 * which is now the base's {@code MissingRequestHeaderException} arm with a byte-identical
 * 400 / {@code "Missing required header: Idempotency-Key"} response.
 *
 * <p><strong>Changed, on purpose</strong> — a missing required request parameter (live
 * on {@code InternalAccessController}, whose three {@code @RequestParam}s have no
 * default) previously fell through the catch-all and answered <em>500 INTERNAL_ERROR</em>;
 * the shared base maps it to 400 VALIDATION_ERROR.
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

    @Test
    @DisplayName("MEMBERSHIP_STATE_INVALID → 422 + details{from,to} (membership-api.md 문서화 필드)")
    void membershipStateInvalid_carriesDetails() throws Exception {
        mockMvc.perform(get("/__test/invalid-transition"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MEMBERSHIP_STATE_INVALID"))
                .andExpect(jsonPath("$.details.from").value("CANCELED"))
                .andExpect(jsonPath("$.details.to").value("ACTIVE"));
    }

    @Test
    @DisplayName("Idempotency-Key 헤더 누락 → 400 VALIDATION_ERROR (공유 base 상속, 기존 로컬 arm 과 동일 메시지)")
    void missingIdempotencyKeyHeader_returns400() throws Exception {
        mockMvc.perform(post("/__test/needs-idempotency-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Missing required header: Idempotency-Key"));
    }

    @Test
    @DisplayName("필수 요청 파라미터 누락 → 400 VALIDATION_ERROR (이전에는 catch-all 500 — /internal/membership/access 에서 실제 도달 가능)")
    void missingRequiredParam_returns400() throws Exception {
        mockMvc.perform(get("/__test/needs-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Missing required parameter: accountId"));
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

        @PostMapping(value = "/__test/needs-idempotency-key", consumes = MediaType.APPLICATION_JSON_VALUE)
        String needsIdempotencyKey(@RequestHeader("Idempotency-Key") String key, @RequestBody Body body) {
            return key + body.name();
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

        @GetMapping("/__test/invalid-transition")
        String invalidTransition() {
            throw new InvalidStateTransitionException(MembershipStatus.CANCELED, MembershipStatus.ACTIVE);
        }
    }
}
