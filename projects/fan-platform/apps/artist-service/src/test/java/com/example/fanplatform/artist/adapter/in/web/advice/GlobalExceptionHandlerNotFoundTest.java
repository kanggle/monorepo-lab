package com.example.fanplatform.artist.adapter.in.web.advice;

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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-420 / TASK-MONO-421 behaviour (an unmapped path is 404 and a wrong
 * method is 405, neither a catch-all 500), re-pinned after TASK-FAN-BE-038
 * (ADR-MONO-058 § D2) moved those arms out of this service and into
 * {@code libs/java-web-servlet}'s {@code CommonGlobalExceptionHandler}.
 *
 * <p><strong>Why this drives MockMvc instead of calling the handler methods.</strong>
 * These arms are now <em>inherited</em>. A direct call such as
 * {@code new GlobalExceptionHandler().handleNoResourceFound(ex)} — the shape this file
 * had before D2 adoption — compiles and passes purely because the superclass method
 * exists; it would stay green even if Spring registered none of the inherited
 * {@code @ExceptionHandler} methods on this advice, which is the exact failure mode the
 * adoption could introduce (a boot-time <em>Ambiguous @ExceptionHandler</em> clash would
 * also slip past it — and artist-service is the one fan service that <em>overrides</em>
 * an inherited arm, so it is the most exposed to that clash). Routing the request
 * through {@code ExceptionHandlerExceptionResolver} asks the artifact instead of a proxy
 * for it: an unregistered inherited arm resolves to the catch-all 500 and this test goes
 * RED.
 */
@DisplayName("GlobalExceptionHandler 상속 arm 실배선 테스트 (404/405/415 — ADR-MONO-058 D2)")
class GlobalExceptionHandlerNotFoundTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("NoResourceFoundException(매핑 없는 경로)이 404 NOT_FOUND로 처리된다 (500 아님)")
    void noResourceFound_returns404NotFound() throws Exception {
        mockMvc.perform(get("/__test/no-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("The requested resource was not found"));
    }

    @Test
    @DisplayName("NoHandlerFoundException(매핑 없는 경로)이 404 NOT_FOUND로 처리된다 (500 아님)")
    void noHandlerFound_returns404NotFound() throws Exception {
        mockMvc.perform(get("/__test/no-handler"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("The requested resource was not found"));
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드가 405 METHOD_NOT_ALLOWED + RFC 7231 Allow 헤더로 처리된다 (500 아님)")
    void methodNotSupported_returns405WithAllowHeader() throws Exception {
        mockMvc.perform(delete("/__test/get-only"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, org.hamcrest.Matchers.containsString("GET")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type이 415 UNSUPPORTED_MEDIA_TYPE로 처리된다 (500 아님)")
    void mediaTypeNotSupported_returns415() throws Exception {
        mockMvc.perform(post("/__test/json-only")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
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

        @PostMapping(value = "/__test/json-only", consumes = MediaType.APPLICATION_JSON_VALUE)
        String jsonOnly(@RequestBody String body) {
            return body;
        }
    }
}
