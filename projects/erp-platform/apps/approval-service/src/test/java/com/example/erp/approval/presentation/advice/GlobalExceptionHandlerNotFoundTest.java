package com.example.erp.approval.presentation.advice;

import com.example.erp.approval.domain.error.ApprovalErrors;
import jakarta.persistence.OptimisticLockException;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-420: an authenticated request to an unmapped path throws
 * {@link NoResourceFoundException} (Spring Boot 3.4.1) or {@link NoHandlerFoundException},
 * which the unscoped {@code Exception.class} catch-all previously swallowed into a 500.
 * These must resolve to 404, not 500.
 *
 * <p>TASK-ERP-BE-038 (ADR-MONO-058 § D2) rewrote this from direct handler-method calls
 * into a MockMvc {@code standaloneSetup(...).setControllerAdvice(...)} form. That matters
 * now that the arms are <em>inherited</em> from
 * {@code libs/java-web-servlet.CommonGlobalExceptionHandler}: a direct method call would
 * stay green even if {@code extends} registered nothing with Spring's
 * {@code ExceptionHandlerExceptionResolver}, and it would not detect an ambiguous-mapping
 * clash between an overridden base arm and a service-local one. Every assertion below
 * travels through the real resolver.
 */
class GlobalExceptionHandlerNotFoundTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ---------------- inherited (shared base) arms ----------------

    @Test
    @DisplayName("NoResourceFoundException(매핑 없는 경로)이 404 NOT_FOUND로 처리된다 (500 아님)")
    void handleNoResourceFound_returns404NotFound() throws Exception {
        mockMvc.perform(get("/probe/no-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("The requested resource was not found"))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    @DisplayName("NoHandlerFoundException(매핑 없는 경로)이 404 NOT_FOUND로 처리된다 (500 아님)")
    void handleNoHandlerFound_returns404NotFound() throws Exception {
        mockMvc.perform(get("/probe/no-handler"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("HttpRequestMethodNotSupportedException(지원하지 않는 HTTP 메서드)이 405 METHOD_NOT_ALLOWED로 처리된다 (500 아님)")
    void handleMethodNotSupported_returns405MethodNotAllowed() throws Exception {
        mockMvc.perform(get("/probe/json-only"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("POST")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("HttpMediaTypeNotSupportedException(지원하지 않는 Content-Type)이 415 UNSUPPORTED_MEDIA_TYPE으로 처리된다 (500 아님)")
    void handleMediaTypeNotSupported_returns415UnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/probe/json-only")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("nope"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("catch-all: 예상치 못한 예외는 500 INTERNAL_ERROR (원인 문자열 미노출)")
    void handleGeneral_returns500WithoutLeakingDetail() throws Exception {
        mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    /**
     * AC-3 — {@code validationFailureStatus()}'s unmodified default (400) already matches
     * approval-api.md. Pinned through the resolver so a future override in the shared base
     * cannot silently move this service off its published status.
     */
    @Test
    @DisplayName("AC-3: @Valid 위반은 상속 기본값 400 VALIDATION_ERROR (override 불필요)")
    void validationFailureIsBadRequest() throws Exception {
        mockMvc.perform(post("/probe/json-only")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("reason: must not be blank"));
    }

    @Test
    @DisplayName("AC-3: IllegalArgumentException 도 상속 기본값 400 VALIDATION_ERROR")
    void illegalArgumentIsBadRequest() throws Exception {
        mockMvc.perform(get("/probe/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("bad argument"));
    }

    @Test
    @DisplayName("malformed JSON 은 상속된 400 VALIDATION_ERROR")
    void malformedBodyIsBadRequest() throws Exception {
        mockMvc.perform(post("/probe/json-only")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    /**
     * Newly covered by the adoption — before TASK-ERP-BE-038 this service carried no
     * missing-parameter arm, so the catch-all answered 500.
     */
    @Test
    @DisplayName("누락 쿼리 파라미터는 상속된 400 VALIDATION_ERROR (이전 500)")
    void missingParamIsBadRequest() throws Exception {
        mockMvc.perform(get("/probe/needs-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Missing required parameter: stage"));
    }

    // ---------------- overridden base arms (erp contract wins) ----------------

    @Test
    @DisplayName("override: Idempotency-Key 누락은 400 IDEMPOTENCY_KEY_REQUIRED (base 의 VALIDATION_ERROR 아님)")
    void missingIdempotencyKeyKeepsErpCode() throws Exception {
        mockMvc.perform(post("/probe/json-only")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"수정 필요\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    /**
     * <strong>AC-4.</strong> The shared base's own arm answers this exception with code
     * {@code "CONFLICT"}; approval-api.md publishes {@code CONCURRENT_MODIFICATION}.
     * Driven through the resolver so it also proves the override actually wins over the
     * inherited arm (rather than colliding as an ambiguous mapping).
     */
    @Test
    @DisplayName("AC-4: ObjectOptimisticLockingFailureException → 409 CONCURRENT_MODIFICATION (base 의 CONFLICT 아님)")
    void springOptimisticLockKeepsErpCodeString() throws Exception {
        mockMvc.perform(get("/probe/spring-optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    @DisplayName("AC-4: jakarta OptimisticLockException → 409 CONCURRENT_MODIFICATION")
    void jpaOptimisticLockKeepsErpCodeString() throws Exception {
        mockMvc.perform(get("/probe/jpa-optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    // ---------------- erp-owned (service-local) arms ----------------

    @Test
    @DisplayName("erp-owned: IllegalStateException → 422 ILLEGAL_STATE")
    void illegalState() throws Exception {
        mockMvc.perform(get("/probe/illegal-state"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE"));
    }

    @Test
    @DisplayName("erp-owned: MethodArgumentTypeMismatchException → 400 VALIDATION_ERROR")
    void typeMismatch() throws Exception {
        mockMvc.perform(get("/probe/needs-param").param("stage", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Invalid parameter: stage"));
    }

    /**
     * AC-2 — the {@code details.cause} promise approval-api.md has been making since
     * TASK-ERP-BE-012, now actually on the wire and travelling through the real resolver.
     */
    @Test
    @DisplayName("AC-2: APPROVAL_ROUTE_INVALID 는 422 + details.cause 를 실어 나른다")
    void routeInvalidCarriesCauseOverTheWire() throws Exception {
        mockMvc.perform(get("/probe/self-approval"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("APPROVAL_ROUTE_INVALID"))
                .andExpect(jsonPath("$.details.cause").value("self_approval"))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    @DisplayName("AC-2: 제출자-전용 게이트는 403 + details.role=submitter")
    void submitterOnlyGateCarriesRoleOverTheWire() throws Exception {
        mockMvc.perform(get("/probe/not-submitter"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APPROVAL_NOT_AUTHORIZED_APPROVER"))
                .andExpect(jsonPath("$.details.role").value("submitter"));
    }

    @Test
    @DisplayName("AC-2: details 를 문서화하지 않은 도메인 코드는 details 필드를 생략한다")
    void otherDomainCodesOmitDetails() throws Exception {
        mockMvc.perform(get("/probe/domain-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPROVAL_REQUEST_NOT_FOUND"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    // ---------------- probe controller ----------------

    @RestController
    static class ProbeController {

        @GetMapping("/probe/no-resource")
        String noResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/api/definitely-not-a-real-endpoint");
        }

        @GetMapping("/probe/no-handler")
        String noHandler() throws NoHandlerFoundException {
            throw new NoHandlerFoundException("GET", "/api/definitely-not-a-real-endpoint",
                    new org.springframework.http.HttpHeaders());
        }

        @PostMapping(value = "/probe/json-only", consumes = MediaType.APPLICATION_JSON_VALUE)
        String jsonOnly(@RequestHeader("Idempotency-Key") String idempotencyKey,
                        @jakarta.validation.Valid @RequestBody Payload payload) {
            return idempotencyKey + payload.reason();
        }

        @GetMapping("/probe/boom")
        String boom() {
            throw new IllegalMonitorStateException("secret crash");
        }

        @GetMapping("/probe/illegal-argument")
        String illegalArgument() {
            throw new IllegalArgumentException("bad argument");
        }

        @GetMapping("/probe/illegal-state")
        String illegalState() {
            throw new IllegalStateException("route has no steps");
        }

        @GetMapping("/probe/needs-param")
        String needsParam(@RequestParam("stage") int stage) {
            return String.valueOf(stage);
        }

        @GetMapping("/probe/spring-optimistic-lock")
        String springOptimisticLock() {
            throw new ObjectOptimisticLockingFailureException("ApprovalRequest", "appr-1");
        }

        @GetMapping("/probe/jpa-optimistic-lock")
        String jpaOptimisticLock() {
            throw new OptimisticLockException("version stale");
        }

        @GetMapping("/probe/self-approval")
        String selfApproval() {
            throw ApprovalErrors.ApprovalRouteInvalidException.withCause(
                    ApprovalErrors.ApprovalRouteInvalidException.CAUSE_SELF_APPROVAL,
                    "self-approval is forbidden");
        }

        @GetMapping("/probe/not-submitter")
        String notSubmitter() {
            throw new ApprovalErrors.ApprovalNotAuthorizedApproverException(
                    "only the submitter may withdraw",
                    ApprovalErrors.ApprovalNotAuthorizedApproverException.ROLE_SUBMITTER);
        }

        @GetMapping("/probe/domain-not-found")
        String domainNotFound() {
            throw new ApprovalErrors.ApprovalRequestNotFoundException("appr-9");
        }
    }

    record Payload(@NotBlank String reason) {
    }
}
