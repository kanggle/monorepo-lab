package com.example.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
@Testcontainers
@DisplayName("감사 로그 통합 테스트")
class AuthAuditLogIntegrationTest {

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
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("회원가입 후 auth_audit_log에 SIGNUP 레코드가 기록된다")
    void signup_recordsAuditLog() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", "audit-signup@example.com",
                    "password", "password1!",
                    "name", "감사테스트"
                ))))
            .andExpect(status().isCreated());

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth_audit_log WHERE email = ? AND event_type = 'SIGNUP' AND result = 'SUCCESS'",
            Integer.class, "audit-signup@example.com");
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("로그인 성공 후 auth_audit_log에 LOGIN_SUCCESS 레코드가 기록된다")
    void login_success_recordsAuditLog() throws Exception {
        String email = "audit-login@example.com";
        signupUser(email, "password1!", "로그인감사");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", email,
                    "password", "password1!"
                ))))
            .andExpect(status().isOk());

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth_audit_log WHERE email = ? AND event_type = 'LOGIN_SUCCESS' AND result = 'SUCCESS'",
            Integer.class, email);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("로그인 실패 시 auth_audit_log에 LOGIN_FAILURE 레코드가 기록된다")
    void login_failure_recordsAuditLog() throws Exception {
        String email = "audit-fail@example.com";
        signupUser(email, "password1!", "실패감사");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", email,
                    "password", "wrongpassword"
                ))))
            .andExpect(status().isUnauthorized());

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth_audit_log WHERE email = ? AND event_type = 'LOGIN_FAILURE' AND result = 'FAILURE'",
            Integer.class, email);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("토큰 갱신 후 auth_audit_log에 TOKEN_REFRESH 레코드가 기록된다")
    void refresh_recordsAuditLog() throws Exception {
        String email = "audit-refresh@example.com";
        signupUser(email, "password1!", "갱신감사");

        String refreshToken = loginAndGetRefreshToken(email, "password1!");

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
            .andExpect(status().isOk());

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth_audit_log WHERE email = ? AND event_type = 'TOKEN_REFRESH' AND result = 'SUCCESS'",
            Integer.class, email);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("로그아웃 후 auth_audit_log에 LOGOUT 레코드가 기록된다")
    void logout_recordsAuditLog() throws Exception {
        String email = "audit-logout@example.com";
        signupUser(email, "password1!", "로그아웃감사");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "password1!"))))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = loginJson.get("accessToken").asText();
        String refreshToken = loginJson.get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
            .andExpect(status().isNoContent());

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth_audit_log WHERE email = ? AND event_type = 'LOGOUT' AND result = 'SUCCESS'",
            Integer.class, email);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("X-Forwarded-For 헤더에 복수 IP가 있으면 첫 번째 IP가 ip_address에 저장된다")
    void login_xForwardedFor_firstIpStored() throws Exception {
        String email = "audit-ip@example.com";
        signupUser(email, "password1!", "IP감사");

        mockMvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", "1.1.1.1, 2.2.2.2, 3.3.3.3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", email,
                    "password", "password1!"
                ))))
            .andExpect(status().isOk());

        String ipAddress = jdbcTemplate.queryForObject(
            "SELECT ip_address FROM auth_audit_log WHERE email = ? AND event_type = 'LOGIN_SUCCESS'",
            String.class, email);
        assertThat(ipAddress).isEqualTo("1.1.1.1");
    }

    // helper methods

    private void signupUser(String email, String password, String name) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password, "name", name))))
            .andExpect(status().isCreated());
    }

    private String loginAndGetRefreshToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("refreshToken").asText();
    }
}
