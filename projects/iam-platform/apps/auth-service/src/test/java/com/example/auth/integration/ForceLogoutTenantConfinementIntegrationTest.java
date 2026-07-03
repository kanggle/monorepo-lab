package com.example.auth.integration;

import com.example.auth.application.ForceLogoutUseCase;
import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.credentials.CredentialHash;
import com.example.auth.infrastructure.persistence.CredentialJpaEntity;
import com.example.auth.infrastructure.persistence.CredentialJpaRepository;
import com.example.auth.infrastructure.persistence.RefreshTokenJpaRepository;
import com.example.security.password.Argon2idPasswordHasher;
import com.example.testsupport.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-468 — session-revoke (force-logout) tenant confinement.
 *
 * <p>{@link ForceLogoutUseCase} confines the revoke to the operator's active tenant
 * (stamped as {@code X-Tenant-Id} by admin-service, TASK-BE-467). This IT is the
 * AC-4 authority (the Docker-free {@code :test} cannot exercise the credential-tenant
 * gate against real MySQL + Redis):
 *
 * <ul>
 *   <li><b>cross-tenant</b> ({@code X-Tenant-Id} = a tenant that does not own the
 *       account) → no-op: 0 revoked, the account's refresh tokens survive.</li>
 *   <li><b>same-tenant</b> → the tokens are revoked.</li>
 *   <li><b>net-zero</b> (no tenant / {@code '*'}) → revokes (today's behavior).</li>
 * </ul>
 *
 * <p>Drives the use-case directly (not the HTTP layer) so the confinement + repo +
 * Redis path runs against real infra without the {@code /internal/auth} auth ceremony;
 * the controller merely threads {@code X-Tenant-Id} into {@code execute(id, tenant)}.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Force-logout tenant confinement 통합 테스트 — TASK-BE-468")
class ForceLogoutTenantConfinementIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        // MySQL + Kafka registered by AbstractIntegrationTest.
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        // AccountServiceClient bean needs a base-url to instantiate; this test never calls it.
        registry.add("auth.account-service.base-url", () -> "http://localhost:1");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    // AccountServiceClient mints a GAP client_credentials bearer via a SAS self-call
    // (unreachable in @SpringBootTest); mock the provider so the context wires (mirrors
    // AuthIntegrationTest). Not exercised by this test.
    @MockitoBean
    private com.example.auth.infrastructure.client.IamClientCredentialsTokenProvider gapTokenProvider;

    @Autowired private ForceLogoutUseCase forceLogoutUseCase;
    @Autowired private CredentialJpaRepository credentialJpaRepository;
    @Autowired private RefreshTokenJpaRepository refreshTokenJpaRepository;
    @Autowired private JdbcTemplate jdbc;

    private static final String WMS = "wms";
    private static final String FAN = "fan-platform";
    private static final String ACC_WMS = "acc-wms-1";
    private static final String ACC_FAN = "acc-fan-1";

    // Argon2 is deliberately slow — hash once, reuse across the seed.
    private static final String PW_HASH = new Argon2idPasswordHasher().hash("password123");

    @BeforeEach
    void seed() {
        refreshTokenJpaRepository.deleteAll();
        credentialJpaRepository.deleteAll();
        Instant now = Instant.now();

        credentialJpaRepository.save(CredentialJpaEntity.fromDomain(
                Credential.create(ACC_WMS, WMS, "wms-user@example.com", CredentialHash.argon2id(PW_HASH), now)));
        credentialJpaRepository.save(CredentialJpaEntity.fromDomain(
                Credential.create(ACC_FAN, FAN, "fan-user@example.com", CredentialHash.argon2id(PW_HASH), now)));

        seedActiveToken("jti-wms-1", ACC_WMS, WMS, now);
        seedActiveToken("jti-wms-2", ACC_WMS, WMS, now);
        seedActiveToken("jti-fan-1", ACC_FAN, FAN, now);
    }

    private void seedActiveToken(String jti, String accountId, String tenantId, Instant now) {
        jdbc.update("""
                INSERT INTO refresh_tokens (jti, account_id, tenant_id, issued_at, expires_at, revoked)
                VALUES (?, ?, ?, ?, ?, false)
                """, jti, accountId, tenantId,
                Timestamp.from(now), Timestamp.from(now.plusSeconds(604_800)));
    }

    @Test
    @DisplayName("cross-tenant (계정=wms, X-Tenant-Id=fan-platform) → 0 revoked, wms 세션 생존")
    void crossTenant_isNoOp_andSessionsSurvive() {
        ForceLogoutUseCase.Result result = forceLogoutUseCase.execute(ACC_WMS, FAN);

        assertThat(result.revokedTokenCount()).isZero();
        // Enumeration-safe confinement: the other tenant's sessions are untouched.
        assertThat(refreshTokenJpaRepository.findActiveJtisByAccountId(ACC_WMS)).hasSize(2);
    }

    @Test
    @DisplayName("same-tenant (X-Tenant-Id=wms) → 2 revoked, 활성 세션 0")
    void sameTenant_revokes() {
        ForceLogoutUseCase.Result result = forceLogoutUseCase.execute(ACC_WMS, WMS);

        assertThat(result.revokedTokenCount()).isEqualTo(2);
        assertThat(refreshTokenJpaRepository.findActiveJtisByAccountId(ACC_WMS)).isEmpty();
    }

    @Test
    @DisplayName("NET-ZERO: 헤더 없음 → 정상 revoke")
    void noTenant_isNetZero_revokes() {
        forceLogoutUseCase.execute(ACC_FAN);

        assertThat(refreshTokenJpaRepository.findActiveJtisByAccountId(ACC_FAN)).isEmpty();
    }

    @Test
    @DisplayName("NET-ZERO: X-Tenant-Id='*' (SUPER_ADMIN) → 테넌트 게이트 우회, 정상 revoke")
    void wildcardTenant_isNetZero_revokes() {
        forceLogoutUseCase.execute(ACC_WMS, "*");

        assertThat(refreshTokenJpaRepository.findActiveJtisByAccountId(ACC_WMS)).isEmpty();
    }
}
