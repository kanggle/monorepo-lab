package com.example.auth.infrastructure.persistence;

import com.example.auth.domain.session.DeviceSession;
import com.example.auth.domain.session.RevokeReason;
import com.example.testsupport.integration.DockerAvailableCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Slice test for {@link DeviceSessionJpaRepository} against real MySQL via Testcontainers.
 * Validates the unique constraint and the eviction-order query
 * {@link DeviceSessionJpaRepository#findOldestActiveByAccountId(String, org.springframework.data.domain.Pageable)}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@DisplayName("DeviceSessionJpaRepository")
@ExtendWith(DockerAvailableCondition.class)
class DeviceSessionJpaRepositoryTest {

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
    private DeviceSessionJpaRepository repo;

    @BeforeEach
    void cleanup() {
        repo.deleteAll();
    }

    @Test
    @DisplayName("findOldestActiveByAccountId orders by last_seen_at ASC and excludes revoked")
    void oldestActiveOrdering() {
        Instant base = Instant.parse("2026-04-01T00:00:00Z");
        save(make("dev-newest", "acc-1", "fp-A", base, base.plusSeconds(300), null, null));
        save(make("dev-mid",    "acc-1", "fp-B", base, base.plusSeconds(120), null, null));
        save(make("dev-oldest", "acc-1", "fp-C", base, base.plusSeconds(10),  null, null));
        // revoked — must be excluded
        save(make("dev-revoked", "acc-1", "fp-D", base, base.plusSeconds(5),
                base.plusSeconds(100), RevokeReason.USER_REQUESTED));

        List<DeviceSessionJpaEntity> oldest =
                repo.findOldestActiveByAccountId("acc-1", PageRequest.of(0, 2));

        assertThat(oldest).extracting(DeviceSessionJpaEntity::getDeviceId)
                .containsExactly("dev-oldest", "dev-mid");
    }

    @Test
    @DisplayName("countActiveByAccountId excludes revoked rows")
    void countActiveExcludesRevoked() {
        Instant t = Instant.parse("2026-04-01T00:00:00Z");
        save(make("d1", "acc-2", "f1", t, t, null, null));
        save(make("d2", "acc-2", "f2", t, t, null, null));
        save(make("d3", "acc-2", "f3", t, t, t.plusSeconds(60), RevokeReason.EVICTED_BY_LIMIT));

        assertThat(repo.countActiveByAccountId("acc-2")).isEqualTo(2L);
    }

    @Test
    @DisplayName("(account_id, fingerprint, issued_at) is unique")
    void uniqueConstraint() {
        Instant t = Instant.parse("2026-04-01T00:00:00Z");
        save(make("d-a", "acc-3", "fp-same", t, t, null, null));

        DeviceSessionJpaEntity dup = make("d-b", "acc-3", "fp-same", t, t, null, null);

        assertThatThrownBy(() -> {
            repo.saveAndFlush(dup);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("findActiveByAccountAndFingerprint ignores revoked rows")
    void findActiveIgnoresRevoked() {
        Instant t = Instant.parse("2026-04-01T00:00:00Z");
        save(make("d-revoked", "acc-4", "fp-x", t, t,
                t.plusSeconds(60), RevokeReason.USER_REQUESTED));
        // After previous row revoked, a fresh active row may exist with later issued_at.
        save(make("d-active",  "acc-4", "fp-x", t.plusSeconds(120), t.plusSeconds(120),
                null, null));

        var found = repo.findActiveByAccountAndFingerprint("acc-4", "fp-x");
        assertThat(found).isPresent();
        assertThat(found.get().getDeviceId()).isEqualTo("d-active");
    }

    private DeviceSessionJpaEntity save(DeviceSessionJpaEntity e) {
        return repo.saveAndFlush(e);
    }

    private static DeviceSessionJpaEntity make(String deviceId, String accountId, String fp,
                                               Instant issuedAt, Instant lastSeenAt,
                                               Instant revokedAt, RevokeReason reason) {
        DeviceSession s = new DeviceSession(
                null, deviceId, accountId, fp,
                "UA", "1.1.1.1", "KR",
                issuedAt, lastSeenAt, revokedAt, reason);
        return DeviceSessionJpaEntity.fromDomain(s);
    }
}
