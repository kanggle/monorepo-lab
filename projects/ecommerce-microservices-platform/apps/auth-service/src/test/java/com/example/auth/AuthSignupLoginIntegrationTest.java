package com.example.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
@Testcontainers
@DisplayName("회원가입 + 로그인 통합 테스트")
class AuthSignupLoginIntegrationTest {

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
    private DataSource dataSource;

    @Test
    @DisplayName("회원가입 후 로그인하면 토큰을 발급받는다")
    void signup_then_login_success() throws Exception {
        // 회원가입
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", "user@example.com",
                    "password", "password1!",
                    "name", "홍길동"
                ))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("user@example.com"));

        // 로그인
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", "user@example.com",
                    "password", "password1!"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty())
            .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    @DisplayName("동일 이메일로 중복 가입 시 409를 반환한다")
    void signup_duplicate_returns409() throws Exception {
        Map<String, String> body = Map.of("email", "dup@example.com", "password", "password1!", "name", "중복유저");

        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body))).andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("회원가입 응답의 createdAt이 ISO 8601 UTC 형식(Z 접미사)이다")
    void signup_createdAt_iso8601utc() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", "utc@example.com",
                    "password", "password1!",
                    "name", "UTC테스트"
                ))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.createdAt").value(org.hamcrest.Matchers.endsWith("Z")));
    }

    @Test
    @DisplayName("Flyway V2 후 idx_users_email 인덱스가 없고 uq_users_email 제약조건은 유지된다")
    void flyway_v2_duplicateIndex_removed() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            List<String> indexNames = new ArrayList<>();
            try (ResultSet rs = meta.getIndexInfo(null, null, "users", false, false)) {
                while (rs.next()) {
                    indexNames.add(rs.getString("INDEX_NAME"));
                }
            }
            assertThat(indexNames).doesNotContain("idx_users_email");
            assertThat(indexNames).contains("uq_users_email");
        }
    }

    @Test
    @DisplayName("틀린 비밀번호로 로그인 시 401을 반환한다")
    void login_wrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "email", "wrong@example.com", "password", "password1!", "name", "테스트"
            )))).andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", "wrong@example.com", "password", "wrongpassword"
                ))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }
}
