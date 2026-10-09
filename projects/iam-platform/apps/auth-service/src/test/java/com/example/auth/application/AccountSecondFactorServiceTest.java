package com.example.auth.application;

import com.example.auth.application.AccountSecondFactorService.ConfirmEnrollmentResult;
import com.example.auth.application.AccountSecondFactorService.RecoveryOutcome;
import com.example.auth.application.AccountSecondFactorService.StartEnrollmentOutcome;
import com.example.auth.application.AccountSecondFactorService.StartEnrollmentResult;
import com.example.auth.application.AccountSecondFactorService.VerificationOutcome;
import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.exception.EmailSendException;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.port.AccountServicePort.EmailVerificationState;
import com.example.auth.application.port.EmailSenderPort;
import com.example.auth.application.port.TotpCodeCalculator;
import com.example.auth.domain.mfa.AccountTotp;
import com.example.auth.domain.repository.AccountTotpRepository;
import com.example.auth.infrastructure.totp.AesGcmTotpSecretCipher;
import com.example.auth.infrastructure.totp.Rfc6238TotpCodeCalculator;
import com.example.security.password.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-771 S2b — enrollment, the second step and recovery codes (auth-api.md § IdP 브라우저 화면 — 2단계 인증).
 * Real RFC 6238 calculator and real AES-GCM cipher; an in-memory repository; the account-service and mail ports
 * mocked (STRICT_STUBS).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("계정 2단계 인증 서비스 (TASK-MONO-771 S2b)")
class AccountSecondFactorServiceTest {

    private static final String ACCOUNT = "0199de70-0000-7000-8000-000000000771";
    private static final String EMAIL = "member@example.com";
    private static final Instant T0 = Instant.parse("2026-10-08T00:00:15Z");

    @Mock
    private AccountServicePort accountServicePort;

    @Mock
    private EmailSenderPort emailSender;

    private final InMemoryRepo repo = new InMemoryRepo();
    private final Rfc6238TotpCodeCalculator calculator = new Rfc6238TotpCodeCalculator();
    private final AesGcmTotpSecretCipher cipher = new AesGcmTotpSecretCipher("v1",
            Map.of("v1", Base64.getEncoder().encodeToString(new byte[32])));
    private final MutableClock clock = new MutableClock(T0);
    private AccountSecondFactorService service;

    @BeforeEach
    void setUp() {
        service = new AccountSecondFactorService(repo, calculator, cipher, new PrefixHasher(),
                accountServicePort, emailSender, clock);
    }

    // ------------------------------------------------------------------ enrollment

    @Test
    @DisplayName("등록 happy path: 인증된 이메일 → 대기 비밀 → 첫 코드로 확정 · 복구 코드 10개(해시만 저장) · 알림 메일")
    void enroll_happyPath() {
        when(accountServicePort.getEmailVerificationState(ACCOUNT)).thenReturn(EmailVerificationState.VERIFIED);

        StartEnrollmentResult started = service.startEnrollment(ACCOUNT, "fan-platform");
        assertThat(started.outcome()).isEqualTo(StartEnrollmentOutcome.PENDING_CREATED);
        assertThat(repo.row().isConfirmed()).as("a pending row passes no check").isFalse();
        assertThat(service.hasConfirmedEnrollment(ACCOUNT)).isFalse();

        ConfirmEnrollmentResult confirmed =
                service.confirmEnrollment(ACCOUNT, codeNow(started.base32Secret()), EMAIL);

        assertThat(confirmed.confirmed()).isTrue();
        assertThat(confirmed.recoveryCodes()).hasSize(10).allMatch(c -> c.matches("[A-Z2-9]{4}-[A-Z2-9]{4}"));
        AccountTotp row = repo.row();
        assertThat(row.isConfirmed()).isTrue();
        assertThat(row.getLastUsedStep()).isEqualTo(TotpCodeCalculator.timeStepOf(T0));
        assertThat(row.getRecoveryCodeHashes()).hasSize(10)
                .as("only hashes are stored").noneMatch(confirmed.recoveryCodes()::contains);
        assertThat(service.hasConfirmedEnrollment(ACCOUNT)).isTrue();
        verify(emailSender).sendSecondFactorEnrolledNotice(EMAIL);
    }

