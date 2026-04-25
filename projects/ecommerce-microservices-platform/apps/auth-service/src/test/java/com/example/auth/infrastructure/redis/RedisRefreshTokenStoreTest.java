package com.example.auth.infrastructure.redis;

import com.example.auth.domain.repository.RefreshTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Tag("integration")
@Testcontainers
@DisplayName("RedisRefreshTokenStore 통합 테스트")
class RedisRefreshTokenStoreTest {

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
    }

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    private String token;
    private UUID userId;

    @BeforeEach
    void setUp() {
        token = UUID.randomUUID().toString();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("save 후 findUserIdByToken으로 userId를 조회할 수 있다")
    void save_and_find() {
        refreshTokenStore.save(token, userId, 60L);

        Optional<UUID> found = refreshTokenStore.findUserIdByToken(token);

        assertThat(found).contains(userId);
    }

    @Test
    @DisplayName("존재하지 않는 토큰 조회 시 Optional.empty() 반환")
    void findUserIdByToken_notExist_returnsEmpty() {
        Optional<UUID> found = refreshTokenStore.findUserIdByToken("nonexistent-token");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("invalidate 전에는 isRevoked가 false")
    void isRevoked_beforeInvalidate_returnsFalse() {
        refreshTokenStore.save(token, userId, 60L);

        assertThat(refreshTokenStore.isRevoked(token)).isFalse();
    }

    @Test
    @DisplayName("invalidate 후 findUserIdByToken은 empty를 반환한다")
    void invalidate_removesToken() {
        refreshTokenStore.save(token, userId, 60L);
        refreshTokenStore.invalidate(token, 60L);

        Optional<UUID> found = refreshTokenStore.findUserIdByToken(token);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("invalidate 후 isRevoked가 true")
    void invalidate_marksAsRevoked() {
        refreshTokenStore.save(token, userId, 60L);
        refreshTokenStore.invalidate(token, 60L);

        assertThat(refreshTokenStore.isRevoked(token)).isTrue();
    }

    @Test
    @DisplayName("저장하지 않은 토큰은 revoked가 아니다")
    void isRevoked_unknownToken_returnsFalse() {
        assertThat(refreshTokenStore.isRevoked("unknown-token")).isFalse();
    }

}
