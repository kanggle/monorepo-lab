package com.example.auth.application;

import com.example.auth.application.port.TokenGeneratorPort;
import com.example.auth.domain.repository.AccessTokenInvalidationStore;
import com.example.auth.domain.repository.BulkInvalidationStore;
import com.example.auth.domain.repository.CredentialRepository;
import com.example.auth.domain.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForceLogoutUseCase {

    /** SUPER_ADMIN platform-scope sentinel — treated as net-zero (no tenant confinement). */
    private static final String PLATFORM_TENANT_ID = "*";

    private final RefreshTokenRepository refreshTokenRepository;
    private final BulkInvalidationStore bulkInvalidationStore;
    private final AccessTokenInvalidationStore accessTokenInvalidationStore;
    private final TokenGeneratorPort tokenGeneratorPort;
    private final CredentialRepository credentialRepository;

    /**
     * NET-ZERO overload — no active tenant supplied (revoke across the account's
     * tenant, byte-identical to the pre-BE-468 behavior). Retained for the internal
     * callers/tests that do not thread a tenant.
     */
    @Transactional
    public Result execute(String accountId) {
        return execute(accountId, null);
    }

    /**
     * TASK-BE-468 — tenant-confined force-logout. The operator's active tenant
     * ({@code requestedTenantId}, from {@code X-Tenant-Id}) gates the account:
     *
     * <ul>
     *   <li>absent / blank / {@code '*'} (SUPER_ADMIN platform scope) → NET-ZERO:
     *       revoke across the account's tenant (today's behavior).</li>
     *   <li>a concrete tenant that does NOT own the account (credential's tenant
     *       differs, or no credential) → <b>no-op</b>: {@code revokedTokenCount=0},
     *       NO DB revoke and NO Redis invalidation (enumeration-safe confinement —
     *       never disrupts another tenant's sessions, never leaks existence).</li>
     *   <li>a concrete tenant that owns the account → the normal revoke + Redis path
     *       (identical to net-zero, since the account's tokens are all in that
     *       tenant).</li>
     * </ul>
     */
    @Transactional
    public Result execute(String accountId, String requestedTenantId) {
        if (isConcreteTenant(requestedTenantId) && !ownsAccount(requestedTenantId, accountId)) {
            // Cross-tenant target: confine to a no-op. No DB revoke, no Redis marker.
            log.info("force-logout confined: tenant={} does not own account={} — no-op",
                    requestedTenantId, accountId);
            return new Result(accountId, 0, Instant.now());
        }

        int revokedCount = refreshTokenRepository.revokeAllByAccountId(accountId);
        Instant revokedAt = Instant.now();
        bulkInvalidationStore.invalidateAll(accountId, tokenGeneratorPort.refreshTokenTtlSeconds());
        accessTokenInvalidationStore.invalidateAccessBefore(
                accountId, revokedAt, tokenGeneratorPort.accessTokenTtlSeconds());
        return new Result(accountId, revokedCount, revokedAt);
    }

    private static boolean isConcreteTenant(String tenantId) {
        return tenantId != null && !tenantId.isBlank() && !PLATFORM_TENANT_ID.equals(tenantId);
    }

    private boolean ownsAccount(String tenantId, String accountId) {
        return credentialRepository.findByAccountId(accountId)
                .map(c -> tenantId.equals(c.getTenantId()))
                .orElse(false);
    }

    public record Result(String accountId, int revokedTokenCount, Instant revokedAt) {}
}
