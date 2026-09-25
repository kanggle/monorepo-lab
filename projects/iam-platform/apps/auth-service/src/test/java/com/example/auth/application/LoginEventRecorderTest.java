package com.example.auth.application;

import com.example.auth.application.event.AuthEventPublisher;
import com.example.auth.domain.session.SessionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Constructor;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * TASK-BE-599 — unit tests for {@link LoginEventRecorder}, the form-login path's login events
 * (ⓐ only: no rate limit, no device session).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class LoginEventRecorderTest {

    @Mock
    private AuthEventPublisher authEventPublisher;

    @InjectMocks
    private LoginEventRecorder recorder;

    private static final SessionContext CTX =
            new SessionContext("203.0.113.7", "Mozilla/5.0 Chrome/120.0", null, "KR");

    @Test
    @DisplayName("attempted is published verbatim (accountId, emailHash, tenant, ctx)")
    void recordAttempted_publishes() {
        recorder.recordAttempted("acc-1", "0123456789", "acme-corp", CTX);

        verify(authEventPublisher).publishLoginAttempted("acc-1", "0123456789", "acme-corp", CTX);
        verifyNoMoreInteractions(authEventPublisher);
    }

    @Test
    @DisplayName("failed carries failCount=0 — no auth-service failure counter on this path (ⓑ declined)")
    void recordFailed_failCountZero() {
        recorder.recordFailed("acc-1", "0123456789", "acme-corp", "CREDENTIALS_INVALID", CTX);

        verify(authEventPublisher).publishLoginFailed(
                "acc-1", "0123456789", "acme-corp", "CREDENTIALS_INVALID", 0, CTX);
        verifyNoMoreInteractions(authEventPublisher);
    }

    @Test
    @DisplayName("succeeded carries deviceId=null / isNewDevice=null / sessionJti=null, and NOTHING else is published (no auth.session.created)")
    void recordSucceeded_noDeviceFields_noSessionCreated() {
        recorder.recordSucceeded("acc-1", "acme-corp", CTX);

        verify(authEventPublisher).publishLoginSucceeded("acc-1", null, "acme-corp", CTX, null, null);
        // ⓒ withdrawn: no auth.session.created, no other event.
        verifyNoMoreInteractions(authEventPublisher);
    }

    @Test
    @DisplayName("TASK-BE-602: social succeeded carries loginMethod, the same null device fields, and nothing else")
    void recordSucceeded_social_carriesLoginMethod() {
        recorder.recordSucceeded("acc-1", "ecommerce", CTX, "OAUTH_NAVER");

        verify(authEventPublisher).publishLoginSucceeded(
                "acc-1", null, "ecommerce", CTX, null, null, "OAUTH_NAVER");
        verifyNoMoreInteractions(authEventPublisher);
    }

    @Test
    @DisplayName("the form login does not register a device session — the recorder cannot even reach the use case")
    void formLogin_doesNotRegisterDeviceSession() {
        // Structural: the only collaborator is the event publisher. If device-session
        // registration comes back, this constructor grows a RegisterOrUpdateDeviceSessionUseCase
        // parameter and this test fails — re-adding ⓒ must be a deliberate decision.
        Constructor<?>[] ctors = LoginEventRecorder.class.getDeclaredConstructors();
        assertThat(ctors).hasSize(1);
        assertThat(Arrays.asList(ctors[0].getParameterTypes()))
                .containsExactly(AuthEventPublisher.class)
                .doesNotContain(RegisterOrUpdateDeviceSessionUseCase.class);
    }

    @Test
    @DisplayName("a publish failure propagates to the caller (so the transaction rolls back); the caller decides to swallow it")
    void recordSucceeded_publishFailurePropagates() {
        doThrow(new IllegalStateException("outbox down")).when(authEventPublisher)
                .publishLoginSucceeded(anyString(), isNull(), anyString(), any(), isNull(), isNull());

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
