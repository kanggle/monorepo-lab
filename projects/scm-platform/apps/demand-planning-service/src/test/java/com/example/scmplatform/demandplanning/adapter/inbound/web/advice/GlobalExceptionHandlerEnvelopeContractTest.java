package com.example.scmplatform.demandplanning.adapter.inbound.web.advice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-SCM-BE-055 (ADR-MONO-058 § D2) — pins the error-envelope contract of this
 * service's advice after the generic (non-domain) arms moved to
 * {@code libs/java-web-servlet}'s {@code CommonGlobalExceptionHandler}.
 *
 * <p>It drives MockMvc rather than calling handler methods directly: the arms under test
 * are now <em>inherited</em>, and a direct call would stay green even if Spring registered
 * none of them on this advice (or failed at boot with an
 * <em>Ambiguous @ExceptionHandler</em> clash).
 *
 * <p><strong>Preserved</strong> — 422 for {@code @Valid} violations and for a
 * controller-boundary {@code IllegalArgumentException}, 400 for a path-variable type
 * mismatch, and 409 {@code CONCURRENT_MODIFICATION} for an optimistic-lock collision.
 *
 * <p><strong>Changed, on purpose</strong> — (a) a missing required request parameter, a
 * missing required header and a malformed body previously had no arm and fell through the
 * catch-all to <em>500 INTERNAL_ERROR</em>; (b) every error body now carries
 * {@code timestamp}, which this advice omitted while the service's own security-layer
 * writer already emitted it; (c) the {@code @Valid} message separator follows the shared
 * base ({@code "field: msg"}) instead of this service's former {@code "field msg"}.
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
                .andExpect(jsonPath("$.message").value("Missing required parameter: skuCode"));
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
    @DisplayName("본문 파싱 실패 → 400 VALIDATION_ERROR (공유 base 상속)")
    void malformedBody_returns400() throws Exception {
        mockMvc.perform(post("/__test/valid-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    /**
     * The shared base maps {@code ObjectOptimisticLockingFailureException} to 409
     * {@code CONFLICT}. This service publishes 409 {@code CONCURRENT_MODIFICATION} for the
     * same case, and its own local arm was registered for the <em>superclass</em>
     * {@code OptimisticLockingFailureException} — so the base's more-specific arm would
     * have shadowed it for the commonest concrete type and silently renamed a published
     * error code, with no compile error and no contract edit. The override plus this test
     * are what make that RED instead of invisible.
     */
    @Test
    @DisplayName("낙관적 락 충돌 → 409 CONCURRENT_MODIFICATION (공유 base 의 CONFLICT 로 조용히 바뀌지 않는다)")
    void optimisticLock_keepsConcurrentModificationCode() throws Exception {
        mockMvc.perform(get("/__test/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    /**
     * {@code platform/error-handling.md § Error Response Format} requires
     * {@code {code, message, timestamp}} on <em>every</em> error response. This service's advice previously emitted only {@code {code, message}} — the shared {@code ErrorResponse} adds the missing {@code timestamp}, matching what its own {@code HttpErrorResponseWriter} already emitted from the security layer.
     */
    @Test
    @DisplayName("에러 봉투는 정확히 {code, message, timestamp} — ISO-8601 문자열 timestamp 포함")
    void errorEnvelopeIsTheThreeFieldPlatformEnvelope() throws Exception {
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

        @GetMapping("/__test/optimistic-lock")
        String optimisticLock() {
            throw new ObjectOptimisticLockingFailureException("ReorderSuggestion", "id-001");
        }
    }
}
