package com.example.auth;

import com.example.auth.application.service.UserWithdrawalService;
import com.example.auth.domain.entity.AuditEventType;
import com.example.auth.domain.entity.User;
import com.example.auth.domain.repository.AccessTokenBlocklist;
import com.example.auth.domain.repository.RefreshTokenStore;
import com.example.auth.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Tag("integration")
@Testcontainers
@DisplayName("UserWithdrawal 통합 테스트")
class UserWithdrawalIntegrationTest {

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

    @Container
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("jwt.secret", () -> "integration-test-secret-key-min-32chars!!");
    }

    @Autowired
    private UserWithdrawalService userWithdrawalService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @Autowired
    private AccessTokenBlocklist accessTokenBlocklist;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("탈퇴 처리 시 사용자가 비활성화되고 감사 로그가 기록된다")
    void handleUserWithdrawal_deactivatesUserAndRecordsAudit() {
        User user = User.create("withdrawal-test-" + UUID.randomUUID() + "@example.com", "encodedPw", "TestUser");
        userRepository.save(user);

        userWithdrawalService.handleUserWithdrawal(user.getId().toString());

        User deactivated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(deactivated.isActive()).isFalse();

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_audit_log WHERE user_id = ? AND event_type = ?",
                Integer.class,
                user.getId(), AuditEventType.ACCOUNT_DEACTIVATED.name());
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 비활성화된 사용자에 대한 중복 처리 시 에러 없이 완료된다")
    void handleUserWithdrawal_alreadyDeactivated_idempotent() {
        User user = User.create("withdrawal-idempotent-" + UUID.randomUUID() + "@example.com", "encodedPw", "TestUser");
        userRepository.save(user);

        userWithdrawalService.handleUserWithdrawal(user.getId().toString());
        userWithdrawalService.handleUserWithdrawal(user.getId().toString());

        User deactivated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(deactivated.isActive()).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 userId로 호출 시 에러 없이 완료된다")
    void handleUserWithdrawal_nonExistentUser_completesWithoutError() {
        String randomUserId = UUID.randomUUID().toString();

        userWithdrawalService.handleUserWithdrawal(randomUserId);
    }

    @Test
    @DisplayName("Flyway 마이그레이션으로 users 테이블에 active 컬럼이 존재한다")
    void flywayMigration_activeColumnExists() {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'active')",
                Boolean.class);
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("탈퇴 처리 시 해당 사용자의 모든 refresh token이 폐기된다")
    void handleUserWithdrawal_invalidatesAllRefreshTokens() {
        User user = User.create("withdrawal-rt-" + UUID.randomUUID() + "@example.com", "encodedPw", "TestUser");
        userRepository.save(user);
        UUID userId = user.getId();

        refreshTokenStore.save("refresh-token-1", userId, 604800L);
        refreshTokenStore.save("refresh-token-2", userId, 604800L);

        userWithdrawalService.handleUserWithdrawal(userId.toString());

        Set<String> remainingTokens = refreshTokenStore.findAllTokenHashesByUserId(userId);
        assertThat(remainingTokens).isEmpty();
        assertThat(refreshTokenStore.isRevoked("refresh-token-1")).isTrue();
        assertThat(refreshTokenStore.isRevoked("refresh-token-2")).isTrue();
    }

    @Test
    @DisplayName("탈퇴 처리 시 해당 사용자의 userId가 access token 블랙리스트에 등록된다")
    void handleUserWithdrawal_blocksUserAccessTokens() {
        User user = User.create("withdrawal-at-" + UUID.randomUUID() + "@example.com", "encodedPw", "TestUser");
        userRepository.save(user);
        UUID userId = user.getId();

        userWithdrawalService.handleUserWithdrawal(userId.toString());

        assertThat(accessTokenBlocklist.isUserBlocked(userId)).isTrue();
    }
}
