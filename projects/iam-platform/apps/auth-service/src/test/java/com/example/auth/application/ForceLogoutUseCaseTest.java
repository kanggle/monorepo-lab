package com.example.auth.application;

import com.example.auth.application.port.TokenGeneratorPort;
import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.repository.AccessTokenInvalidationStore;
import com.example.auth.domain.repository.BulkInvalidationStore;
import com.example.auth.domain.repository.CredentialRepository;
import com.example.auth.domain.repository.RefreshTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("ForceLogoutUseCase 단위 테스트 (TASK-BE-147 후 — domain port 사용)")
class ForceLogoutUseCaseTest {

    private static final String ACCOUNT_ID = "acc-1";
    private static final long REFRESH_TTL = 604_800L;
    private static final long ACCESS_TTL = 1_800L;

    private static final String TENANT_WMS = "wms";

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private BulkInvalidationStore bulkInvalidationStore;
    @Mock private AccessTokenInvalidationStore accessTokenInvalidationStore;
    @Mock private TokenGeneratorPort tokenGeneratorPort;
    @Mock private CredentialRepository credentialRepository;

    @InjectMocks
    private ForceLogoutUseCase useCase;

    @Test
    @DisplayName("execute: refresh revoke → bulk marker → access marker 순서로 호출, 결과는 revoked 카운트 + revokedAt")
    void execute_invokesAllInvalidations() {
        given(refreshTokenRepository.revokeAllByAccountId(ACCOUNT_ID)).willReturn(3);
        given(tokenGeneratorPort.refreshTokenTtlSeconds()).willReturn(REFRESH_TTL);
        given(tokenGeneratorPort.accessTokenTtlSeconds()).willReturn(ACCESS_TTL);

        Instant before = Instant.now();
        ForceLogoutUseCase.Result result = useCase.execute(ACCOUNT_ID);
        Instant after = Instant.now();

        InOrder order = inOrder(refreshTokenRepository, bulkInvalidationStore, accessTokenInvalidationStore);
        order.verify(refreshTokenRepository).revokeAllByAccountId(ACCOUNT_ID);
        order.verify(bulkInvalidationStore).invalidateAll(ACCOUNT_ID, REFRESH_TTL);
        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        order.verify(accessTokenInvalidationStore).invalidateAccessBefore(
                eq(ACCOUNT_ID), instantCaptor.capture(), eq(ACCESS_TTL));

        assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(result.revokedTokenCount()).isEqualTo(3);
        assertThat(result.revokedAt()).isBetween(before, after);
        assertThat(instantCaptor.getValue()).isEqualTo(result.revokedAt());
    }

    // ── TASK-BE-468 tenant confinement ──────────────────────────────────────────

    @Test
    @DisplayName("cross-tenant: 활성 테넌트가 계정 소유가 아니면 no-op (0 revoked, DB/Redis 미접촉)")
    void execute_crossTenant_isNoOp() {
        Credential cred = mock(Credential.class);
        given(cred.getTenantId()).willReturn(TENANT_WMS);
        given(credentialRepository.findByAccountId(ACCOUNT_ID)).willReturn(Optional.of(cred));

        ForceLogoutUseCase.Result result = useCase.execute(ACCOUNT_ID, "fan-platform");

        assertThat(result.revokedTokenCount()).isZero();
        verify(refreshTokenRepository, never()).revokeAllByAccountId(ACCOUNT_ID);
        verifyNoInteractions(bulkInvalidationStore, accessTokenInvalidationStore, tokenGeneratorPort);
    }

    @Test
    @DisplayName("cross-tenant: credential 부재도 not-owned → no-op (fail-closed 0 revoked)")
    void execute_noCredential_isNoOp() {
        given(credentialRepository.findByAccountId(ACCOUNT_ID)).willReturn(Optional.empty());

        ForceLogoutUseCase.Result result = useCase.execute(ACCOUNT_ID, "fan-platform");

        assertThat(result.revokedTokenCount()).isZero();
        verify(refreshTokenRepository, never()).revokeAllByAccountId(ACCOUNT_ID);
        verifyNoInteractions(bulkInvalidationStore, accessTokenInvalidationStore, tokenGeneratorPort);
    }

    @Test
    @DisplayName("same-tenant: 활성 테넌트가 계정 소유면 정상 revoke + Redis")
    void execute_sameTenant_revokes() {
        Credential cred = mock(Credential.class);
        given(cred.getTenantId()).willReturn(TENANT_WMS);
        given(credentialRepository.findByAccountId(ACCOUNT_ID)).willReturn(Optional.of(cred));
        given(refreshTokenRepository.revokeAllByAccountId(ACCOUNT_ID)).willReturn(2);
        given(tokenGeneratorPort.refreshTokenTtlSeconds()).willReturn(REFRESH_TTL);
        given(tokenGeneratorPort.accessTokenTtlSeconds()).willReturn(ACCESS_TTL);

        ForceLogoutUseCase.Result result = useCase.execute(ACCOUNT_ID, TENANT_WMS);

        assertThat(result.revokedTokenCount()).isEqualTo(2);
        verify(refreshTokenRepository).revokeAllByAccountId(ACCOUNT_ID);
        verify(bulkInvalidationStore).invalidateAll(ACCOUNT_ID, REFRESH_TTL);
    }

    @Test
    @DisplayName("net-zero: X-Tenant-Id='*' (SUPER_ADMIN) → 테넌트 게이트 우회, 정상 revoke, credential 미조회")
    void execute_wildcardTenant_isNetZero() {
        given(refreshTokenRepository.revokeAllByAccountId(ACCOUNT_ID)).willReturn(1);
        given(tokenGeneratorPort.refreshTokenTtlSeconds()).willReturn(REFRESH_TTL);
        given(tokenGeneratorPort.accessTokenTtlSeconds()).willReturn(ACCESS_TTL);

        ForceLogoutUseCase.Result result = useCase.execute(ACCOUNT_ID, "*");

        assertThat(result.revokedTokenCount()).isEqualTo(1);
        verify(refreshTokenRepository).revokeAllByAccountId(ACCOUNT_ID);
        verifyNoInteractions(credentialRepository);
    }
}
