package com.example.account.application.service;

import com.example.account.application.exception.EmailAlreadyVerifiedException;
import com.example.account.application.exception.EmailVerificationTokenInvalidException;
import com.example.account.application.result.VerifyEmailResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.EmailVerificationTokenStore;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.tenant.TenantId;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link VerifyEmailUseCase} (TASK-BE-114).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VerifyEmailUseCase 단위 테스트")
class VerifyEmailUseCaseTest {

    private static final String TOKEN = "verify-token-uuid";
    private static final String ACCOUNT_ID = "acc-1";
    private static final String EMAIL = "user@example.com";

    @Mock
    private EmailVerificationTokenStore tokenStore;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private VerifyEmailUseCase useCase;

    private Account unverifiedAccount() {
        return Account.reconstitute(
                ACCOUNT_ID, TenantId.FAN_PLATFORM, EMAIL, null,
                AccountStatus.ACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                null, null, null, 0
        );
    }

    private Account verifiedAccount(Instant verifiedAt) {
        return Account.reconstitute(
                ACCOUNT_ID, TenantId.FAN_PLATFORM, EMAIL, null,
                AccountStatus.ACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                null, null, verifiedAt, 0
        );
    }

    @Test
    @DisplayName("정상 인증 — 토큰 조회 → verifyEmail → save → token delete 순서, emailVerifiedAt 반환")
    void execute_validToken_verifiesAndDeletesToken() {
        given(tokenStore.findAccountId(TOKEN)).willReturn(Optional.of(ACCOUNT_ID));
        given(accountRepository.findById(TenantId.FAN_PLATFORM, ACCOUNT_ID)).willReturn(Optional.of(unverifiedAccount()));
        given(accountRepository.save(any(Account.class))).willAnswer(inv -> inv.getArgument(0));

        VerifyEmailResult result = useCase.execute(TOKEN);

        assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(result.emailVerifiedAt()).isNotNull();

        // Captured account must have emailVerifiedAt set after save.
        ArgumentCaptor<Account> savedCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getEmailVerifiedAt()).isNotNull();

        // Critical ordering: account.save MUST happen before token delete so
        // a transient delete failure does not leave us with no token + no flag.
        InOrder order = inOrder(tokenStore, accountRepository);
        order.verify(tokenStore).findAccountId(TOKEN);
        order.verify(accountRepository).findById(TenantId.FAN_PLATFORM, ACCOUNT_ID);
        order.verify(accountRepository).save(any(Account.class));
        order.verify(tokenStore).delete(TOKEN);
    }

    @Test
    @DisplayName("토큰 미존재 — EmailVerificationTokenInvalidException, account 조회/save/delete 모두 호출 안 됨")
    void execute_unknownToken_throwsAndHasNoSideEffects() {
        given(tokenStore.findAccountId(TOKEN)).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(TOKEN))
                .isInstanceOf(EmailVerificationTokenInvalidException.class);

        verify(accountRepository, never()).findById(any(), anyString());
        verify(accountRepository, never()).save(any());
        verify(tokenStore, never()).delete(anyString());
    }

    @Test
    @DisplayName("토큰은 유효하나 account 가 사라진 경우 동일하게 EmailVerificationTokenInvalidException")
    void execute_accountVanished_throwsAndDoesNotDeleteToken() {
        given(tokenStore.findAccountId(TOKEN)).willReturn(Optional.of(ACCOUNT_ID));
        given(accountRepository.findById(TenantId.FAN_PLATFORM, ACCOUNT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(TOKEN))
                .isInstanceOf(EmailVerificationTokenInvalidException.class);

        verify(accountRepository, never()).save(any());
        // Token must NOT be deleted — keeps response uniform with the unknown-token path.
        verify(tokenStore, never()).delete(anyString());
    }

    @Test
    @DisplayName("이미 인증된 계정 — EmailAlreadyVerifiedException, save/delete 호출 안 됨")
    void execute_alreadyVerified_throwsEmailAlreadyVerified() {
        Instant priorVerification = Instant.parse("2026-04-01T00:00:00Z");
        given(tokenStore.findAccountId(TOKEN)).willReturn(Optional.of(ACCOUNT_ID));
        given(accountRepository.findById(TenantId.FAN_PLATFORM, ACCOUNT_ID))
                .willReturn(Optional.of(verifiedAccount(priorVerification)));

        assertThatThrownBy(() -> useCase.execute(TOKEN))
                .isInstanceOf(EmailAlreadyVerifiedException.class);

        verify(accountRepository, never()).save(any());
        // Do not delete the token — leaves the resend pathway clean and the
        // 409 response idempotent.
        verify(tokenStore, never()).delete(anyString());
    }
}