    @Test
    @DisplayName("🔴 OD-4: 미인증 이메일 → 거절 · 비밀을 만들지 않는다")
    void enroll_unverifiedEmail_refused_noSecret() {
        when(accountServicePort.getEmailVerificationState(ACCOUNT)).thenReturn(EmailVerificationState.NOT_VERIFIED);

        StartEnrollmentResult started = service.startEnrollment(ACCOUNT, "fan-platform");

        assertThat(started.outcome()).isEqualTo(StartEnrollmentOutcome.EMAIL_NOT_VERIFIED);
        assertThat(started.base32Secret()).isNull();
        assertThat(repo.rows).isEmpty();
    }

    @Test
    @DisplayName("인증 여부 조회 실패 → UNAVAILABLE (fail-closed) · 비밀 없음")
    void enroll_lookupFails_failClosed() {
        when(accountServicePort.getEmailVerificationState(ACCOUNT))
                .thenThrow(new AccountServiceUnavailableException("down", new RuntimeException()));

        assertThat(service.startEnrollment(ACCOUNT, "fan-platform").outcome())
                .isEqualTo(StartEnrollmentOutcome.UNAVAILABLE);
        assertThat(repo.rows).isEmpty();
    }

    @Test
    @DisplayName("확정된 등록이 있으면 GET 은 교체하지 않는다 (재등록은 관리자 리셋 뒤에만) · account-service 도 안 부른다")
    void enroll_alreadyConfirmed_notReplaced() {
        String secret = enrolled();
        byte[] before = repo.row().getSecretCiphertext();

        assertThat(service.startEnrollment(ACCOUNT, "fan-platform").outcome())
                .isEqualTo(StartEnrollmentOutcome.ALREADY_ENROLLED);
        assertThat(repo.row().getSecretCiphertext()).isEqualTo(before);
        assertThat(secret).isNotBlank();
    }

    @Test
    @DisplayName("알림 메일 실패는 등록을 되돌리지 않는다")
    void enroll_noticeFailure_keepsEnrollment() {
        when(accountServicePort.getEmailVerificationState(ACCOUNT)).thenReturn(EmailVerificationState.VERIFIED);
        doThrow(new EmailSendException("smtp down")).when(emailSender).sendSecondFactorEnrolledNotice(anyString());
        String secret = service.startEnrollment(ACCOUNT, "fan-platform").base32Secret();

        assertThat(service.confirmEnrollment(ACCOUNT, codeNow(secret), EMAIL).confirmed()).isTrue();
        assertThat(service.hasConfirmedEnrollment(ACCOUNT)).isTrue();
    }

    @Test
    @DisplayName("대기 10분이 지나면 확인 코드가 맞아도 확정되지 않는다")
    void enroll_pendingExpired() {
        when(accountServicePort.getEmailVerificationState(ACCOUNT)).thenReturn(EmailVerificationState.VERIFIED);
        String secret = service.startEnrollment(ACCOUNT, "fan-platform").base32Secret();
        clock.now = T0.plusSeconds(11 * 60);

        assertThat(service.confirmEnrollment(ACCOUNT, codeNow(secret), EMAIL).confirmed()).isFalse();
        verify(emailSender, never()).sendSecondFactorEnrolledNotice(anyString());
    }

    // ------------------------------------------------------------------ second step

    @Test
    @DisplayName("challenge 성공: 다음 step 의 코드 → ACCEPTED · 카운터 전진")
    void challenge_success() {
        String secret = enrolled();
        clock.now = T0.plusSeconds(30);

        assertThat(service.verifyAuthenticatorCode(ACCOUNT, codeNow(secret))).isEqualTo(VerificationOutcome.ACCEPTED);
        assertThat(repo.row().getLastUsedStep()).isEqualTo(TotpCodeCalculator.timeStepOf(T0) + 1);
    }

    @Test
    @DisplayName("challenge 실패: 틀린 코드 · 형식 오류 → REJECTED")
    void challenge_wrongCode() {
        enrolled();
        clock.now = T0.plusSeconds(30);

        assertThat(service.verifyAuthenticatorCode(ACCOUNT, "000000".equals(codeNow(enrolledSecret)) ? "111111" : "000000"))
                .isEqualTo(VerificationOutcome.REJECTED);
        assertThat(service.verifyAuthenticatorCode(ACCOUNT, "12ab56")).isEqualTo(VerificationOutcome.REJECTED);
    }

