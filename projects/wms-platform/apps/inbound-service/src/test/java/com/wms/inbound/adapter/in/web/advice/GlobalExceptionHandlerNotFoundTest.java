package com.wms.inbound.adapter.in.web.advice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * TASK-MONO-420 / TASK-MONO-421 behaviour (an unmapped path is 404, a wrong method is
 * 405 with an {@code Allow} header, a wrong {@code Content-Type} is 415 — none of them a
 * catch-all 500), re-pinned after TASK-BE-567 (ADR-MONO-058 § D2) moved those arms out of
 * this service and into {@code libs/java-web-servlet}'s {@code CommonGlobalExceptionHandler}.
 *
 * <p><strong>Why this drives MockMvc instead of calling the handler methods.</strong>
 * These arms are now <em>inherited</em>. A direct call such as
 * {@code new GlobalExceptionHandler().handleNoResourceFound(ex)} — the shape this file had
 * before D2 adoption — compiles and passes purely because the superclass method exists; it
 * would stay green even if Spring registered none of the inherited {@code @ExceptionHandler}
 * methods on this advice, and a boot-time <em>Ambiguous @ExceptionHandler</em> clash would
 * also slip past it. Routing through {@code ExceptionHandlerExceptionResolver} asks the
 * artifact instead of a proxy for it.
 */
@DisplayName("inbound GlobalExceptionHandler — 상속 arm 실배선 + flat 봉투 형태 (ADR-MONO-058 D2)")
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
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("NoHandlerFoundException → 404 NOT_FOUND (500 아님)")
    void noHandlerFound_returns404() throws Exception {
        mockMvc.perform(get("/__test/no-handler"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드 → 405 + RFC 7231 Allow 헤더 (500 아님)")
    void methodNotSupported_returns405WithAllowHeader() throws Exception {
        mockMvc.perform(delete("/__test/get-only"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, Matchers.containsString("GET")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type → 415 UNSUPPORTED_MEDIA_TYPE (500 아님)")
    void mediaTypeNotSupported_returns415() throws Exception {
        mockMvc.perform(post("/__test/json-only")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("미분류 예외 → 500 INTERNAL_ERROR (catch-all 도 상속된 arm 이다)")
    void unexpectedException_returns500() throws Exception {
        mockMvc.perform(get("/__test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    /**
     * Deliberate behaviour change recorded by TASK-BE-567: before D2 adoption this service
     * had no {@code HttpMessageNotReadableException} arm, so a malformed body fell through
     * to the catch-all and answered <strong>500</strong> — contradicting
     * {@code inbound-service-api.md}'s own "VALIDATION_ERROR | 400 | Bad input (type,
     * format, required field)" row. The inherited arm moves it onto the documented 400.
     */
    @Test
    @DisplayName("잘못된 JSON 본문 → 400 VALIDATION_ERROR (D2 이전에는 500 — 계약대로 교정)")
    void malformedBody_returns400() throws Exception {
        mockMvc.perform(post("/__test/json-only")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    /** Same deliberate 500 → 400 correction as {@link #malformedBody_returns400}. */
    @Test
    @DisplayName("필수 쿼리 파라미터 누락 → 400 VALIDATION_ERROR (D2 이전에는 500)")
    void missingRequestParameter_returns400() throws Exception {
        mockMvc.perform(get("/__test/needs-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    /**
     * AC-2 — the published wire shape, asserted as a whole rather than as a field subset.
     * inbound-service publishes the <strong>flat</strong> {@code {code, message, timestamp}}
     * body (component-for-component identical to {@code libs/java-web}'s {@code ErrorResponse}),
     * <em>not</em> the nested {@code {"error": {...}}} envelope that master-service /
     * admin-service publish. The explicit {@code $.error} absence check is what would catch an
     * adoption that silently changed the wrapping key.
     */
    @Test
    @DisplayName("에러 본문은 flat {code,message,timestamp} — 최상위 error 래퍼 키가 없어야 한다")
    void errorEnvelopeIsFlat_withIsoStringTimestamp() throws Exception {
        mockMvc.perform(get("/__test/no-resource"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.code").isString())
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.timestamp", Matchers.matchesRegex("^\\d{4}-\\d{2}-\\d{2}T.*Z$")));
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/__test/no-resource")
        String noResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/api/definitely-not-a-real-endpoint");
        }

        @GetMapping("/__test/no-handler")
        String noHandler() throws NoHandlerFoundException {
            throw new NoHandlerFoundException("GET", "/api/definitely-not-a-real-endpoint", new HttpHeaders());
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
