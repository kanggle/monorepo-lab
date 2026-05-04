package com.example.admin.application;

import com.example.admin.application.exception.InvalidTwoFaCodeException;
import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.exception.TotpNotEnrolledException;
import com.example.admin.infrastructure.persistence.AdminOperatorTotpJpaEntity;
import com.example.admin.infrastructure.persistence.AdminOperatorTotpJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import com.example.admin.infrastructure.security.TotpGenerator;
import com.example.admin.infrastructure.security.TotpSecretCipher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.security.password.PasswordHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Business logic for operator TOTP enrollment and verification.
 *
 * <p>Enroll (idempotent re-enroll allowed): generate fresh 160-bit secret,
 * encrypt with active kid, generate 10 recovery codes, Argon2id-hash and
 * persist. Audit row ({@code OPERATOR_2FA_ENROLL}, outcome SUCCESS) is
 * written via {@link AdminActionAuditor}.
 *
 * <p>Verify: decrypt stored secret using the row's kid, verify the submitted
 * code via {@link TotpGenerator}. On success update {@code last_used_at};
 * on failure throw {@link InvalidTwoFaCodeException} (controller emits a
 * FAILURE audit row before re-throwing).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TotpEnrollmentService {

    private static final int RECOVERY_CODE_COUNT = 10;
    private static final char[] RECOVERY_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    private final AdminOperatorJpaRepository operatorRepository;
    private final AdminOperatorTotpJpaRepository totpRepository;
    private final TotpGenerator totpGenerator;
    private final TotpSecretCipher cipher;
    private final PasswordHasher passwordHasher;
    private final ObjectMapper objectMapper;
    private final SecureRandom recoveryRandom;

    @Value("${admin.totp.issuer:admin-service}")
    private String issuer;

    /**
     * Enrolls (or re-enrolls) TOTP for {@code operatorUuid} (external UUID v7).
     * Returns the otpauth URI plus the 10 plaintext recovery codes (one-time).
     */
    @Transactional
    public EnrollmentResult enroll(String operatorUuid) {
        AdminOperatorJpaEntity operator = operatorRepository.findByOperatorId(operatorUuid)
                .orElseThrow(() -> new OperatorNotFoundException(
                        "admin_operators row not found for operatorId=" + operatorUuid));
        long operatorPk = operator.getId();

        byte[] secret = totpGenerator.newSecret();
        byte[] encrypted = cipher.encrypt(secret, operatorPk);

        List<String> plainRecovery = new ArrayList<>(RECOVERY_CODE_COUNT);
        List<String> hashed = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String plain = generateRecoveryCode(recoveryRandom);
            plainRecovery.add(plain);
            hashed.add(passwordHasher.hash(plain));
        }
        String hashedJson;
        try {
            hashedJson = objectMapper.writeValueAsString(hashed);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize recovery code hashes", e);
        }

        Instant now = Instant.now();
        AdminOperatorTotpJpaEntity existing = totpRepository.findById(operatorPk).orElse(null);
        if (existing == null) {
            totpRepository.save(AdminOperatorTotpJpaEntity.create(
                    operatorPk, encrypted, cipher.activeKeyId(), hashedJson, now));
        } else {
            existing.replaceSecret(encrypted, cipher.activeKeyId(), hashedJson, now);
            totpRepository.save(existing);
        }

        String otpauth = totpGenerator.otpauthUri(secret, issuer, operator.getEmail());
        // Zeroise plaintext secret — best effort; JVM may still retain copies.
        java.util.Arrays.fill(secret, (byte) 0);
        return new EnrollmentResult(otpauth, plainRecovery, now);
    }

    /**
     * Regenerates the 10 backup recovery codes for an already-enrolled operator
     * (TASK-BE-113). Existing {@code recovery_codes_hashed} are atomically
     * replaced with hashes of the freshly generated codes — previous codes are
     * invalidated immediately. Plain-text codes are returned to the caller for
     * one-time exposure in the HTTP response and are NOT logged.
     *
     * @param operatorUuid external UUID v7 of the operator (JWT {@code sub}).
     * @return list of 10 plain-text recovery codes ({@code XXXX-XXXX-XXXX}).
     * @throws OperatorNotFoundException if no {@code admin_operators} row exists for the UUID.
     * @throws TotpNotEnrolledException  if the operator has not yet enrolled TOTP.
     */
    @Transactional
    public List<String> regenerateRecoveryCodes(String operatorUuid) {
        AdminOperatorJpaEntity operator = operatorRepository.findByOperatorId(operatorUuid)
                .orElseThrow(() -> new OperatorNotFoundException(
                        "admin_operators row not found for operatorId=" + operatorUuid));
        long operatorPk = operator.getId();

        AdminOperatorTotpJpaEntity row = totpRepository.findById(operatorPk)
                .orElseThrow(() -> new TotpNotEnrolledException(
                        "TOTP not enrolled for operator"));

        List<String> plainRecovery = new ArrayList<>(RECOVERY_CODE_COUNT);
        List<String> hashed = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String plain = generateRecoveryCode(recoveryRandom);
            plainRecovery.add(plain);
            hashed.add(passwordHasher.hash(plain));
        }
        String hashedJson;
        try {
            hashedJson = objectMapper.writeValueAsString(hashed);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize recovery code hashes", e);
        }

        row.replaceRecoveryHashes(hashedJson, Instant.now());
        totpRepository.save(row);

        // Plain-text codes are intentionally NOT logged (R4 compliance).
        return plainRecovery;
    }

    /**
     * Verifies {@code code} against the operator's stored TOTP secret.
     * @throws InvalidTwoFaCodeException if no row exists or the code does not match
     */
    @Transactional
    public void verify(String operatorUuid, String code) {
        AdminOperatorJpaEntity operator = operatorRepository.findByOperatorId(operatorUuid)
                .orElseThrow(() -> new InvalidTwoFaCodeException("2FA not enrolled"));
        AdminOperatorTotpJpaEntity row = totpRepository.findById(operator.getId()).orElse(null);
        if (row == null) {
            throw new InvalidTwoFaCodeException("2FA not enrolled");
        }
        byte[] secret = cipher.decrypt(row.getSecretEncrypted(), operator.getId(), row.getSecretKeyId());
        try {
            if (!totpGenerator.verify(secret, code)) {
                throw new InvalidTwoFaCodeException("TOTP code does not match");
            }
            row.markUsed(Instant.now());
            totpRepository.save(row);
        } finally {
            java.util.Arrays.fill(secret, (byte) 0);
        }
    }

    /** Exposed for rare diagnostic/test paths; do not read in production code. */
    @SuppressWarnings("unused")
    List<String> parseRecoveryHashes(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Malformed recovery_codes_hashed JSON", e);
        }
    }

    private static String generateRecoveryCode(SecureRandom random) {
        char[] buf = new char[14]; // 4-4-4 with two dashes
        for (int i = 0; i < 14; i++) {
            if (i == 4 || i == 9) {
                buf[i] = '-';
            } else {
                buf[i] = RECOVERY_ALPHABET[random.nextInt(RECOVERY_ALPHABET.length)];
            }
        }
        return new String(buf);
    }

    public record EnrollmentResult(String otpauthUri, List<String> recoveryCodes, Instant enrolledAt) {}
}
