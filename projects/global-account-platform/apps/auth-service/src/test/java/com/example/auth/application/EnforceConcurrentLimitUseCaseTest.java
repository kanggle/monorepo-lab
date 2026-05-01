package com.example.auth.application;

import com.example.auth.application.event.AuthEventPublisher;
import com.example.auth.domain.repository.DeviceSessionRepository;
import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.auth.domain.session.DeviceSession;
import com.example.auth.domain.session.RevokeReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnforceConcurrentLimitUseCaseTest {

    @Mock
    private DeviceSessionRepository deviceSessionRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private AuthEventPublisher authEventPublisher;

    @InjectMocks
    private EnforceConcurrentLimitUseCase useCase;

    private static final String ACCOUNT_ID = "acc-evict";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(useCase, "maxActiveSessions", 10);
    }

    private static final String TENANT_ID = "fan-platform";

    @Test
    @DisplayName("no-op when active count + 1 still fits the limit")
    void noopWhenWithinLimit() {
        when(deviceSessionRepository.countActiveByAccountId(ACCOUNT_ID)).thenReturn(8L);

        List<String> evicted = useCase.enforce(ACCOUNT_ID, TENANT_ID);

        assertThat(evicted).isEmpty();
        verify(deviceSessionRepository, never()).findOldestActiveByAccountId(anyString(), anyInt());
        verify(authEventPublisher, never())
                .publishAuthSessionRevoked(anyString(), anyString(), anyString(), anyString(),
                        anyList(), any(Instant.class), anyString(), any());
    }

    @Test
    @DisplayName("11th login evicts oldest device, cascade-revokes refresh tokens, and emits event")
    void elevenAttemptedTriggersEviction() {
        // 10 active sessions already; the caller is about to insert the 11th — must evict 1.
        when(deviceSessionRepository.countActiveByAccountId(ACCOUNT_ID)).thenReturn(10L);

        DeviceSession oldest = makeSession("dev-oldest");
        when(deviceSessionRepository.findOldestActiveByAccountId(ACCOUNT_ID, 1))
                .thenReturn(List.of(oldest));
        when(refreshTokenRepository.findActiveJtisByDeviceId("dev-oldest"))
                .thenReturn(List.of("jti-A"));

        List<String> evicted = useCase.enforce(ACCOUNT_ID, TENANT_ID);

        assertThat(evicted).containsExactly("dev-oldest");
        assertThat(oldest.getRevokeReason()).isEqualTo(RevokeReason.EVICTED_BY_LIMIT);
        assertThat(oldest.isRevoked()).isTrue();
        verify(refreshTokenRepository).revokeAllByDeviceId("dev-oldest");
        verify(deviceSessionRepository).save(oldest);

        ArgumentCaptor<List<String>> jtisCaptor = jtisCaptor();
        verify(authEventPublisher).publishAuthSessionRevoked(
                eq(ACCOUNT_ID), eq(TENANT_ID), eq("dev-oldest"),
                eq(RevokeReason.EVICTED_BY_LIMIT.name()),
                jtisCaptor.capture(), any(Instant.class),
                eq("SYSTEM"), eq(null));
        assertThat(jtisCaptor.getValue()).containsExactly("jti-A");
    }

    @Test
    @DisplayName("two-over-limit evicts the two oldest in a single transaction")
    void twoOverLimitEvictsTwo() {
        when(deviceSessionRepository.countActiveByAccountId(ACCOUNT_ID)).thenReturn(11L);
        DeviceSession s1 = makeSession("dev-a");
        DeviceSession s2 = makeSession("dev-b");
        when(deviceSessionRepository.findOldestActiveByAccountId(ACCOUNT_ID, 2))
                .thenReturn(List.of(s1, s2));
        when(refreshTokenRepository.findActiveJtisByDeviceId(anyString()))
                .thenReturn(List.of());

        List<String> evicted = useCase.enforce(ACCOUNT_ID, TENANT_ID);

        assertThat(evicted).containsExactly("dev-a", "dev-b");
        verify(refreshTokenRepository, times(1)).revokeAllByDeviceId("dev-a");
        verify(refreshTokenRepository, times(1)).revokeAllByDeviceId("dev-b");
        verify(authEventPublisher, times(2))
                .publishAuthSessionRevoked(eq(ACCOUNT_ID), eq(TENANT_ID), anyString(),
                        eq(RevokeReason.EVICTED_BY_LIMIT.name()),
                        anyList(), any(Instant.class), eq("SYSTEM"), eq(null));
    }

    private static DeviceSession makeSession(String deviceId) {
        Instant now = Instant.now();
        return new DeviceSession(1L, deviceId, ACCOUNT_ID, "fp",
                "UA", "1.1.1.1", "KR", now.minusSeconds(3600), now.minusSeconds(60),
                null, null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<List<String>> jtisCaptor() {
        return ArgumentCaptor.forClass((Class) List.class);
    }

    // Suppress unused import warning when both ArrayList and List are present.
    @SuppressWarnings("unused")
    private void unused() { new ArrayList<String>(); }
}
