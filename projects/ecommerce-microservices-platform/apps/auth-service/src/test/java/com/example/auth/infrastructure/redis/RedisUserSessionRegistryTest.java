package com.example.auth.infrastructure.redis;

import com.example.auth.domain.repository.UserSessionRegistry;
import com.example.auth.infrastructure.util.TokenKeyHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@DisplayName("RedisUserSessionRegistry 통합 테스트")
class RedisUserSessionRegistryTest {

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
        registry.add("app.redis.key-namespace", () -> "test");
        registry.add("auth.session.max-concurrent", () -> "3");
        registry.add("auth.session.inactivity-timeout", () -> "604800");
    }

    @Autowired
    private UserSessionRegistry sessionRegistry;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        // 이전 테스트 잔류 데이터 정리
        redisTemplate.delete("test:sessions:" + userId);
    }

    @Test
    @DisplayName("registerSession은 세션을 Sorted Set에 추가하고 evictedSessionId가 null인 결과를 반환한다")
    void registerSession_addsSessionAndReturnsNoEviction() {
        String token = UUID.randomUUID().toString();

        UserSessionRegistry.RegistrationResult result = sessionRegistry.registerSession(userId, token, 604800L);

        assertThat(result.evictedSessionId()).isNull();
        assertThat(result.newSessionId()).isEqualTo(TokenKeyHasher.sha256Hex(token));

        Long count = redisTemplate.opsForZSet().size("test:sessions:" + userId);
        assertThat(count).isEqualTo(1L);

        Double score = redisTemplate.opsForZSet().score("test:sessions:" + userId, result.newSessionId());
        assertThat(score).isNotNull();
    }

    @Test
    @DisplayName("최대 세션 수(3) 초과 시 가장 오래된 세션이 제거되고 evictedHash를 반환한다")
    void registerSession_evictsOldestWhenLimitExceeded() {
        String token1 = UUID.randomUUID().toString();
        String token2 = UUID.randomUUID().toString();
        String token3 = UUID.randomUUID().toString();
        String token4 = UUID.randomUUID().toString();

        sessionRegistry.registerSession(userId, token1, 604800L);
        sessionRegistry.registerSession(userId, token2, 604800L);
        sessionRegistry.registerSession(userId, token3, 604800L);

        // max-concurrent=3이므로 4번째 등록 시 가장 오래된 token1이 제거되어야 함
        UserSessionRegistry.RegistrationResult result = sessionRegistry.registerSession(userId, token4, 604800L);

        assertThat(result.evictedSessionId()).isEqualTo(TokenKeyHasher.sha256Hex(token1));
        assertThat(result.newSessionId()).isEqualTo(TokenKeyHasher.sha256Hex(token4));

        Long count = redisTemplate.opsForZSet().size("test:sessions:" + userId);
        assertThat(count).isEqualTo(3L);

        // token1 hash는 Sorted Set에서 제거됨
        Double removedScore = redisTemplate.opsForZSet().score(
            "test:sessions:" + userId, TokenKeyHasher.sha256Hex(token1));
        assertThat(removedScore).isNull();
    }

    @Test
    @DisplayName("rotateSession은 old hash를 제거하고 new hash를 추가한다")
    void rotateSession_replacesOldWithNew() {
        String oldToken = UUID.randomUUID().toString();
        String newToken = UUID.randomUUID().toString();
        String oldHash = TokenKeyHasher.sha256Hex(oldToken);
        String newHash = TokenKeyHasher.sha256Hex(newToken);

        sessionRegistry.registerSession(userId, oldToken, 604800L);
        sessionRegistry.rotateSession(userId, oldToken, newToken, 604800L);

        Double oldScore = redisTemplate.opsForZSet().score("test:sessions:" + userId, oldHash);
        Double newScore = redisTemplate.opsForZSet().score("test:sessions:" + userId, newHash);

        assertThat(oldScore).isNull();
        assertThat(newScore).isNotNull();
    }

    @Test
    @DisplayName("removeSession은 해당 세션 hash를 Sorted Set에서 제거한다")
    void removeSession_removesFromSortedSet() {
        String token = UUID.randomUUID().toString();
        String hash = TokenKeyHasher.sha256Hex(token);

        sessionRegistry.registerSession(userId, token, 604800L);
        sessionRegistry.removeSession(userId, token);

        Double score = redisTemplate.opsForZSet().score("test:sessions:" + userId, hash);
        assertThat(score).isNull();
    }

    @Test
    @DisplayName("비활성 타임아웃보다 오래된 세션은 다음 registerSession 시 자동으로 정리된다")
    void registerSession_removesInactiveSessions() {
        // 과거 score(0 epoch)로 수동 삽입 — 비활성 세션 시뮬레이션
        String staleHash = TokenKeyHasher.sha256Hex("stale-token");
        redisTemplate.opsForZSet().add("test:sessions:" + userId, staleHash, 0.0);

        // 새 세션 등록 시 cutoffMillis 계산으로 stale 세션 제거
        String newToken = UUID.randomUUID().toString();
        sessionRegistry.registerSession(userId, newToken, 604800L);

        Double staleScore = redisTemplate.opsForZSet().score("test:sessions:" + userId, staleHash);
        assertThat(staleScore).isNull();
    }
}
