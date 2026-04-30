package com.example.admin.application;

import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.exception.TotpNotEnrolledException;
import com.example.admin.infrastructure.persistence.AdminOperatorTotpJpaEntity;
import com.example.admin.infrastructure.persistence.AdminOperatorTotpJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import com.example.admin.infrastructure.security.TotpGenerator;
import com.example.admin.infrastructure.security.TotpSecretCipher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gap.security.password.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the parts of {@link TotpEnrollmentService} that can be
 * validated without a live database — specifically the new constructor-
 * injected {@link SecureRandom} (deterministic recovery-code generation) and
 * the missing-operator path now raising {@link OperatorNotFoundException}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TotpEnrollmentServiceTest {

    @Mock AdminOperatorJpaRepository operatorRepository;
    @Mock AdminOperatorTotpJpaRepository totpRepository;
    @Mock TotpGenerator totpGenerator;
    @Mock TotpSecretCipher cipher;
    @Mock PasswordHasher passwordHasher;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Deterministic {@link SecureRandom} using SHA1PRNG with a fixed seed.
     * {@code new SecureRandom(byte[])} mixes the default entropy source so
     * it is NOT deterministic; the SHA1PRNG + setSeed pattern is.
     */
    private static SecureRandom deterministicRandom() {
        try {
            SecureRandom r = SecureRandom.getInstance("SHA1PRNG");
            r.setSeed(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
            return r;
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private TotpEnrollmentService newService(SecureRandom random) {
        return new TotpEnrollmentService(
                operatorRepository, totpRepository, totpGenerator, cipher, passwordHasher, mapper, random);
    }

    @BeforeEach
    void setup() {
        when(totpGenerator.newSecret()).thenReturn(new byte[20]);
        when(cipher.encrypt(any(), anyLong())).thenReturn(new byte[]{1, 2, 3});
        when(cipher.activeKeyId()).thenReturn("v1");
        when(passwordHasher.hash(anyString())).thenAnswer(inv -> "h:" + inv.getArgument(0));
        when(totpGenerator.otpauthUri(any(), anyString(), anyString())).thenReturn("otpauth://totp/x");
        when(totpRepository.findById(anyLong())).thenReturn(Optional.empty());
    }

    @Test
    void deterministicSecureRandomProducesDeterministicRecoveryCodes() {
        AdminOperatorJpaEntity operator = mock(AdminOperatorJpaEntity.class);
        when(operator.getId()).thenReturn(42L);
        when(operator.getEmail()).thenReturn("op@example.com");
        when(operatorRepository.findByOperatorId("op-uuid")).thenReturn(Optional.of(operator));

        TotpEnrollmentService svc1 = newService(deterministicRandom());
        TotpEnrollmentService.EnrollmentResult r1 = svc1.enroll("op-uuid");

        TotpEnrollmentService svc2 = newService(deterministicRandom());
        TotpEnrollmentService.EnrollmentResult r2 = svc2.enroll("op-uuid");

        assertThat(r1.recoveryCodes()).hasSize(10);
        assertThat(r1.recoveryCodes()).isEqualTo(r2.recoveryCodes());
        // Format: 4-4-4 with dashes at index 4 and 9
        for (String code : r1.recoveryCodes()) {
            assertThat(code).hasSize(14);
            assertThat(code.charAt(4)).isEqualTo('-');
            assertThat(code.charAt(9)).isEqualTo('-');
        }
    }

    @Test
    void enrollMissingOperatorRaisesOperatorNotFound() {
        when(operatorRepository.findByOperatorId("missing")).thenReturn(Optional.empty());

        TotpEnrollmentService svc = newService(new SecureRandom(new byte[]{0}));
        assertThatThrownBy(() -> svc.enroll("missing"))
                .isInstanceOf(OperatorNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void regenerateRecoveryCodes_returns10Codes_andReplacesHashes() {
        AdminOperatorJpaEntity operator = mock(AdminOperatorJpaEntity.class);
        when(operator.getId()).thenReturn(42L);
        when(operatorRepository.findByOperatorId("op-uuid")).thenReturn(Optional.of(operator));

        AdminOperatorTotpJpaEntity totpRow = mock(AdminOperatorTotpJpaEntity.class);
        when(totpRepository.findById(42L)).thenReturn(Optional.of(totpRow));
        when(totpRepository.save(totpRow)).thenReturn(totpRow);

        TotpEnrollmentService svc = newService(deterministicRandom());
        List<String> codes = svc.regenerateRecoveryCodes("op-uuid");

        assertThat(codes).hasSize(10);
        for (String code : codes) {
            assertThat(code).hasSize(14);
            assertThat(code.charAt(4)).isEqualTo('-');
            assertThat(code.charAt(9)).isEqualTo('-');
        }
        assertThat(new HashSet<>(codes)).hasSize(10);
        verify(totpRow).replaceRecoveryHashes(anyString(), any(Instant.class));
        verify(totpRepository).save(totpRow);
        verify(passwordHasher, times(10)).hash(anyString());
    }

    @Test
    void regenerateRecoveryCodes_totpNotEnrolled_throwsTotpNotEnrolledException() {
        AdminOperatorJpaEntity operator = mock(AdminOperatorJpaEntity.class);
        when(operator.getId()).thenReturn(99L);
        when(operatorRepository.findByOperatorId("unenrolled")).thenReturn(Optional.of(operator));
        when(totpRepository.findById(99L)).thenReturn(Optional.empty());

        TotpEnrollmentService svc = newService(new SecureRandom(new byte[]{0}));
        assertThatThrownBy(() -> svc.regenerateRecoveryCodes("unenrolled"))
                .isInstanceOf(TotpNotEnrolledException.class);
    }
}

class TotpEnrollmentServiceEntityFactories {
    // Reserved — intentionally left blank. AdminOperatorJpaEntity requires a
    // protected no-args ctor; Mockito stubs suffice in the tests above.
}
