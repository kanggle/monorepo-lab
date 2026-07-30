package com.example.fanplatform.notification.presentation.advice;

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
 * the swap to {@code libs/java-web-servlet}'s {@code CommonGlobalExceptionHandler}, and
 * the one behaviour that deliberately changed.
 *
 * <p><strong>Preserved</strong> — fan-platform publishes 422 for {@code @Valid}
 * constraint violations and for a controller-boundary {@code IllegalArgumentException},
 * where the shared base defaults to 400. That is carried by the single
 * {@code validationFailureStatus()} override in {@link AbstractDomainExceptionHandler};
 * if the override is dropped these go 400 and this test goes RED.
 *
 * <p><strong>Changed, on purpose</strong> — a missing required request parameter, a
 * missing required header and a malformed request body previously fell through this
 * service's catch-all {@code @ExceptionHandler(Exception.class)} and answered
 * <em>500 INTERNAL_ERROR</em>. The shared base maps all three to 400 VALIDATION_ERROR,
 * which is what {@code platform/error-handling.md} and the fan HTTP contracts already
 * describe ("400 VALIDATION_ERROR — malformed JSON / type mismatch"). Pinned here so the
 * improvement is a fixed behaviour rather than an unguarded side effect.
 *
 * <p>The envelope shape itself is asserted key-by-key: exactly
 * {@code {code, message, timestamp}} with no {@code details} — i.e. the shared
 * {@code ErrorResponse} is byte-compatible with the {@code details}-less
 * {@code ApiErrorBody} this service used to emit.
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
    @DisplayName("본문 파싱 실패 → 400 VALIDATION_ERROR (이전에는 catch-all 500)")
    void malformedBody_returns400() throws Exception {
        mockMvc.perform(post("/__test/valid-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    @DisplayName("에러 봉투는 정확히 {code, message, timestamp} — details 키 없음, timestamp 는 ISO-8601 문자열")
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
        String needsParam(@RequestParam("accountId") String accountId) {
            return accountId;
        }

        @GetMapping("/__test/needs-header")
        String needsHeader(@RequestHeader("X-Trace-Id") String traceId) {
            return traceId;
        }
    }
}
