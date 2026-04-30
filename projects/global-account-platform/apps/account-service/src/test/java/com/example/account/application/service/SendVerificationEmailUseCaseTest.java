package com.example.account.application.service;

import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.exception.EmailAlreadyVerifiedException;
import com.example.account.application.exception.RateLimitedException;
import com.example.account.application.port.EmailVerificationNotifier;
import com.example.account.domain.account.Account;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.EmailVerificationTokenStore;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.tenant.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link SendVerificationEmailUseCase} (TASK-BE-114).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SendVerificationEmailUseCase 단위 테스트")
class SendVerificationEmailUseCaseTest {

    private static final String ACCOUNT_ID = "acc-1";
    private static final String EMAIL = "user@example.com";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private EmailVerificationTokenStore tokenStore;

    @Mock
    private EmailVerificationNotifier notifier;

    @InjectMocks
    private SendVerificationEmailUseCase useCase;

    private Account unverifiedAccount() {
        return Account.reconstitute(
                ACCOUNT_ID, TenantId.FAN_PLATFORM, EMAIL, null,
                AccountStatus.ACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                null, null, null, 0
        );
    }

    private Account verifiedAccount() {
        return Account.reconstitute(
                ACCOUNT_ID, TenantId.FAN_PLATFORM, EMAIL, null,
                AccountStatus.ACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                null, null,
                Instant.parse("2026-04-01T00:00:00Z"),
                0
        );
    }

    @Test
    @DisplayName("정상 발송 — 토큰 저장(24h TTL) + 사용자 이메일로 알림 전송, 5분 슬롯 획득")
    void execute_unverifiedAccount_savesTokenAndSendsEmail() {
        given(accountRepository.findById(TenantId.FAN_PLATFORM, ACCOUNT_ID))
                .willReturn(Optional.of(unverifiedAccount()));
        given(tokenStore.tryAcquireResendSlot(eq(ACCOUNT_ID), any(Duration.class)))
                .willReturn(true);

        useCase.execute(ACCOUNT_ID);

        // Rate-limit slot uses 5-minute TTL.
        ArgumentCaptor<Duration> slotTtlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(tokenStore).tryAcquireResendSlot(eq(ACCOUNT_ID), slotTtlCaptor.capture());
        assertThat(slotTtlCaptor.getValue()).isEqualTo(Duration.ofSeconds(300));

        // Token is a UUID v4, persisted with a 24-hour TTL, and the same token
        // is forwarded to the notifier.
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> tokenTtlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(tokenStore).save(tokenCaptor.capture(), eq(ACCOUNT_ID), tokenTtlCaptor.capture());
        verify(notifier).sendVerificationEmail(eq(EMAIL), eq(tokenCaptor.getValue()));

        assertThat(tokenTtlCaptor.getValue()).isEqualTo(Duration.ofHours(24));
        assertThat(UUID.fromString(tokenCaptor.getValue())).isNotNull();
    }

    @Test
    @DisplayName("이미 인증된 계정 — EmailAlreadyVerifiedException, 슬롯/토큰/이메일 미수행")
    void execute_alreadyVerified_throwsAndDoesNothing() {
        given(accountRepository.findById(TenantId.FAN_PLATFORM, ACCOUNT_ID))
                .willReturn(Optional.of(verifiedAccount()));

        assertThatThrownBy(() -> useCase.execute(ACCOUNT_ID))
                .isInstanceOf(EmailAlreadyVerifiedException.class);

        verifyNoInteractions(tokenStore);
        verifyNoInteractions(notifier);
    }

    @Test
    @DisplayName("계정 미존재 — AccountNotFoundException")
    void execute_unknownAccount_throwsAccountNotFound() {
        given(accountRepository.findById(TenantId.FAN_PLATFORM, ACCOUNT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ACCOUNT_ID))
                .isInstanceOf(AccountNotFoundException.class);

        verifyNoInteractions(tokenStore);
        verifyNoInteractions(notifier);
    }

    @Test
    @DisplayName("레이트 리밋 차단 — RateLimitedException, 토큰 미발급/이메일 미발송")
    void execute_rateLimitMarkerExists_throwsRateLimited() {
        given(accountRepository.findById(TenantId.FAN_PLATFORM, ACCOUNT_ID))
                .willReturn(Optional.of(unverifiedAccount()));
        given(tokenStore.tryAcquireResendSlot(eq(ACCOUNT_ID), any(Duration.class)))
                .willReturn(false);

        assertThatThrownBy(() -> useCase.execute(ACCOUNT_ID))
                .isInstanceOf(RateLimitedException.class);

        verify(tokenStore, never()).save(anyString(), anyString(), any(Duration.class));
        verifyNoInteractions(notifier);
    }

    @Test
    @DisplayName("이메일 발송 실패는 swallow — 토큰은 이미 저장됨")
    void execute_notifierFails_swallowsExceptionAfterTokenSaved() {
        given(accountRepository.findById(TenantId.FAN_PLATFORM, ACCOUNT_ID))
                .willReturn(Optional.of(unverifiedAccount()));
        given(tokenStore.tryAcquireResendSlot(eq(ACCOUNT_ID), any(Duration.class)))
                .willReturn(true);
        willThrow(new RuntimeException("smtp down"))
                .given(notifier).sendVerificationEmail(eq(EMAIL), anyString());

        // Must not propagate.
        useCase.execute(ACCOUNT_ID);

        verify(tokenStore).save(anyString(), eq(ACCOUNT_ID), any(Duration.class));
        verify(notifier).sendVerificationEmail(eq(EMAIL), anyString());
    }
}
