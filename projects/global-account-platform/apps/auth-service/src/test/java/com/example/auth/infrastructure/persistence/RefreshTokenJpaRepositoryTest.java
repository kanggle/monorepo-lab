package com.example.auth.infrastructure.persistence;

import com.example.auth.domain.token.RefreshToken;
import com.example.testsupport.integration.DockerAvailableCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@DisplayName("RefreshTokenJpaRepository 쿼리 슬라이스 테스트")
class RefreshTokenJpaRepositoryTest {

    @SuppressWarnings("resource")
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("auth_db")
            .withUsername("test")
            .withPassword("test")
            .withCommand("mysqld", "--log-bin-trust-function-creators=1")
            .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private RefreshTokenJpaRepository repo;

    @BeforeEach
    void cleanup() {
        repo.deleteAll();
    }

    // ── findByJti ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByJti — 존재하는 jti → 토큰 반환")
    void findByJti_existing_returnsToken() {
        String jti = uuid();
        repo.save(active(jti, "acc-1", null, null));

        Optional<RefreshTokenJpaEntity> result = repo.findByJti(jti);

        assertThat(result).isPresent();
        assertThat(result.get().getJti()).isEqualTo(jti);
    }

    @Test
    @DisplayName("findByJti — 없는 jti → empty")
    void findByJti_unknown_returnsEmpty() {
        assertThat(repo.findByJti("ghost-" + uuid())).isEmpty();
    }

    // ── existsByRotatedFrom / findByRotatedFrom ───────────────────────────────

    @Test
    @DisplayName("existsByRotatedFrom — rotation chain 존재 → true")
    void existsByRotatedFrom_existingChain_returnsTrue() {
        String parentJti = uuid();
        String childJti = uuid();
        repo.save(active(parentJti, "acc-1", null, null));
        repo.save(active(childJti, "acc-1", parentJti, null));

        assertThat(repo.existsByRotatedFrom(parentJti)).isTrue();
    }

    @Test
    @DisplayName("existsByRotatedFrom — chain 없음 → false")
    void existsByRotatedFrom_noChain_returnsFalse() {
        String jti = uuid();
        repo.save(active(jti, "acc-1", null, null));

        assertThat(repo.existsByRotatedFrom(jti)).isFalse();
    }

    @Test
    @DisplayName("findByRotatedFrom — rotation chain 토큰 조회")
    void findByRotatedFrom_existingChain_returnsToken() {
        String parentJti = uuid();
        String childJti = uuid();
        repo.save(active(parentJti, "acc-1", null, null));
        repo.save(active(childJti, "acc-1", parentJti, null));

        Optional<RefreshTokenJpaEntity> child = repo.findByRotatedFrom(parentJti);

        assertThat(child).isPresent();
        assertThat(child.get().getJti()).isEqualTo(childJti);
    }

    // ── revokeAllByAccountId ─────────────────────────────────────────────────

    @Test
    @DisplayName("revokeAllByAccountId — 활성 토큰만 revoke, 이미 revoked 는 건드리지 않음")
    void revokeAllByAccountId_revokesActiveOnly() {
        String accountId = "acc-" + uuid();
        String jti1 = uuid();
        String jti2 = uuid();
        String jtiRevoked = uuid();

        repo.save(active(jti1, accountId, null, null));
        repo.save(active(jti2, accountId, null, null));
        repo.save(revoked(jtiRevoked, accountId));

        int count = repo.revokeAllByAccountId(accountId);

        assertThat(count).isEqualTo(2);

        // 재조회로 상태 검증
        assertThat(repo.findByJti(jti1)).isPresent().get()
                .extracting(RefreshTokenJpaEntity::isRevoked).isEqualTo(true);
        assertThat(repo.findByJti(jti2)).isPresent().get()
                .extracting(RefreshTokenJpaEntity::isRevoked).isEqualTo(true);
    }

    // ── findActiveJtisByAccountId ────────────────────────────────────────────

    @Test
    @DisplayName("findActiveJtisByAccountId — revoked 행 제외 active JTI 목록")
    void findActiveJtisByAccountId_excludesRevoked() {
        String accountId = "acc-" + uuid();
        String activeJti = uuid();
        String revokedJti = uuid();

        repo.save(active(activeJti, accountId, null, null));
        repo.save(revoked(revokedJti, accountId));

        List<String> jtis = repo.findActiveJtisByAccountId(accountId);

        assertThat(jtis).containsExactly(activeJti);
    }

    // ── findActiveJtisByDeviceId ─────────────────────────────────────────────

    @Test
    @DisplayName("findActiveJtisByDeviceId — device 단위 active JTI, revoked 제외")
    void findActiveJtisByDeviceId_excludesRevoked() {
        String deviceId = "dev-" + uuid();
        String accountId = "acc-" + uuid();
        String activeJti = uuid();
        String revokedJti = uuid();

        repo.save(activeWithDevice(activeJti, accountId, deviceId));
        repo.save(revokedWithDevice(revokedJti, accountId, deviceId));

        List<String> jtis = repo.findActiveJtisByDeviceId(deviceId);

        assertThat(jtis).containsExactly(activeJti);
    }

    // ── revokeAllByDeviceId ──────────────────────────────────────────────────

    @Test
    @DisplayName("revokeAllByDeviceId — device 단위 active 토큰 bulk revoke")
    void revokeAllByDeviceId_revokesActiveOnly() {
        String deviceId = "dev-" + uuid();
        String accountId = "acc-" + uuid();
        String jti1 = uuid();
        String jti2 = uuid();
        String jtiAlreadyRevoked = uuid();

        repo.save(activeWithDevice(jti1, accountId, deviceId));
        repo.save(activeWithDevice(jti2, accountId, deviceId));
        repo.save(revokedWithDevice(jtiAlreadyRevoked, accountId, deviceId));

        int count = repo.revokeAllByDeviceId(deviceId);

        assertThat(count).isEqualTo(2);
        assertThat(repo.findActiveJtisByDeviceId(deviceId)).isEmpty();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

    private RefreshTokenJpaEntity active(String jti, String accountId,
                                         String rotatedFrom, String deviceId) {
        RefreshToken token = RefreshToken.create(jti, accountId,
                Instant.now(), Instant.now().plus(Duration.ofDays(30)),
                rotatedFrom, null, deviceId);
        return RefreshTokenJpaEntity.fromDomain(token);
    }

    private RefreshTokenJpaEntity revoked(String jti, String accountId) {
        RefreshToken token = RefreshToken.create(jti, accountId,
                Instant.now(), Instant.now().plus(Duration.ofDays(30)),
                null, null, null);
        token.revoke();
        return RefreshTokenJpaEntity.fromDomain(token);
    }

    private RefreshTokenJpaEntity activeWithDevice(String jti, String accountId, String deviceId) {
        return active(jti, accountId, null, deviceId);
    }

    private RefreshTokenJpaEntity revokedWithDevice(String jti, String accountId, String deviceId) {
        RefreshToken token = RefreshToken.create(jti, accountId,
                Instant.now(), Instant.now().plus(Duration.ofDays(30)),
                null, null, deviceId);
        token.revoke();
        return RefreshTokenJpaEntity.fromDomain(token);
    }
}
