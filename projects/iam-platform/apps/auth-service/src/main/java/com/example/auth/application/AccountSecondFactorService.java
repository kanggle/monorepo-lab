package com.example.auth.application;

import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.port.AccountServicePort.EmailVerificationState;
import com.example.auth.application.port.EmailSenderPort;
import com.example.auth.application.port.TotpCodeCalculator;
import com.example.auth.application.port.TotpSecretCipher;
import com.example.auth.domain.mfa.AccountTotp;
import com.example.auth.domain.repository.AccountTotpRepository;
import com.example.security.password.PasswordHasher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * TASK-MONO-771 S2b (ADR-MONO-080 D4 · R3) — the account-plane second factor: enrollment, the login second step,
 * recovery codes. Behaviour = auth-api.md § IdP 브라우저 화면 — 2단계 인증 (TOTP); storage = data-model.md §
 * account_totp.
 *
 * <p>🔴 <b>This service enforces nothing on token issuance.</b> It answers «is there a confirmed enrollment» and
 * «is this code good»; whether a token exchange or an assume-tenant REQUIRES a second factor is S4. An account
 * without a confirmed enrollment is untouched (AC-3).
 *
 * <p><b>Fail-closed reads.</b> Every method that decides whether a person passes the second step propagates a
 * storage failure as an exception — the page renders «지금은 확인할 수 없습니다» and lets nobody through.
 */
@Slf4j
@Service
public class AccountSecondFactorService {

    /** Codes accepted around «now»: the current step and one either side (±30 s clock drift). */
    static final int WINDOW = 1;

    /** How many recovery codes an enrollment holds (auth-api.md: 10, single use). */
    static final int RECOVERY_CODE_COUNT = 10;

    /** Recovery-code alphabet: no 0/O/1/I/L — read off a screen and typed back. */
    private static final char[] RECOVERY_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

