package com.example.auth.application;

import com.example.auth.application.result.RegisterDeviceSessionResult;
import com.example.auth.domain.repository.DeviceSessionRepository;
import com.example.auth.domain.session.DeviceSession;
import com.example.auth.domain.session.SessionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RegisterOrUpdateDeviceSessionUseCase 단위 테스트")
class RegisterOrUpdateDeviceSessionUseCaseTest {

    @Mock
    private DeviceSessionRepository deviceSessionRepository;

    @Mock
    private EnforceConcurrentLimitUseCase enforceConcurrentLimitUseCase;

    @InjectMocks
    private RegisterOrUpdateDeviceSessionUseCase useCase;

    private static final String ACCOUNT_ID = "acc-reg";

    @Test
    @DisplayName("기존 활성 세션 fingerprint 일치 — touch 후 isNew=false 반환")
    void execute_knownFingerprint_touchesAndReturnsExisting() {
        String fingerprint = "fp-known";
        DeviceSession existing = session("dev-existing", ACCOUNT_ID, fingerprint);
        when(deviceSessionRepository.findActiveByAccountAndFingerprint(ACCOUNT_ID, fingerprint))
                .thenReturn(Optional.of(existing));

        RegisterDeviceSessionResult result = useCase.execute(ACCOUNT_ID, ctx(fingerprint));

        assertThat(result.deviceId()).isEqualTo("dev-existing");
        assertThat(result.newSession()).isFalse();
        assertThat(result.evictedDeviceIds()).isEmpty();
        verify(deviceSessionRepository).save(existing);
        verify(enforceConcurrentLimitUseCase, never()).enforce(anyString());
    }

    @Test
    @DisplayName("fingerprint 없음 (unknown) — 항상 새 세션 생성")
    void execute_unknownFingerprint_alwaysCreatesNew() {
        when(enforceConcurrentLimitUseCase.enforce(ACCOUNT_ID)).thenReturn(List.of());

        RegisterDeviceSessionResult result = useCase.execute(ACCOUNT_ID, ctx(null));

        assertThat(result.newSession()).isTrue();
        verify(deviceSessionRepository, never())
                .findActiveByAccountAndFingerprint(anyString(), anyString());
        verify(deviceSessionRepository).save(any(DeviceSession.class));
    }

    @Test
    @DisplayName("blank fingerprint — unknown 처리 후 새 세션 생성")
    void execute_blankFingerprint_treatedAsUnknown() {
        when(enforceConcurrentLimitUseCase.enforce(ACCOUNT_ID)).thenReturn(List.of());

        RegisterDeviceSessionResult result = useCase.execute(ACCOUNT_ID, ctx("   "));

        assertThat(result.newSession()).isTrue();
        verify(deviceSessionRepository, never())
                .findActiveByAccountAndFingerprint(anyString(), anyString());
    }

    @Test
    @DisplayName("신규 fingerprint — limit 체크 후 새 세션 생성, evicted 목록 반환")
    void execute_newFingerprint_enforcesLimitAndCreatesSession() {
        String fingerprint = "fp-new";
        when(deviceSessionRepository.findActiveByAccountAndFingerprint(ACCOUNT_ID, fingerprint))
                .thenReturn(Optional.empty());
        when(enforceConcurrentLimitUseCase.enforce(ACCOUNT_ID))
                .thenReturn(List.of("dev-evicted"));

        RegisterDeviceSessionResult result = useCase.execute(ACCOUNT_ID, ctx(fingerprint));

        assertThat(result.newSession()).isTrue();
        assertThat(result.evictedDeviceIds()).containsExactly("dev-evicted");
        verify(deviceSessionRepository).save(any(DeviceSession.class));
    }

    private static SessionContext ctx(String fingerprint) {
        return new SessionContext("10.0.0.1", "Mozilla/5.0", fingerprint);
    }

    private static DeviceSession session(String deviceId, String accountId, String fingerprint) {
        Instant now = Instant.now();
        return new DeviceSession(1L, deviceId, accountId, fingerprint, "UA", "1.1.1.1", "KR",
                now.minusSeconds(3600), now.minusSeconds(60), null, null);
    }
}
