package com.example.auth.application;

import com.example.auth.application.event.AuthEventPublisher;
import com.example.auth.application.result.RegisterDeviceSessionResult;
import com.example.auth.domain.session.SessionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-599 — unit tests for {@link LoginEventRecorder}, the form-login path's login
 * side effects (events + device session, no rate limit).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class LoginEventRecorderTest {

    @Mock
    private AuthEventPublisher authEventPublisher;

    @Mock
    private RegisterOrUpdateDeviceSessionUseCase registerOrUpdateDeviceSessionUseCase;

    @InjectMocks
    private LoginEventRecorder recorder;

    private static final SessionContext CTX =
            new SessionContext("203.0.113.7", "Mozilla/5.0 Chrome/120.0", "fp-raw", "KR");

    @Test
    @DisplayName("attempted is published verbatim (accountId, emailHash, tenant, ctx)")
    void recordAttempted_publishes() {
        recorder.recordAttempted("acc-1", "0123456789", "acme-corp", CTX);

        verify(authEventPublisher).publishLoginAttempted("acc-1", "0123456789", "acme-corp", CTX);
    }

    @Test
    @DisplayName("failed carries failCount=0 — no auth-service failure counter on this path (ⓑ declined)")
    void recordFailed_failCountZero() {
        recorder.recordFailed("acc-1", "0123456789", "acme-corp", "CREDENTIALS_INVALID", CTX);

        verify(authEventPublisher).publishLoginFailed(
                "acc-1", "0123456789", "acme-corp", "CREDENTIALS_INVALID", 0, CTX);
    }

    @Test
    @DisplayName("new device: device session registered first, then succeeded(isNewDevice=true) + session.created")
    void recordSucceeded_newDevice() {
        when(registerOrUpdateDeviceSessionUseCase.execute("acc-1", "acme-corp", CTX))
                .thenReturn(new RegisterDeviceSessionResult("dev-1", true, List.of("dev-old")));

        RegisterDeviceSessionResult result = recorder.recordSucceeded("acc-1", "acme-corp", CTX);

        assertThat(result.deviceId()).isEqualTo("dev-1");
        InOrder order = inOrder(registerOrUpdateDeviceSessionUseCase, authEventPublisher);
        order.verify(registerOrUpdateDeviceSessionUseCase).execute("acc-1", "acme-corp", CTX);
        // sessionJti is null: SAS has not minted a refresh token yet at form-login time.
        order.verify(authEventPublisher).publishLoginSucceeded(
                "acc-1", null, "acme-corp", CTX, "dev-1", true);
        order.verify(authEventPublisher).publishAuthSessionCreated(
                eq("acc-1"), eq("acme-corp"), eq("dev-1"), isNull(),
                eq(LoginHashes.fingerprintHash("fp-raw")), eq("Chrome"), eq("203.0.*.*"), eq("KR"),
                any(), eq(List.of("dev-old")));
    }

    @Test
    @DisplayName("known device: succeeded(isNewDevice=false), no session.created")
    void recordSucceeded_knownDevice() {
        when(registerOrUpdateDeviceSessionUseCase.execute("acc-1", "acme-corp", CTX))
                .thenReturn(new RegisterDeviceSessionResult("dev-1", false, List.of()));

        recorder.recordSucceeded("acc-1", "acme-corp", CTX);

        verify(authEventPublisher).publishLoginSucceeded(
                "acc-1", null, "acme-corp", CTX, "dev-1", false);
        verify(authEventPublisher, never()).publishAuthSessionCreated(
                anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("a publish failure propagates to the caller (so the transaction rolls back); the caller decides to swallow it")
    void recordSucceeded_publishFailurePropagates() {
        when(registerOrUpdateDeviceSessionUseCase.execute("acc-1", "acme-corp", CTX))
                .thenReturn(new RegisterDeviceSessionResult("dev-1", false, List.of()));
        doThrow(new IllegalStateException("outbox down")).when(authEventPublisher)
                .publishLoginSucceeded(anyString(), isNull(), anyString(), any(), anyString(), anyBoolean());

        assertThatThrownBy(() -> recorder.recordSucceeded("acc-1", "acme-corp", CTX))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("LoginHashes keeps LoginUseCase's algorithm: SHA-256(lower(email))[:10] and SHA-256(fingerprint) hex")
    void hashes_keepTheContractAlgorithm() {
        // Reference values computed outside the JVM (sha256sum), NOT via LoginUseCase — which
        // now delegates here, so comparing the two would be a tautology.
        assertThat(LoginHashes.emailHash("User@Example.com")).isEqualTo("b4c9a28932");
        assertThat(LoginUseCase.hashEmail("user@example.com")).isEqualTo("b4c9a28932");
        assertThat(LoginHashes.fingerprintHash("fp-raw"))
                .isEqualTo("739d4b61fab3ecdb938ad7d3761044209cd70b1b38cb1165612b635fa9788672");
        assertThat(LoginHashes.fingerprintHash(" ")).isNull();
        assertThat(LoginHashes.fingerprintHash(null)).isNull();
    }
}