    private final AccountTotpRepository repository;
    private final TotpCodeCalculator calculator;
    private final TotpSecretCipher cipher;
    private final PasswordHasher hasher;
    private final AccountServicePort accountServicePort;
    private final EmailSenderPort emailSender;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AccountSecondFactorService(AccountTotpRepository repository, TotpCodeCalculator calculator,
                                      TotpSecretCipher cipher, PasswordHasher hasher,
                                      AccountServicePort accountServicePort, EmailSenderPort emailSender,
                                      Clock clock) {
        this.repository = repository;
        this.calculator = calculator;
        this.cipher = cipher;
        this.hasher = hasher;
        this.accountServicePort = accountServicePort;
        this.emailSender = emailSender;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ status

    /**
     * {@code true} only for a CONFIRMED enrollment — a pending one passes no check (data-model.md). The question
     * the authorize second-factor gate and {@code /mfa/challenge} ask.
     *
     * @throws RuntimeException when the row cannot be read — callers fail closed
     */
    @Transactional(readOnly = true)
    public boolean hasConfirmedEnrollment(String accountId) {
        return repository.findByAccountId(accountId).map(AccountTotp::isConfirmed).orElse(false);
    }

    /** What {@code GET /mfa} shows. */
    @Transactional(readOnly = true)
    public EnrollmentStatus status(String accountId) {
        Optional<AccountTotp> row = repository.findByAccountId(accountId);
        if (row.isEmpty() || !row.get().isConfirmed()) {
            return new EnrollmentStatus(false, 0);
        }
        return new EnrollmentStatus(true, row.get().remainingRecoveryCodes());
    }

    // ------------------------------------------------------------------ enrollment

    /**
     * {@code GET /mfa/setup}: checks the preconditions (owner decision OD-4) and, only if they hold, writes a
     * fresh PENDING secret (replacing an earlier pending one; a confirmed one is never replaced here).
     *
     * <p>Order: an existing confirmed enrollment first (no account-service call needed), then the verified-email
     * predicate — an unverified or unreadable answer creates NO secret.
     */
    @Transactional
    public StartEnrollmentResult startEnrollment(String accountId, String tenantId) {
        Optional<AccountTotp> existing = repository.findByAccountId(accountId);
        if (existing.isPresent() && existing.get().isConfirmed()) {
            return StartEnrollmentResult.of(StartEnrollmentOutcome.ALREADY_ENROLLED);
        }
        EmailVerificationState email;
        try {
            email = accountServicePort.getEmailVerificationState(accountId);
        } catch (AccountServiceUnavailableException e) {
            log.warn("mfa setup: email-verification lookup failed — no enrollment (fail-closed)");
            return StartEnrollmentResult.of(StartEnrollmentOutcome.UNAVAILABLE);
        }
        if (email != EmailVerificationState.VERIFIED) {
            // NOT_VERIFIED, and NOT_APPLICABLE (no account record → no verifiable mailbox) alike: OD-4 asks for
            // a verified mailbox before a second factor can be bound (TOFU defence), and neither has one.
            return StartEnrollmentResult.of(StartEnrollmentOutcome.EMAIL_NOT_VERIFIED);
        }
        Instant now = clock.instant();
        byte[] secret = calculator.newSecret();
        TotpSecretCipher.Sealed sealed = cipher.encrypt(secret, accountId);
        if (existing.isPresent()) {
            repository.deleteByAccountId(accountId); // a stale pending row — replaced, never reused
        }
        repository.save(AccountTotp.pending(accountId, tenantId, sealed.ciphertext(), sealed.keyId(), now));
        return new StartEnrollmentResult(StartEnrollmentOutcome.PENDING_CREATED, calculator.toBase32(secret));
    }

    /**
     * {@code POST /mfa/setup}: the first code from the app confirms the pending secret. On success: confirmed,
     * {@code last_used_step} = the accepted step, ten recovery codes generated (only their Argon2id hashes are
     * stored) and returned ONCE in plain text, and the enrollment notice mailed (best-effort, OD-4).
     */
    @Transactional
    public ConfirmEnrollmentResult confirmEnrollment(String accountId, String code, String noticeEmail) {
        Optional<AccountTotp> row = repository.findByAccountId(accountId);
        if (row.isEmpty() || row.get().isConfirmed() || row.get().isPendingExpired(clock.instant())) {
            return ConfirmEnrollmentResult.rejected();
        }
        AccountTotp totp = row.get();
        Optional<Long> step = matchingFreshStep(totp, code);
        if (step.isEmpty()) {
            return ConfirmEnrollmentResult.rejected();
        }
        List<String> plain = newRecoveryCodes();
        totp.confirm(step.get(), hashAll(plain), clock.instant());
        try {
            repository.save(totp);
        } catch (OptimisticLockingFailureException e) {
            return ConfirmEnrollmentResult.rejected(); // a concurrent confirm won
        }
        sendEnrollmentNotice(noticeEmail);
        return new ConfirmEnrollmentResult(true, plain);
    }

    /**
     * The manual-entry key of a still-alive PENDING enrollment, so a wrong first code can be retried against the
     * same QR (auth-api.md: «같은 QR 로 재입력»). Empty when there is no pending row or it expired.
     */
    @Transactional(readOnly = true)
    public Optional<String> pendingManualKey(String accountId) {
        return repository.findByAccountId(accountId)
                .filter(t -> !t.isConfirmed() && !t.isPendingExpired(clock.instant()))
                .map(t -> calculator.toBase32(
                        cipher.decrypt(t.getSecretCiphertext(), t.getSecretKeyId(), t.getAccountId())));
    }

    // ------------------------------------------------------------------ second step

    /**
     * {@code POST /mfa/challenge} with an authenticator code. Accepted only when it matches a step inside ±1 that
     * is LATER than the last step this enrollment accepted (anti-replay, F5); the counter then moves forward.
     */
    @Transactional
    public VerificationOutcome verifyAuthenticatorCode(String accountId, String code) {
        Optional<AccountTotp> row = repository.findByAccountId(accountId);
        if (row.isEmpty() || !row.get().isConfirmed()) {
            return VerificationOutcome.NOT_ENROLLED;
        }
        AccountTotp totp = row.get();
        Optional<Long> step = matchingFreshStep(totp, code);
        if (step.isEmpty()) {
            return VerificationOutcome.REJECTED;
        }
        totp.recordAuthenticatorSuccess(step.get(), clock.instant());
        reencryptIfStale(totp);
        try {
            repository.save(totp);
        } catch (OptimisticLockingFailureException e) {
            return VerificationOutcome.REJECTED; // the same code submitted twice at once — one wins
        }
        return VerificationOutcome.ACCEPTED;
    }

    /**
     * {@code POST /mfa/challenge} with a recovery code: consumed on success (single use). The result carries how
     * many remain so the page can suggest regenerating (≤ 2).
     */
    @Transactional
    public RecoveryOutcome verifyRecoveryCode(String accountId, String recoveryCode) {
        Optional<AccountTotp> row = repository.findByAccountId(accountId);
        if (row.isEmpty() || !row.get().isConfirmed()) {
            return RecoveryOutcome.rejected();
        }
        String normalized = normalizeRecoveryCode(recoveryCode);
        if (normalized == null) {
            return RecoveryOutcome.rejected();
        }
        AccountTotp totp = row.get();
        List<String> hashes = totp.getRecoveryCodeHashes();
        for (int i = 0; i < hashes.size(); i++) {
            if (hasher.verify(normalized, hashes.get(i))) {
                totp.consumeRecoveryCode(i, clock.instant());
                try {
                    repository.save(totp);
                } catch (OptimisticLockingFailureException e) {
                    return RecoveryOutcome.rejected(); // consumed concurrently — single use holds
                }
                return new RecoveryOutcome(true, totp.remainingRecoveryCodes());
            }
        }
        return RecoveryOutcome.rejected();
    }

    /**
     * {@code POST /mfa/recovery-codes}: ten new codes replace ALL previous ones. The caller has already checked
     * that the session passed the second step.
     *
     * @return the new codes in plain text (shown once), or empty when there is no confirmed enrollment
     */
    @Transactional
    public Optional<List<String>> regenerateRecoveryCodes(String accountId) {
        Optional<AccountTotp> row = repository.findByAccountId(accountId);
        if (row.isEmpty() || !row.get().isConfirmed()) {
            return Optional.empty();
        }
        List<String> plain = newRecoveryCodes();
        AccountTotp totp = row.get();
        totp.replaceRecoveryCodes(hashAll(plain), clock.instant());
        repository.save(totp);
        return Optional.of(plain);
    }

    // ------------------------------------------------------------------ internals

    /**
     * The step inside ±{@link #WINDOW} whose code equals {@code code} AND that this enrollment has not consumed
     * yet. 🔴 The freshness predicate ({@link AccountTotp#isStepFresh}) is what makes a seen code useless.
     */
    private Optional<Long> matchingFreshStep(AccountTotp totp, String code) {
        String candidate = normalizeCode(code);
        if (candidate == null) {
            return Optional.empty();
        }
        byte[] secret = cipher.decrypt(totp.getSecretCiphertext(), totp.getSecretKeyId(), totp.getAccountId());
        long now = TotpCodeCalculator.timeStepOf(clock.instant());
        for (long step = now - WINDOW; step <= now + WINDOW; step++) {
            if (!totp.isStepFresh(step)) {
                continue;
            }
            if (constantTimeEquals(calculator.codeAt(secret, step), candidate)) {
                return Optional.of(step);
            }
        }
        return Optional.empty();
    }

    private void reencryptIfStale(AccountTotp totp) {
        if (cipher.activeKeyId().equals(totp.getSecretKeyId())) {
            return;
        }
        byte[] secret = cipher.decrypt(totp.getSecretCiphertext(), totp.getSecretKeyId(), totp.getAccountId());
        TotpSecretCipher.Sealed sealed = cipher.encrypt(secret, totp.getAccountId());
        totp.reencrypt(sealed.ciphertext(), sealed.keyId(), clock.instant());
    }

    private void sendEnrollmentNotice(String email) {
        if (email == null || email.isBlank()) {
            log.warn("mfa setup: enrollment confirmed but the session carries no email — notice not sent");
            return;
        }
        try {
            emailSender.sendSecondFactorEnrolledNotice(email);
        } catch (RuntimeException e) { // EmailSendException per the port contract, or any adapter surprise
            // OD-4: the notice is detection on top of an enrollment that already required a verified mailbox;
            // its failure never undoes the enrollment. No address in the log (R4).
            log.warn("mfa setup: enrollment notice could not be sent ({}) — enrollment kept",
                    e.getClass().getSimpleName());
        }
    }

    private List<String> newRecoveryCodes() {
        List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            StringBuilder code = new StringBuilder(9);
            for (int c = 0; c < 8; c++) {
                if (c == 4) {
                    code.append('-');
                }
                code.append(RECOVERY_ALPHABET[random.nextInt(RECOVERY_ALPHABET.length)]);
            }
            codes.add(code.toString());
        }
        return codes;
    }

