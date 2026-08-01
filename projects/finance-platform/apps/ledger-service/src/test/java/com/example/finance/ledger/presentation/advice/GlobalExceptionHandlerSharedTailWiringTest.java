package com.example.finance.ledger.presentation.advice;

import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ledger-service half of the {@code GlobalExceptionHandlerNotFoundTest} replacement
 * (TASK-FIN-BE-066 / ADR-MONO-058 § D2). The removed pair was byte-identical across the
 * two services and, after adoption, would have been a test of
 * {@code CommonGlobalExceptionHandler}'s logic reached through a subclass reference —
 * something the library's own {@code CommonGlobalExceptionHandlerTest} already covers.
 *
 * <p>This class asserts only what is ledger's: that the inherited arms are really
 * registered on <em>this</em> advice, that ledger's own overrides win where its contract
 * differs, and that the arms ledger previously lacked (and therefore answered 500) now
 * answer what ledger-api.md documents. Its expectations differ from account-service's
 * counterpart at exactly the points the two contracts differ — the
 * {@code IllegalArgumentException} status/code and the optimistic-lock code.
 */
@DisplayName("GlobalExceptionHandler 공유 tail 배선 테스트 (ADR-MONO-058 D2)")
class GlobalExceptionHandlerSharedTailWiringTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ------------------------------------------------------------------
    // 1. the generic tail D2 removed from this service, proven still reached
    // ------------------------------------------------------------------

    @Test
    @DisplayName("NoResourceFoundException → 404 NOT_FOUND (상속된 arm 이 실제로 등록·도달)")
    void noResourceFound_resolvesTo404() throws Exception {
        mockMvc.perform(get("/__probe/no-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("The requested resource was not found"));
    }

    @Test
    @DisplayName("NoHandlerFoundException → 404 NOT_FOUND")
    void noHandlerFound_resolvesTo404() throws Exception {
        mockMvc.perform(get("/__probe/no-handler"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드 → 405 METHOD_NOT_ALLOWED + RFC 7231 Allow 헤더")
    void methodNotSupported_resolvesTo405WithAllowHeader() throws Exception {
        mockMvc.perform(get("/__probe/json-only"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("POST")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type → 415 UNSUPPORTED_MEDIA_TYPE")
    void mediaTypeNotSupported_resolvesTo415() throws Exception {
        mockMvc.perform(post("/__probe/json-only")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("분류되지 않은 예외 → 500 INTERNAL_ERROR, 내부 메시지 미노출")
    void unclassifiedException_resolvesTo500WithoutLeak() throws Exception {
        mockMvc.perform(get("/__probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    // ------------------------------------------------------------------
    // 2. ledger's own override, and the deliberate asymmetry with account-service
    // ------------------------------------------------------------------

    @Test
    @DisplayName("필수 헤더 누락 → 400 IDEMPOTENCY_KEY_REQUIRED (base 의 VALIDATION_ERROR 아님)")
    void missingIdempotencyKey_keepsLedgerCode() throws Exception {
        mockMvc.perform(post("/__probe/json-only")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    /**
     * The deliberate asymmetry TASK-MONO-348 established and TASK-FIN-BE-066 preserved:
     * account-service answers {@code 422 AMOUNT_INVALID} here (it overrides the arm),
     * ledger keeps the base's {@code 400 VALIDATION_ERROR} because ledger-api.md registers
     * {@code VALIDATION_ERROR} as "always 400 in this service" and has no
     * {@code AMOUNT_INVALID} code at all. Inheriting the base arm — rather than overriding
     * {@code validationFailureStatus()} — is what reproduces that exactly.
     */
    @Test
    @DisplayName("IllegalArgumentException → 400 VALIDATION_ERROR (account 의 422 와 의도적 비대칭)")
    void illegalArgument_stays400ValidationError() throws Exception {
        mockMvc.perform(get("/__probe/bad-rate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("settlementRate")));
    }

    @Test
    @DisplayName("IllegalStateException → 422 ILLEGAL_STATE (ledger 고유 arm 이 유지된다)")
    void illegalState_stays422() throws Exception {
        mockMvc.perform(get("/__probe/illegal-state"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE"));
    }

    // ------------------------------------------------------------------
    // 3. arms newly reachable through the base (previously swallowed to 500)
    // ------------------------------------------------------------------

    /**
     * ledger-service shipped without a {@code MethodArgumentNotValidException} arm, so a
     * bean-validation failure fell through the catch-all and answered
     * <strong>500 INTERNAL_ERROR</strong> — contradicting ledger-api.md, which registers
     * {@code VALIDATION_ERROR | 400}. The inherited arm fixes that; pinned here so the fix
     * is guarded rather than incidental.
     */
    @Test
    @DisplayName("@Valid 위반 → 400 VALIDATION_ERROR (기존 500 에서 개선)")
    void beanValidationFailure_nowResolvesTo400() throws Exception {
        mockMvc.perform(post("/__probe/json-only")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("필수 쿼리 파라미터 누락 → 400 VALIDATION_ERROR (기존 500 에서 개선)")
    void missingRequiredParam_nowResolvesTo400() throws Exception {
        mockMvc.perform(get("/__probe/needs-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Missing required parameter: periodId"));
    }

    /**
     * Also previously a 500. The inherited arm answers 409 {@code CONFLICT} — the
     * fleet-registered optimistic-lock code ({@code platform/error-handling.md}
     * § Transactional, T5), newly documented in ledger-api.md § Error codes by this task.
     * account-service overrides this arm to its own registered alias
     * {@code CONCURRENT_MODIFICATION}; ledger has no such alias and takes the base code.
     */
    @Test
    @DisplayName("낙관적 락 충돌 → 409 CONFLICT (기존 500 에서 개선, ledger-api.md 에 신규 문서화)")
    void optimisticLock_nowResolvesTo409Conflict() throws Exception {
        mockMvc.perform(get("/__probe/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    // ------------------------------------------------------------------
    // 4. wire shape — ApiErrorBody retirement is invisible to a client
    // ------------------------------------------------------------------

    @Test
    @DisplayName("에러 봉투는 정확히 {code, message, timestamp} — details 키는 존재하지 않는다")
    void envelopeKeySetIsUnchanged() throws Exception {
        mockMvc.perform(get("/__probe/no-resource"))
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @RestController
    static class ProbeController {

        @GetMapping("/__probe/no-resource")
        String noResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/api/definitely-not-a-real-endpoint");
        }

        @GetMapping("/__probe/no-handler")
        String noHandler() throws NoHandlerFoundException {
            throw new NoHandlerFoundException("GET", "/api/definitely-not-a-real-endpoint",
                    new HttpHeaders());
        }

        @GetMapping("/__probe/boom")
        String boom() {
            throw new RuntimeException("internal detail that must not reach the client");
        }

        @GetMapping("/__probe/bad-rate")
        String badRate() {
            throw new IllegalArgumentException("settlementRate must be a decimal string: abc");
        }

        @GetMapping("/__probe/illegal-state")
        String illegalState() {
            throw new IllegalStateException("journal entry has no lines");
        }

        @GetMapping("/__probe/optimistic-lock")
        String optimisticLock() {
            throw new ObjectOptimisticLockingFailureException("AccountingPeriod", "per-1");
        }

        @GetMapping("/__probe/needs-param")
        String needsParam(@RequestParam String periodId) {
            return periodId;
        }

        /** POST-only + JSON-only, so a GET yields 405 and a wrong Content-Type yields 415. */
        @PostMapping(value = "/__probe/json-only", consumes = MediaType.APPLICATION_JSON_VALUE)
        String jsonOnly(@RequestHeader("Idempotency-Key") String key,
                        @jakarta.validation.Valid @RequestBody ProbeBody body) {
            return key + body.name();
        }
    }

    record ProbeBody(@NotBlank String name) {
    }
}
