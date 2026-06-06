package com.example.auth.application;

import com.example.auth.application.result.ListSessionsResult;
import com.example.auth.domain.repository.DeviceSessionRepository;
import com.example.auth.domain.session.DeviceSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ListSessionsUseCase 단위 테스트")
class ListSessionsUseCaseTest {

    @Mock
    private DeviceSessionRepository deviceSessionRepository;

    @InjectMocks
    private ListSessionsUseCase useCase;

    private static final String ACCOUNT_ID = "acc-list";
    private static final int MAX_SESSIONS = 10;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(useCase, "maxActiveSessions", MAX_SESSIONS);
    }

    @Test
    @DisplayName("활성 세션 없음 — 빈 목록 반환")
    void execute_noSessions_returnsEmptyList() {
        when(deviceSessionRepository.findActiveByAccountId(ACCOUNT_ID)).thenReturn(List.of());

        ListSessionsResult result = useCase.execute(ACCOUNT_ID, "dev-x");

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        assertThat(result.maxActiveSessions()).isEqualTo(MAX_SESSIONS);
    }

    @Test
    @DisplayName("현재 디바이스 세션 — current=true 로 표시")
    void execute_currentDevice_markedAsCurrent() {
        String currentDeviceId = "dev-current";
        DeviceSession current = session(currentDeviceId, ACCOUNT_ID);
        DeviceSession other = session("dev-other", ACCOUNT_ID);
        when(deviceSessionRepository.findActiveByAccountId(ACCOUNT_ID))
                .thenReturn(List.of(current, other));

        ListSessionsResult result = useCase.execute(ACCOUNT_ID, currentDeviceId);

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().stream()
                .filter(r -> r.deviceId().equals(currentDeviceId))
                .findFirst().orElseThrow().current()).isTrue();
        assertThat(result.items().stream()
                .filter(r -> r.deviceId().equals("dev-other"))
                .findFirst().orElseThrow().current()).isFalse();
    }

    @Test
    @DisplayName("currentDeviceId=null — 모든 세션 current=false")
    void execute_nullCurrentDeviceId_allSessionsNotCurrent() {
        DeviceSession s = session("dev-a", ACCOUNT_ID);
        when(deviceSessionRepository.findActiveByAccountId(ACCOUNT_ID)).thenReturn(List.of(s));

        ListSessionsResult result = useCase.execute(ACCOUNT_ID, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).current()).isFalse();
    }

    private static DeviceSession session(String deviceId, String accountId) {
        Instant now = Instant.now();
        return new DeviceSession(1L, deviceId, accountId, "fp", "Mozilla/5.0", "1.2.3.4", "US",
                now.minusSeconds(3600), now.minusSeconds(60), null, null);
    }
}
