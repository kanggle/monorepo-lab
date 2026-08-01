package com.wms.master.adapter.in.web.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.master.adapter.in.web.dto.response.ApiErrorEnvelope;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * TASK-MONO-162 / TASK-MONO-421 behaviour (an unmapped path is 404, a wrong method is 405
 * with an {@code Allow} header, a wrong {@code Content-Type} is 415 — none of them a
 * catch-all 500), re-pinned after TASK-BE-567 (ADR-MONO-058 § D2) replaced those arms'
 * hand-written mapping logic with a delegation to {@code libs/java-web-servlet}'s
 * {@code CommonGlobalExceptionHandler}.
 *
 * <p><strong>Why this drives MockMvc instead of calling the handler methods.</strong>
 * master-service <em>composes</em> the shared handler rather than extending it (its
 * published envelope nests under a top-level {@code error} key, which the shared
 * {@code ResponseEntity<ErrorResponse>} arms cannot produce). A direct method call would
 * still pass if the advice failed to register, or if a re-wrap dropped the {@code Allow}
 * header. Routing through {@code ExceptionHandlerExceptionResolver} asks the artifact.
 */
@DisplayName("master GlobalExceptionHandler — 위임 arm 실배선 + nested 봉투 형태 (ADR-MONO-058 D2)")
class GlobalExceptionHandlerNotFoundTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("NoResourceFoundException → 404 NOT_FOUND (500 아님)")
    void noResourceFound_returns404() throws Exception {
        mockMvc.perform(get("/__test/no-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드 → 405 + RFC 7231 Allow 헤더 (재래핑이 헤더를 잃지 않는다)")
    void methodNotSupported_returns405WithAllowHeader() throws Exception {
        mockMvc.perform(delete("/__test/get-only"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, Matchers.containsString("GET")))
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type → 415 UNSUPPORTED_MEDIA_TYPE (500 아님)")
    void mediaTypeNotSupported_returns415() throws Exception {
        mockMvc.perform(post("/__test/json-only")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("미분류 예외 → 500 INTERNAL_ERROR")
    void unexpectedException_returns500() throws Exception {
        mockMvc.perform(get("/__test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
    }

    @Test
    @DisplayName("잘못된 JSON 본문 → 400 VALIDATION_ERROR")
    void malformedBody_returns400() throws Exception {
        mockMvc.perform(post("/__test/json-only")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("필수 쿼리 파라미터 누락 → 400 VALIDATION_ERROR")
    void missingRequestParameter_returns400() throws Exception {
        mockMvc.perform(get("/__test/needs-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    /**
     * AC-2 — the published wire shape, asserted as a whole rather than as a field subset.
     * master-service publishes the <strong>nested</strong> {@code {"error": {...}}} envelope
     * documented in {@code master-service-api.md} § Error Envelope. The explicit
     * {@code $.code} absence check is what would catch an adoption that silently flattened
     * the wrapping key to the shared library's shape — the exact regression the D2 task's
     * Failure Scenarios call out.
     */
    @Test
    @DisplayName("에러 본문은 nested {\"error\":{...}} — 최상위에 flat code 가 있으면 안 된다")
    void errorEnvelopeIsNestedUnderErrorKey() throws Exception {
        mockMvc.perform(get("/__test/no-resource"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error.code").isString())
                .andExpect(jsonPath("$.error.message").isString())
                .andExpect(jsonPath("$.error.timestamp").exists())
                // `details` / `traceId` / `requestId` are @JsonInclude(NON_NULL) — absent, not null.
                .andExpect(jsonPath("$.error.details").doesNotExist());
    }

    /**
     * AC-2 (timestamp format) — {@code ApiError.timestamp} is a raw {@link java.time.Instant},
     * so whether it serialises as an ISO-8601 <em>string</em> (as
     * {@code error-envelope.schema.json} and {@code platform/error-handling.md} require) or as
     * a numeric epoch depends on the effective {@code ObjectMapper}. This asserts it under the
     * mapper Spring Boot actually auto-configures rather than a hand-tuned one. master-service
     * contributes no {@code ObjectMapper} {@code @Bean}, so Boot's is the effective mapper.
     */
    @Test
    @DisplayName("timestamp 는 Boot 가 구성한 ObjectMapper 아래에서 ISO-8601 문자열이다 (숫자 epoch 아님)")
    void timestampIsIsoStringUnderBootConfiguredMapper() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .run(context -> {
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);
                    JsonNode json = mapper.readTree(
                            mapper.writeValueAsString(ApiErrorEnvelope.of("NOT_FOUND", "Resource not found")));
                    assertThat(json.path("error").path("timestamp").isTextual()).isTrue();
                    assertThat(json.path("error").path("timestamp").asText())
                            .matches("^\\d{4}-\\d{2}-\\d{2}T.*Z$");
                });
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/__test/no-resource")
        String noResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/api/definitely-not-a-real-endpoint");
        }

        @GetMapping("/__test/get-only")
        String getOnly() {
            return "ok";
        }

        @GetMapping("/__test/boom")
        String boom() {
            throw new RuntimeException("unexpected");
        }

        @GetMapping("/__test/needs-param")
        String needsParam(@RequestParam("page") int page) {
            return String.valueOf(page);
        }

        @PostMapping(value = "/__test/json-only", consumes = MediaType.APPLICATION_JSON_VALUE)
        String jsonOnly(@RequestBody Payload body) {
            return body.name();
        }

        record Payload(String name) {}
    }
}
