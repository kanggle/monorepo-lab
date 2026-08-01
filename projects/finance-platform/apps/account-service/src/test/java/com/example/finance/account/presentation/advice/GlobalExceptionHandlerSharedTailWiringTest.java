package com.example.finance.account.presentation.advice;

import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Replaces the byte-identical {@code GlobalExceptionHandlerNotFoundTest} pair that
 * account-service and ledger-service each carried (TASK-FIN-BE-066 / ADR-MONO-058 § D2).
 * That pair asserted the generic tail by <em>calling the handler methods directly</em>,
 * which after adoption would only re-test {@code CommonGlobalExceptionHandler}'s own
 * logic — already covered by {@code CommonGlobalExceptionHandlerTest} in the library.
 *
 * <p>What is genuinely account-service's and therefore asserted here:
 * <ol>
 *   <li><strong>Reachability</strong> — the inherited arms are actually registered on
 *       <em>this</em> advice and resolved by Spring's real
 *       {@code ExceptionHandlerExceptionResolver}. A subclass that silently failed to
 *       register them still passes a direct method call; it fails here.</li>
 *   <li><strong>Precedence</strong> — the four arms this service {@code @Override}s
 *       (missing header, {@code IllegalArgumentException}, optimistic lock, and the
 *       domain integrity arm) still win over the base's generic versions.</li>
 *   <li><strong>Wire shape</strong> — the envelope is exactly
 *       {@code {code, message, timestamp}} with an ISO-8601 <em>string</em> timestamp and
 *       <strong>no</strong> {@code details} key, i.e. the retirement of the local
 *       {@code ApiErrorBody} changed nothing a client can observe.</li>
 * </ol>
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
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("POST")))
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
    // 2. arms this service overrides still win over the base
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Idempotency-Key 누락 → 400 IDEMPOTENCY_KEY_REQUIRED (base 의 VALIDATION_ERROR 아님)")
    void missingIdempotencyKey_keepsFinanceCode() throws Exception {
        mockMvc.perform(post("/__probe/json-only")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    /**
     * account-api.md registers {@code AMOUNT_INVALID | 422}. The base answers
     * {@code 400 VALIDATION_ERROR} for the same exception and its
     * {@code validationFailureStatus()} hook can only move the status, not the code —
     * so this service overrides the arm outright. This test is the guard that the
     * override, not the base, is what Spring selects.
     */
    @Test
    @DisplayName("IllegalArgumentException → 422 AMOUNT_INVALID (override 가 base 를 이긴다)")
    void illegalArgument_keeps422AmountInvalid() throws Exception {
        mockMvc.perform(get("/__probe/bad-amount"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AMOUNT_INVALID"));
    }

    @Test
    @DisplayName("낙관적 락 충돌 → 409 CONCURRENT_MODIFICATION (base 의 CONFLICT 별칭 아님)")
    void optimisticLock_keepsFinanceAlias() throws Exception {
        mockMvc.perform(get("/__probe/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    // ------------------------------------------------------------------
    // 3. arms newly reachable through the base (previously swallowed to 500)
    // ------------------------------------------------------------------

    /**
     * Before adoption this service had no {@code MissingServletRequestParameterException}
     * arm, so a missing required query parameter fell through the catch-all and answered
     * <strong>500 INTERNAL_ERROR</strong>. The base supplies it at 400
     * {@code VALIDATION_ERROR}, which is what account-api.md already documents. Pinned
     * here so the improvement is a fixed behaviour rather than an unguarded side effect.
     */
    @Test
    @DisplayName("필수 쿼리 파라미터 누락 → 400 VALIDATION_ERROR (기존 500 에서 개선)")
    void missingRequiredParam_nowResolvesTo400() throws Exception {
        mockMvc.perform(get("/__probe/needs-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Missing required parameter: accountId"));
    }

    @Test
    @DisplayName("@Valid 위반 → 400 VALIDATION_ERROR (account-api.md 의 문서화된 상태 유지)")
    void beanValidationFailure_stays400() throws Exception {
        mockMvc.perform(post("/__probe/json-only")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ------------------------------------------------------------------
    // 4. wire shape — ApiErrorBody retirement is invisible to a client
    // ------------------------------------------------------------------

    /**
     * The retired {@code ApiErrorBody} was
     * {@code {code, message, details?, timestamp}} with {@code details} suppressed by
     * {@code @JsonInclude(NON_NULL)} and never populated by any arm. The shared
     * {@link com.example.web.dto.ErrorResponse} is {@code {code, message, timestamp}}.
     * A client therefore sees the same three keys before and after — asserted, not assumed.
     */
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
                    new org.springframework.http.HttpHeaders());
        }

        @GetMapping("/__probe/boom")
        String boom() {
            throw new RuntimeException("internal detail that must not reach the client");
        }

        @GetMapping("/__probe/bad-amount")
        String badAmount() {
            throw new IllegalArgumentException("amount must be non-negative minor units: -1");
        }

        @GetMapping("/__probe/optimistic-lock")
        String optimisticLock() {
            throw new ObjectOptimisticLockingFailureException("Account", "acc-1");
        }

        @GetMapping("/__probe/needs-param")
        String needsParam(@RequestParam String accountId) {
            return accountId;
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
