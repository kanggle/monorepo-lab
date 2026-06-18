package com.example.auth.application;

import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.repository.CredentialRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-298 (ADR-MONO-040 Phase 3 part A) — unit tests for
 * {@link ResolveCredentialAccountIdUseCase}, the auth_db.credentials email →
 * account_id resolver (reverse of Phase 2) backing the internal
 * {@code POST /internal/auth/credentials/account-id-by-email} endpoint the
 * admin-service {@code oidc_subject} backfill consults.
 *
 * <p>Central concern: tenant scoping. {@code credentials.email} is unique only per
 * {@code (tenant_id, email)} — a wrong tenant's account would mis-authorize an
 * operator. These tests pin the tenant-scoped happy path, the global-unambiguity
 * fallback, and the cross-tenant ambiguity → empty (fail-soft, no wrong account_id).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("ResolveCredentialAccountIdUseCase (email→account_id, tenant-scoped) 단위 테스트")
class ResolveCredentialAccountIdUseCaseTest {

    private static final String EMAIL = "operator@example.com";
    private static final String TENANT = "acme-corp";
    private static final String ACME_ACCOUNT = "01928c4a-7e9f-7c00-9a40-d2b1f5e8c200";
    private static final String GLOBEX_ACCOUNT = "01928c4a-7e9f-7c00-9a40-d2b1f5e8c999";

    @Mock CredentialRepository credentialRepository;
    @InjectMocks ResolveCredentialAccountIdUseCase useCase;

    private Credential credential(String tenantId, String accountId) {
        return new Credential(1L, accountId, tenantId, EMAIL, "hash", "argon2id",
                Instant.now(), Instant.now(), 0);
    }

    @Test
    @DisplayName("tenant-scoped 매칭 → 그 account_id 반환 (cross-tenant 조회 안 함)")
    void tenantScopedHit_returnsAccountId() {
        when(credentialRepository.findByTenantIdAndEmail(TENANT, EMAIL))
                .thenReturn(Optional.of(credential(TENANT, ACME_ACCOUNT)));

        assertThat(useCase.resolveAccountId(EMAIL, TENANT)).contains(ACME_ACCOUNT);
        verify(credentialRepository, never()).findAllByEmail(anyString());
    }

    @Test
    @DisplayName("tenant miss + email 이 전역적으로 unique(1건) → 그 account_id 해석")
    void tenantMiss_globallyUnambiguous_resolves() {
        when(credentialRepository.findByTenantIdAndEmail("other-tenant", EMAIL))
                .thenReturn(Optional.empty());
        when(credentialRepository.findAllByEmail(EMAIL))
                .thenReturn(List.of(credential(TENANT, ACME_ACCOUNT)));

        assertThat(useCase.resolveAccountId(EMAIL, "other-tenant")).contains(ACME_ACCOUNT);
    }

    @Test
    @DisplayName("tenant miss + cross-tenant 모호(2건) → empty (fail-soft, 잘못된 account_id 안 씀)")
    void tenantMiss_crossTenantAmbiguous_returnsEmpty() {
        when(credentialRepository.findByTenantIdAndEmail("unknown-tenant", EMAIL))
                .thenReturn(Optional.empty());
        when(credentialRepository.findAllByEmail(EMAIL))
                .thenReturn(List.of(
                        credential("acme-corp", ACME_ACCOUNT),
                        credential("globex-corp", GLOBEX_ACCOUNT)));

        assertThat(useCase.resolveAccountId(EMAIL, "unknown-tenant")).isEmpty();
    }

    @Test
    @DisplayName("어느 credential 도 없음 → empty")
    void noCredential_returnsEmpty() {
        when(credentialRepository.findByTenantIdAndEmail(TENANT, EMAIL))
                .thenReturn(Optional.empty());
        when(credentialRepository.findAllByEmail(EMAIL)).thenReturn(List.of());

        assertThat(useCase.resolveAccountId(EMAIL, TENANT)).isEmpty();
    }

    @Test
    @DisplayName("blank/null email → empty, repository 미조회")
    void blankEmail_returnsEmptyWithoutLookup() {
        assertThat(useCase.resolveAccountId("  ", TENANT)).isEmpty();
        assertThat(useCase.resolveAccountId(null, TENANT)).isEmpty();
        verify(credentialRepository, never()).findByTenantIdAndEmail(anyString(), anyString());
        verify(credentialRepository, never()).findAllByEmail(anyString());
    }

    @Test
    @DisplayName("null tenant → tenant-scoped 조회 생략, global-unambiguity 경로만 사용")
    void nullTenant_usesGlobalUnambiguityPathOnly() {
        when(credentialRepository.findAllByEmail(EMAIL))
                .thenReturn(List.of(credential(TENANT, ACME_ACCOUNT)));

        assertThat(useCase.resolveAccountId(EMAIL, null)).contains(ACME_ACCOUNT);
        verify(credentialRepository, never()).findByTenantIdAndEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("SUPER_ADMIN 센티넬 tenant '*' → '*' 스코프로 직접 해석")
    void superAdminSentinelTenant_resolvesUnderStar() {
        String starAccount = "01928c4a-7e9f-7c00-9a40-d2b1f5e8c100";
        when(credentialRepository.findByTenantIdAndEmail("*", EMAIL))
                .thenReturn(Optional.of(credential("*", starAccount)));

        assertThat(useCase.resolveAccountId(EMAIL, "*")).contains(starAccount);
        verify(credentialRepository, never()).findAllByEmail(anyString());
    }
}