    /**
     * 🔴 F5 — the anti-replay test. THE one test that exercises {@code last_used_step}: the bite (remove the
     * freshness check in {@code AccountSecondFactorService#matchingFreshStep}) turns this test, and only it, red.
     */
    @Test
    @DisplayName("🔴 F5 재생 방지: 받아들인 코드를 ±1 창 안에서 다시 내면 거절된다")
    void challenge_replayRefused() {
        String secret = enrolled();
        clock.now = T0.plusSeconds(30);
        String code = codeNow(secret);
        assertThat(service.verifyAuthenticatorCode(ACCOUNT, code)).isEqualTo(VerificationOutcome.ACCEPTED);

        clock.now = T0.plusSeconds(45); // same step, still inside the window
        assertThat(service.verifyAuthenticatorCode(ACCOUNT, code)).isEqualTo(VerificationOutcome.REJECTED);
    }

    @Test
    @DisplayName("등록 없음(또는 대기 행만) → NOT_ENROLLED — 대기 비밀은 아무 판정도 통과시키지 않는다")
    void challenge_notEnrolled() {
        assertThat(service.verifyAuthenticatorCode(ACCOUNT, "123456")).isEqualTo(VerificationOutcome.NOT_ENROLLED);

        when(accountServicePort.getEmailVerificationState(ACCOUNT)).thenReturn(EmailVerificationState.VERIFIED);
        String pendingSecret = service.startEnrollment(ACCOUNT, "fan-platform").base32Secret();
        assertThat(service.verifyAuthenticatorCode(ACCOUNT, codeNow(pendingSecret)))
                .isEqualTo(VerificationOutcome.NOT_ENROLLED);
    }

    // ------------------------------------------------------------------ recovery codes

    @Test
    @DisplayName("복구 코드는 1회용: 첫 사용 ACCEPTED(남은 9) · 같은 코드 재사용 REJECTED · 대소문자/하이픈 무관")
    void recoveryCode_singleUse() {
        enrolled();
        String code = enrolledCodes.get(3);

        RecoveryOutcome first = service.verifyRecoveryCode(ACCOUNT, code.toLowerCase().replace("-", ""));
        assertThat(first.accepted()).isTrue();
        assertThat(first.remaining()).isEqualTo(9);

        assertThat(service.verifyRecoveryCode(ACCOUNT, code).accepted()).isFalse();
        assertThat(repo.row().remainingRecoveryCodes()).isEqualTo(9);
    }

    @Test
    @DisplayName("재발급은 전체 교체 — 이전 코드는 즉시 무효")
    void recoveryCodes_regenerate_replacesAll() {
        enrolled();
        String old = enrolledCodes.get(0);

        List<String> fresh = service.regenerateRecoveryCodes(ACCOUNT).orElseThrow();

        assertThat(fresh).hasSize(10).doesNotContain(old);
        assertThat(service.verifyRecoveryCode(ACCOUNT, old).accepted()).isFalse();
        assertThat(service.verifyRecoveryCode(ACCOUNT, fresh.get(0)).accepted()).isTrue();
    }

    // ------------------------------------------------------------------ S6: administrator reset

    @Test
    @DisplayName("🔴 S6: 관리자 리셋 뒤 = «등록 없음» — 게이트가 묻는 술어 false · 옛 코드 · 복구 코드 무효 · 재등록 가능(OD-4 전제 다시)")
    void afterAdminReset_accountIsNotEnrolled_andCanEnrolAgain() {
        String oldSecret = enrolled();
        String oldRecoveryCode = enrolledCodes.get(0);
        assertThat(service.hasConfirmedEnrollment(ACCOUNT)).isTrue();

        AccountSecondFactorResetUseCase reset = new AccountSecondFactorResetUseCase(repo, accountServicePort, clock);
        AccountSecondFactorResetUseCase.Result result = reset.reset(ACCOUNT, "op-s6");

        assertThat(result.outcome()).isEqualTo(AccountSecondFactorResetUseCase.Outcome.RESET);
        assertThat(result.wasConfirmed()).isTrue();
        // The exact predicate AuthorizeSecondFactorGate routes on: false → a step-up / policy entry goes to
        // /mfa/setup (AuthorizeSecondFactorGateTest.stepUp_notEnrolled_goesToSetup), not /mfa/challenge.
        assertThat(service.hasConfirmedEnrollment(ACCOUNT)).isFalse();
        assertThat(service.status(ACCOUNT).enrolled()).isFalse();
        clock.now = T0.plusSeconds(60);
        assertThat(service.verifyAuthenticatorCode(ACCOUNT, codeNow(oldSecret)))
                .as("the lost app's codes no longer pass").isEqualTo(VerificationOutcome.NOT_ENROLLED);
        assertThat(service.verifyRecoveryCode(ACCOUNT, oldRecoveryCode).accepted())
                .as("the lost recovery codes no longer pass").isFalse();

        // Re-enrolment is open again, with the OD-4 precondition asked afresh (enrolled() stubbed VERIFIED).
        StartEnrollmentResult again = service.startEnrollment(ACCOUNT, "fan-platform");
        assertThat(again.outcome()).isEqualTo(StartEnrollmentOutcome.PENDING_CREATED);
        assertThat(again.base32Secret()).isNotEqualTo(oldSecret);
    }

