package com.example.auth.infrastructure.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JsonAuthenticationEntryPoint 단위 테스트")
class JsonAuthenticationEntryPointTest {

    private JsonAuthenticationEntryPoint entryPoint;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        entryPoint = new JsonAuthenticationEntryPoint(objectMapper);
    }

    @Test
    @DisplayName("인증 실패 시 401 상태 코드를 반환한다")
    void commence_returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticationException exception = new AuthenticationException("Unauthorized") {};

        entryPoint.commence(request, response, exception);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("인증 실패 시 Content-Type이 application/json이다")
    void commence_returnsJsonContentType() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticationException exception = new AuthenticationException("Unauthorized") {};

        entryPoint.commence(request, response, exception);

        assertThat(response.getContentType()).contains(MediaType.APPLICATION_JSON_VALUE);
    }

    @Test
    @DisplayName("인증 실패 응답 바디에 code, message, timestamp 필드가 포함된다")
    void commence_returnsErrorResponseWithCodeMessageTimestamp() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticationException exception = new AuthenticationException("Unauthorized") {};

        entryPoint.commence(request, response, exception);

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.has("code")).isTrue();
        assertThat(body.has("message")).isTrue();
        assertThat(body.has("timestamp")).isTrue();
        assertThat(body.get("code").asText()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("인증 실패 응답의 timestamp는 ISO 8601 형식이다")
    void commence_timestamp_isIso8601() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticationException exception = new AuthenticationException("Unauthorized") {};

        entryPoint.commence(request, response, exception);

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        String timestamp = body.get("timestamp").asText();
        assertThat(Instant.parse(timestamp)).isNotNull();
    }
}