    private List<String> hashAll(List<String> plain) {
        List<String> hashes = new ArrayList<>(plain.size());
        for (String code : plain) {
            hashes.add(hasher.hash(normalizeRecoveryCode(code)));
        }
        return hashes;
    }

    /** Six digits, spaces ignored; anything else is not a code. */
    static String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        String digits = code.replace(" ", "").trim();
        return digits.matches("\\d{6}") ? digits : null;
    }

    /** Case-insensitive, hyphens/spaces ignored — {@code abcd-efgh} and {@code ABCDEFGH} are the same code. */
    static String normalizeRecoveryCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.replace("-", "").replace(" ", "").trim().toUpperCase(Locale.ROOT);
        return normalized.matches("[A-Z0-9]{8}") ? normalized : null;
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                b.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    // ------------------------------------------------------------------ results

    /** {@code GET /mfa}. */
    public record EnrollmentStatus(boolean enrolled, int remainingRecoveryCodes) {
    }

    /** Outcome of {@link #startEnrollment}. */
    public enum StartEnrollmentOutcome { PENDING_CREATED, ALREADY_ENROLLED, EMAIL_NOT_VERIFIED, UNAVAILABLE }

    /**
     * @param base32Secret the manual-entry key — set ONLY for {@link StartEnrollmentOutcome#PENDING_CREATED};
     *                     response body only, never logged (R4)
     */
    public record StartEnrollmentResult(StartEnrollmentOutcome outcome, String base32Secret) {
        static StartEnrollmentResult of(StartEnrollmentOutcome outcome) {
            return new StartEnrollmentResult(outcome, null);
        }
    }

    /** @param recoveryCodes plain text, shown once — empty when not confirmed */
    public record ConfirmEnrollmentResult(boolean confirmed, List<String> recoveryCodes) {
        static ConfirmEnrollmentResult rejected() {
            return new ConfirmEnrollmentResult(false, List.of());
        }
    }

    /** Outcome of {@link #verifyAuthenticatorCode}. */
    public enum VerificationOutcome { ACCEPTED, REJECTED, NOT_ENROLLED }

    /** Outcome of {@link #verifyRecoveryCode}. */
    public record RecoveryOutcome(boolean accepted, int remaining) {
        static RecoveryOutcome rejected() {
            return new RecoveryOutcome(false, 0);
        }
    }
}
