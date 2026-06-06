package com.example.auth.application;

import com.example.auth.application.port.TokenGeneratorPort;
import com.example.auth.domain.repository.AccessTokenInvalidationStore;
import com.example.auth.domain.repository.BulkInvalidationStore;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
@DisplayName("ForceLogoutUseCase 단위 테스트 (TASK-BE-147 후 — domain port 사용)")
class ForceLogoutUseCaseTest {

    private static final String ACCOUNT_ID = "acc-1";
    private static final long REFRESH_TTL = 604_800L;
    private static final long ACCESS_TTL = 1_800L;

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private BulkInvalidationStore bulkInvalidationStore;
    @Mock private AccessTokenInvalidationStore accessTokenInvalidationStore;
    @Mock private TokenGeneratorPort tokenGeneratorPort;

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
}
