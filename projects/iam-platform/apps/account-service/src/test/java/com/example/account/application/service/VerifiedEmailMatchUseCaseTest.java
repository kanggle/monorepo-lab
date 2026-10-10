package com.example.account.application.service;

import com.example.account.application.exception.AccountEmailMismatchException;
import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.exception.EmailNotVerifiedException;
import com.example.account.application.result.VerifiedEmailMatchResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.tenant.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * TASK-MONO-772 S2 — {@code verified-email:match} (admin-to-account.md). 🔴 The control group is the same pool
 * account and the same expected address: only the verification state differs, and only the verified one matches.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VerifiedEmailMatchUseCase — 인증된 이메일 일치 판정 (TASK-MONO-772 S2)")
class VerifiedEmailMatchUseCaseTest {

    private static final String ACCOUNT = "0199de70-0000-7000-8000-000000000772";
    private static final String EMAIL = "person@example.com";
    private static final Instant VERIFIED_AT = Instant.parse("2026-10-09T10:00:00Z");

    @Mock AccountRepository accountRepository;
    @InjectMocks VerifiedEmailMatchUseCase useCase;

    private static Account account(AccountStatus status, String email, Instant verifiedAt) {
        return Account.reconstitute(ACCOUNT, TenantId.CONSUMER_POOL, email, null, status,
                Instant.EPOCH, Instant.EPOCH, null, null, verifiedAt, 0);
    }

    private void poolAccount(Account a) {
        given(accountRepository.findById(TenantId.CONSUMER_POOL, ACCOUNT)).willReturn(Optional.of(a));
    }

    @Test
    @DisplayName("🔴 대조군: 같은 계정 · 같은 주소 — 미인증은 403 EMAIL_NOT_VERIFIED, 인증되면 일치(인증 시각)")
    void controlGroup_onlyVerifiedMatches() {
        poolAccount(account(AccountStatus.ACTIVE, EMAIL, null));
        assertThatThrownBy(() -> useCase.match(ACCOUNT, EMAIL)).isInstanceOf(EmailNotVerifiedException.class);

        poolAccount(account(AccountStatus.ACTIVE, EMAIL, VERIFIED_AT));
        VerifiedEmailMatchResult r = useCase.match(ACCOUNT, "  PERSON@Example.com ");
        assertThat(r.accountId()).isEqualTo(ACCOUNT);
        assertThat(r.emailVerifiedAt()).isEqualTo(VERIFIED_AT);
    }

    @Test
    @DisplayName("풀에 없음(없는 id · 사이트/B2B 계정) → 404 — 풀 테넌트로만 찾는다")
    void notInPool_404() {
        given(accountRepository.findById(TenantId.CONSUMER_POOL, ACCOUNT)).willReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.match(ACCOUNT, EMAIL)).isInstanceOf(AccountNotFoundException.class);
        verify(accountRepository).findById(TenantId.CONSUMER_POOL, ACCOUNT);
        verifyNoMoreInteractions(accountRepository); // no tenant-less lookup, no other tenant tried
    }

    @Test
    @DisplayName("풀 계정이지만 ACTIVE 아님(LOCKED) → 404 — 인증·이메일과 무관하게 같은 답")
    void notActive_404() {
        poolAccount(account(AccountStatus.LOCKED, EMAIL, VERIFIED_AT));
        assertThatThrownBy(() -> useCase.match(ACCOUNT, EMAIL)).isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("다른 이메일 → 403 ACCOUNT_EMAIL_MISMATCH — 미인증이어도 불일치가 먼저(더 구체적인 답)")
    void mismatch_beforeUnverified() {
        poolAccount(account(AccountStatus.ACTIVE, EMAIL, null));
        assertThatThrownBy(() -> useCase.match(ACCOUNT, "someone-else@example.com"))
                .isInstanceOf(AccountEmailMismatchException.class)
                .hasMessageNotContaining(EMAIL).hasMessageNotContaining("someone-else");
    }
}
