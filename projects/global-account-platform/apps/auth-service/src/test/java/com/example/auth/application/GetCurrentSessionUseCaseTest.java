package com.example.auth.application;

import com.example.auth.application.exception.SessionNotFoundException;
import com.example.auth.application.exception.SessionOwnershipMismatchException;
import com.example.auth.application.result.DeviceSessionResult;
import com.example.auth.domain.repository.DeviceSessionRepository;
import com.example.auth.domain.session.DeviceSession;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetCurrentSessionUseCase 단위 테스트")
class GetCurrentSessionUseCaseTest {

    @Mock
    private DeviceSessionRepository deviceSessionRepository;

    @InjectMocks
    private GetCurrentSessionUseCase useCase;

    private static final String ACCOUNT_ID = "acc-001";
    private static final String DEVICE_ID = "dev-001";

    @Test
    @DisplayName("정상 세션 — DeviceSessionResult 반환 (current=true)")
    void execute_activeSession_returnsResult() {
        DeviceSession session = activeSession(DEVICE_ID, ACCOUNT_ID);
        when(deviceSessionRepository.findByDeviceId(DEVICE_ID)).thenReturn(Optional.of(session));

        DeviceSessionResult result = useCase.execute(ACCOUNT_ID, DEVICE_ID);

        assertThat(result.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(result.current()).isTrue();
    }

    @Test
    @DisplayName("blank deviceId — SessionNotFoundException")
    void execute_blankDeviceId_throwsSessionNotFound() {
        assertThatThrownBy(() -> useCase.execute(ACCOUNT_ID, "  "))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    @DisplayName("null deviceId — SessionNotFoundException")
    void execute_nullDeviceId_throwsSessionNotFound() {
        assertThatThrownBy(() -> useCase.execute(ACCOUNT_ID, null))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    @DisplayName("세션 미존재 — SessionNotFoundException")
    void execute_sessionNotFound_throwsSessionNotFound() {
        when(deviceSessionRepository.findByDeviceId(DEVICE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ACCOUNT_ID, DEVICE_ID))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    @DisplayName("타 계정 세션 조회 — SessionOwnershipMismatchException")
    void execute_crossAccountSession_throwsOwnershipMismatch() {
        DeviceSession session = activeSession(DEVICE_ID, "other-account");
        when(deviceSessionRepository.findByDeviceId(DEVICE_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> useCase.execute(ACCOUNT_ID, DEVICE_ID))
                .isInstanceOf(SessionOwnershipMismatchException.class);
    }

    @Test
    @DisplayName("revoked 세션 — SessionNotFoundException")
    void execute_revokedSession_throwsSessionNotFound() {
        DeviceSession session = revokedSession(DEVICE_ID, ACCOUNT_ID);
        when(deviceSessionRepository.findByDeviceId(DEVICE_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> useCase.execute(ACCOUNT_ID, DEVICE_ID))
                .isInstanceOf(SessionNotFoundException.class);
    }

    private static DeviceSession activeSession(String deviceId, String accountId) {
        Instant now = Instant.now();
        return new DeviceSession(1L, deviceId, accountId, "fp", "UA", "1.1.1.1", "KR",
                now.minusSeconds(3600), now.minusSeconds(60), null, null);
    }

    private static DeviceSession revokedSession(String deviceId, String accountId) {
        Instant now = Instant.now();
        return new DeviceSession(2L, deviceId, accountId, "fp", "UA", "1.1.1.1", "KR",
                now.minusSeconds(3600), now.minusSeconds(60),
                now.minusSeconds(30), com.example.auth.domain.session.RevokeReason.USER_REQUESTED);
    }
}
