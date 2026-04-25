package com.example.auth;

import com.example.auth.domain.entity.User;
import com.example.auth.domain.event.AuthEvent;
import com.example.auth.domain.event.AuthEventPublisher;
import com.example.auth.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 재발행 엔드포인트 통합 테스트.
 *
 * <p>TASK-BE-118 Acceptance Criteria: 100명 users 시드 후 엔드포인트 호출 → 100건 발행 확인.
 *
 * <p>Kafka 컨테이너 대신 {@link AuthEventPublisher}를 카운팅 스텁으로 교체한다
 * (SpringAuthEventPublisher는 Kafka가 아닌 ApplicationEventPublisher를 호출하므로
 *  브로커 없이도 통합 흐름은 완결된다. 발행 건수는 스텁이 집계).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
@Testcontainers
@Import(RepublishSignupEventsIntegrationTest.StubPublisherConfig.class)
@DisplayName("Signup 이벤트 재발행 엔드포인트 통합 테스트")
class RepublishSignupEventsIntegrationTest {

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

    @TestConfiguration
    static class StubPublisherConfig {
        @Bean
        @Primary
        CountingAuthEventPublisher countingAuthEventPublisher() {
            return new CountingAuthEventPublisher();
        }
    }

    static class CountingAuthEventPublisher implements AuthEventPublisher {
        final AtomicInteger count = new AtomicInteger();
        final List<AuthEvent> published = new ArrayList<>();
        volatile int failEveryNth = 0; // 0 = never fail

        @Override
        public synchronized void publish(AuthEvent event) {
            int n = count.incrementAndGet();
            if (failEveryNth > 0 && n % failEveryNth == 0) {
                throw new RuntimeException("stub broker failure for nth=" + n);
            }
            published.add(event);
        }

        void reset() {
            count.set(0);
            published.clear();
            failEveryNth = 0;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CountingAuthEventPublisher publisher;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // AdminAccountSeeder(ApplicationRunner)가 컨텍스트 기동 시 admin 유저를 삽입하므로
        // 각 테스트가 깨끗한 상태에서 시작하도록 users 테이블을 먼저 비운다.
        publisher.reset();
        jdbcTemplate.update("DELETE FROM users");
    }

    @AfterEach
    void cleanup() {
        publisher.reset();
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    @DisplayName("100명 users 시드 후 재발행 호출 시 totalUsers=100, publishedCount=100, failedCount=0")
    void republish_hundredUsers_publishesAll() throws Exception {
        for (int i = 0; i < 100; i++) {
            User u = User.create(
                "republish-" + i + "-" + UUID.randomUUID() + "@example.com",
                "encodedPw", "User" + i);
            userRepository.save(u);
        }

        mockMvc.perform(post("/api/internal/users/republish-signup-events"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalUsers").value(100))
            .andExpect(jsonPath("$.publishedCount").value(100))
            .andExpect(jsonPath("$.failedCount").value(0));

        assertThat(publisher.count.get()).isEqualTo(100);
        assertThat(publisher.published).hasSize(100);
    }

    @Test
    @DisplayName("유저 0명 시나리오 — totalUsers=0, publishedCount=0, failedCount=0")
    void republish_zeroUsers() throws Exception {
        mockMvc.perform(post("/api/internal/users/republish-signup-events"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalUsers").value(0))
            .andExpect(jsonPath("$.publishedCount").value(0))
            .andExpect(jsonPath("$.failedCount").value(0));

        assertThat(publisher.count.get()).isZero();
    }

    @Test
    @DisplayName("publish 실패 시 failedCount에 반영된다")
    void republish_partialFailure_reflectedInFailedCount() throws Exception {
        for (int i = 0; i < 10; i++) {
            User u = User.create(
                "fail-" + i + "-" + UUID.randomUUID() + "@example.com",
                "encodedPw", "User" + i);
            userRepository.save(u);
        }
        publisher.failEveryNth = 3; // 3, 6, 9 → 3건 실패

        mockMvc.perform(post("/api/internal/users/republish-signup-events"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalUsers").value(10))
            .andExpect(jsonPath("$.publishedCount").value(7))
            .andExpect(jsonPath("$.failedCount").value(3));
    }
}