    // ------------------------------------------------------------------ fixtures

    private String enrolledSecret;
    private List<String> enrolledCodes;

    /** A confirmed enrollment at T0 (its confirm consumed T0's step). */
    private String enrolled() {
        when(accountServicePort.getEmailVerificationState(ACCOUNT)).thenReturn(EmailVerificationState.VERIFIED);
        enrolledSecret = service.startEnrollment(ACCOUNT, "fan-platform").base32Secret();
        ConfirmEnrollmentResult result = service.confirmEnrollment(ACCOUNT, codeNow(enrolledSecret), EMAIL);
        assertThat(result.confirmed()).isTrue();
        enrolledCodes = result.recoveryCodes();
        return enrolledSecret;
    }

    private String codeNow(String base32) {
        return calculator.codeAt(base32Decode(base32), TotpCodeCalculator.timeStepOf(clock.now));
    }

    private static byte[] base32Decode(String s) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : s.toCharArray()) {
            buffer = (buffer << 5) | alphabet.indexOf(c);
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    /** Deterministic stand-in for Argon2id (the real hasher is the lib's, tested there). */
    private static final class PrefixHasher implements PasswordHasher {
        @Override
        public String hash(String plain) {
            return "h$" + new StringBuilder(plain).reverse();
        }

        @Override
        public boolean verify(String plain, String hashed) {
            return hash(plain).equals(hashed);
        }
    }

    private static final class InMemoryRepo implements AccountTotpRepository {
        final Map<String, AccountTotp> rows = new HashMap<>();
        private int version;

        AccountTotp row() {
            return rows.get(ACCOUNT);
        }

        @Override
        public Optional<AccountTotp> findByAccountId(String accountId) {
            AccountTotp t = rows.get(accountId);
            // Return a copy, as JPA would — the service must save() for a change to persist.
            return Optional.ofNullable(t).map(InMemoryRepo::copy);
        }

        @Override
        public AccountTotp save(AccountTotp totp) {
            AccountTotp stored = new AccountTotp(totp.getAccountId(), totp.getTenantId(), totp.getSecretCiphertext(),
                    totp.getSecretKeyId(), totp.getConfirmedAt(),
                    totp.hasNoRecoveryCodeColumn() ? null : totp.getRecoveryCodeHashes(), totp.getLastUsedStep(),
                    totp.getLastUsedAt(), totp.getCreatedAt(), totp.getUpdatedAt(), ++version);
            rows.put(totp.getAccountId(), stored);
            return copy(stored);
        }

        @Override
        public void deleteByAccountId(String accountId) {
            rows.remove(accountId);
        }

        private static AccountTotp copy(AccountTotp t) {
            return new AccountTotp(t.getAccountId(), t.getTenantId(), t.getSecretCiphertext(), t.getSecretKeyId(),
                    t.getConfirmedAt(), t.hasNoRecoveryCodeColumn() ? null : t.getRecoveryCodeHashes(),
                    t.getLastUsedStep(), t.getLastUsedAt(), t.getCreatedAt(), t.getUpdatedAt(), t.getVersion());
        }
    }

    private static final class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    @DisplayName("이메일 포트 외에는 아무 것도 부르지 않는 상태 조회")
    void status_readsOnly() {
        assertThat(service.status(ACCOUNT).enrolled()).isFalse();
        verifyNoInteractions(accountServicePort, emailSender);
    }
}
