package com.example.auth.application;

import com.example.auth.application.event.AuthEventPublisher;
import com.example.auth.application.exception.SessionNotFoundException;
import com.example.auth.application.result.RevokeOthersResult;
import com.example.auth.domain.repository.DeviceSessionRepository;
import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.auth.domain.session.DeviceSession;
import com.example.auth.domain.session.RevokeReason;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RevokeAllOtherSessionsUseCase 단위 테스트")
class RevokeAllOtherSessionsUseCaseTest {

    @Mock
    private DeviceSessionRepository deviceSessionRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private AuthEventPublisher authEventPublisher;

    @InjectMocks
    private RevokeAllOtherSessionsUseCase useCase;

    private static final String ACCOUNT_ID = "acc-bulk";
    private static final String CURRENT_DEVICE = "dev-current";

    @Test
    @DisplayName("다른 세션 2개 — 모두 revoke 후 count=2 반환")
    void execute_twoOtherSessions_revokesBothAndReturnsCount() {
        DeviceSession current = activeSession(CURRENT_DEVICE, ACCOUNT_ID);
        DeviceSession other1 = activeSession("dev-other1", ACCOUNT_ID);
        DeviceSession other2 = activeSession("dev-other2", ACCOUNT_ID);
        when(deviceSessionRepository.findByDeviceId(CURRENT_DEVICE)).thenReturn(Optional.of(current));
        when(deviceSessionRepository.findActiveByAccountId(ACCOUNT_ID))
                .thenReturn(List.of(current, other1, other2));
        when(refreshTokenRepository.findActiveJtisByDeviceId(anyString())).thenReturn(List.of());

        RevokeOthersResult result = useCase.execute(ACCOUNT_ID, CURRENT_DEVICE);

        assertThat(result.revokedCount()).isEqualTo(2);
        assertThat(other1.isRevoked()).isTrue();
        assertThat(other1.getRevokeReason()).isEqualTo(RevokeReason.LOGOUT_OTHERS);
        assertThat(other2.isRevoked()).isTrue();
        assertThat(current.isRevoked()).isFalse();
        verify(deviceSessionRepository, times(2)).save(any(DeviceSession.class));
        verify(authEventPublisher, times(2)).publishAuthSessionRevoked(
                anyString(), anyString(), anyString(), anyList(), any(Instant.class), anyString(), anyString());
    }

    @Test
    @DisplayName("다른 세션 없음 — revokedCount=0 반환")
    void execute_noOtherSessions_returnsZero() {
        DeviceSession current = activeSession(CURRENT_DEVICE, ACCOUNT_ID);
        when(deviceSessionRepository.findByDeviceId(CURRENT_DEVICE)).thenReturn(Optional.of(current));
        when(deviceSessionRepository.findActiveByAccountId(ACCOUNT_ID)).thenReturn(List.of(current));

        RevokeOthersResult result = useCase.execute(ACCOUNT_ID, CURRENT_DEVICE);

        assertThat(result.revokedCount()).isZero();
        verify(refreshTokenRepository, never()).revokeAllByDeviceId(anyString());
    }

    @Test
    @DisplayName("blank currentDeviceId — SessionNotFoundException")
    void execute_blankCurrentDeviceId_throwsSessionNotFound() {
        assertThatThrownBy(() -> useCase.execute(ACCOUNT_ID, "  "))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    @DisplayName("null currentDeviceId — SessionNotFoundException")
    void execute_nullCurrentDeviceId_throwsSessionNotFound() {
        assertThatThrownBy(() -> useCase.execute(ACCOUNT_ID, null))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    @DisplayName("current 세션 미존재 — SessionNotFoundException")
    void execute_currentSessionNotFound_throwsSessionNotFound() {
        when(deviceSessionRepository.findByDeviceId(CURRENT_DEVICE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ACCOUNT_ID, CURRENT_DEVICE))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    @DisplayName("current 세션이 타 계정 소속 — SessionNotFoundException")
    void execute_currentSessionDifferentAccount_throwsSessionNotFound() {
        DeviceSession session = activeSession(CURRENT_DEVICE, "other-account");
        when(deviceSessionRepository.findByDeviceId(CURRENT_DEVICE)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> useCase.execute(ACCOUNT_ID, CURRENT_DEVICE))
                .isInstanceOf(SessionNotFoundException.class);
    }

    private static DeviceSession activeSession(String deviceId, String accountId) {
        Instant now = Instant.now();
        return new DeviceSession(1L, deviceId, accountId, "fp", "UA", "1.1.1.1", "KR",
                now.minusSeconds(3600), now.minusSeconds(60), null, null);
    }
}
