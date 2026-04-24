package com.example.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("세션 한도 제한 통합 테스트")
class AuthSessionLimitIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("auth_db")
        .withUsername("auth_user")
        .withPassword("auth_pass");

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("jwt.secret", () -> "integration-test-secret-key-min-32chars!!");
        registry.add("auth.session.max-concurrent", () -> "3");
        registry.add("auth.session.inactivity-timeout", () -> "604800");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private void signup(String email) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "email", email, "password", "password1!", "name", "테스터"
            )))).andExpect(status().isCreated());
    }

    private JsonNode login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", email, "password", "password1!"
                ))))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("최대 세션 수(3) 초과 시 로그인은 성공하고, 가장 오래된 세션의 refreshToken은 무효화된다")
    void sessionLimit_evictsOldestSession() throws Exception {
        String email = "session-limit@example.com";
        signup(email);

        List<String> refreshTokens = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            refreshTokens.add(login(email).get("refreshToken").asText());
        }

        // 4번째 로그인 — max-concurrent=3 초과, 가장 오래된 세션(refreshTokens[0]) 제거
        login(email);

        // 가장 오래된 refreshToken으로 갱신 시도 → 무효화되어야 함
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshTokens.get(0)))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // 나머지 세션들은 여전히 유효
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshTokens.get(1)))))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그아웃 후 세션 레지스트리에서 제거되어 세션 슬롯이 확보된다")
    void logout_removesSessionFromRegistry() throws Exception {
        String email = "session-logout@example.com";
        signup(email);

        // 최대(3) 세션 생성
        List<JsonNode> sessions = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            sessions.add(login(email));
        }

        // 첫 번째 세션 로그아웃
        String firstRefreshToken = sessions.get(0).get("refreshToken").asText();
        String firstAccessToken = sessions.get(0).get("accessToken").asText();

        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer " + firstAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", firstRefreshToken))))
            .andExpect(status().isNoContent());

        // 로그아웃 후 4번째 로그인 — 슬롯이 확보되었으므로 다른 세션이 제거되지 않아야 함
        JsonNode newSession = login(email);
        String newRefreshToken = newSession.get("refreshToken").asText();

        // 2, 3번째 세션은 여전히 유효
        MvcResult result2 = mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", sessions.get(1).get("refreshToken").asText()))))
            .andExpect(status().isOk())
            .andReturn();

        // 새 세션도 유효
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", newRefreshToken))))
            .andExpect(status().isOk());

        // 확인: 2번 세션 갱신 후 새 refreshToken이 발급됨 (rotation)
        JsonNode refreshed = objectMapper.readTree(result2.getResponse().getContentAsString());
        assertThat(refreshed.get("refreshToken").asText())
            .isNotEqualTo(sessions.get(1).get("refreshToken").asText());
    }
}
