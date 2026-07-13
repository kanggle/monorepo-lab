package com.example.auth.infrastructure.persistence;

import com.example.auth.domain.token.RefreshToken;
import com.example.testsupport.integration.DockerAvailableCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
        String accountId = uuid();
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
        String accountId = uuid();
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
        String deviceId = uuid();
        String accountId = uuid();
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
        String deviceId = uuid();
        String accountId = uuid();
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

    /**
     * TASK-MONO-393 — the isolation property, which nothing asserted until now.
     *
     * <p>Every other fixture in this class seeds a SINGLE device, so it cannot tell a
     * device-scoped predicate apart from an account-scoped one: swap
     * {@code WHERE r.deviceId = :deviceId} for {@code WHERE r.accountId = :accountId} and
     * they all still pass. Two devices on ONE account is the smallest fixture that can see
     * the difference — and "one user, several devices" is the only shape this query exists for.
     *
     * <p>This is the property behind "log this device out": it must log out exactly that
     * device. The integration test that nominally covered it goes through HTTP, where a Redis
     * outage makes the fail-closed blacklist check answer 401 for every token — so it can fail
     * (and pass) for reasons that have nothing to do with scoping. This one cannot.
     */
    @Test
    @DisplayName("revokeAllByDeviceId — 같은 계정의 다른 device 토큰은 살아남는다 (account 스코프였다면 RED)")
    void revokeAllByDeviceId_leavesOtherDevicesOfTheSameAccountAlone() {
        String accountId = uuid();          // ONE account …
        String deviceA = uuid();            // … on TWO devices
        String deviceB = uuid();
        String jtiA = uuid();
        String jtiB = uuid();

        repo.save(activeWithDevice(jtiA, accountId, deviceA));
        repo.save(activeWithDevice(jtiB, accountId, deviceB));

        int count = repo.revokeAllByDeviceId(deviceA);

        assertThat(count).isEqualTo(1);
        assertThat(repo.findByJti(jtiA)).isPresent().get()
                .extracting(RefreshTokenJpaEntity::isRevoked).isEqualTo(true);
        assertThat(repo.findByJti(jtiB)).isPresent().get()
                .extracting(RefreshTokenJpaEntity::isRevoked).isEqualTo(false);
        assertThat(repo.findActiveJtisByDeviceId(deviceB)).containsExactly(jtiB);
    }

    @Test
    @DisplayName("findActiveJtisByDeviceId — 같은 계정의 다른 device JTI 는 새어 나오지 않는다")
    void findActiveJtisByDeviceId_doesNotLeakOtherDevicesOfTheSameAccount() {
        String accountId = uuid();
        String deviceA = uuid();
        String deviceB = uuid();
        String jtiA = uuid();
        String jtiB = uuid();

        repo.save(activeWithDevice(jtiA, accountId, deviceA));
        repo.save(activeWithDevice(jtiB, accountId, deviceB));

        assertThat(repo.findActiveJtisByDeviceId(deviceA)).containsExactly(jtiA);
        assertThat(repo.findActiveJtisByDeviceId(deviceB)).containsExactly(jtiB);
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
