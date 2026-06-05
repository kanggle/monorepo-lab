package com.example.auth.application;

import com.example.auth.application.command.RequestPasswordResetCommand;
import com.example.auth.application.exception.EmailSendException;
import com.example.auth.application.port.EmailSenderPort;
import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.repository.CredentialRepository;
import com.example.auth.domain.repository.PasswordResetAttemptCounter;
import com.example.auth.domain.repository.PasswordResetTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("RequestPasswordResetUseCase 단위 테스트")
class RequestPasswordResetUseCaseTest {

    @Mock
    private CredentialRepository credentialRepository;

    @Mock
    private PasswordResetTokenStore passwordResetTokenStore;

    @Mock
    private PasswordResetAttemptCounter rateLimitCounter;

    @Mock
    private EmailSenderPort emailSenderPort;

    @InjectMocks
    private RequestPasswordResetUseCase useCase;

    @BeforeEach
    void setUp() {
        // Default: rate limit lets the request through. Tests that exercise
        // the over-limit path override this stub explicitly.
        lenient().when(rateLimitCounter.tryAcquire(anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("이메일이 존재하면 토큰을 저장하고 이메일을 발송한다")
    void execute_existingEmail_savesTokenAndSendsEmail() {
        // given — credential row exists for the requested email
        String accountId = "acc-1";
        String email = "user@example.com";
        Credential credential = new Credential(
                1L, accountId, email,
                "$argon2id$hash", "argon2id",
                Instant.now(), Instant.now(), 0
        );
        given(credentialRepository.findByAccountIdEmail(email))
                .willReturn(Optional.of(credential));

        // when
        useCase.execute(new RequestPasswordResetCommand(email));

        // then — token saved with 1h TTL and the same token was emailed
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(passwordResetTokenStore).save(tokenCaptor.capture(), eq(accountId), ttlCaptor.capture());
        verify(emailSenderPort).sendPasswordResetEmail(eq(email), eq(tokenCaptor.getValue()));

        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofHours(1));
        // Token should be a UUID string — guards against accidental hardcoding.
        assertThat(UUID.fromString(tokenCaptor.getValue())).isNotNull();
    }

    @Test
    @DisplayName("입력 이메일은 정규화 후 조회된다 (대소문자/공백)")
    void execute_emailIsNormalizedBeforeLookup() {
        // given — caller passes raw input with whitespace + uppercase
        String raw = "  User@Example.COM  ";
        String normalized = "user@example.com";
        Credential credential = new Credential(
                1L, "acc-2", normalized,
                "$argon2id$hash", "argon2id",
                Instant.now(), Instant.now(), 0
        );
        given(credentialRepository.findByAccountIdEmail(normalized))
                .willReturn(Optional.of(credential));

        // when
        useCase.execute(new RequestPasswordResetCommand(raw));

        // then — repository query and email recipient both use the normalized form
        verify(credentialRepository).findByAccountIdEmail(normalized);
        verify(emailSenderPort).sendPasswordResetEmail(eq(normalized), anyString());
    }

    @Test
    @DisplayName("미등록 이메일이면 토큰 저장과 이메일 발송 모두 호출되지 않는다")
    void execute_unknownEmail_noSideEffects() {
        // given — repository returns empty
        given(credentialRepository.findByAccountIdEmail("ghost@example.com"))
                .willReturn(Optional.empty());

        // when
        useCase.execute(new RequestPasswordResetCommand("ghost@example.com"));

        // then — silent no-op: nothing in the side-effect path runs
        verifyNoInteractions(passwordResetTokenStore);
        verifyNoInteractions(emailSenderPort);
    }

    @Test
    @DisplayName("이메일 발송이 실패해도 use case 는 정상 종료된다 (best-effort)")
    void execute_emailSendFails_swallowsException() {
        // given — credential exists but email sender throws
        String accountId = "acc-3";
        String email = "boom@example.com";
        Credential credential = new Credential(
                1L, accountId, email,
                "$argon2id$hash", "argon2id",
                Instant.now(), Instant.now(), 0
        );
        given(credentialRepository.findByAccountIdEmail(email))
                .willReturn(Optional.of(credential));
        willThrow(new EmailSendException("smtp down"))
                .given(emailSenderPort).sendPasswordResetEmail(eq(email), anyString());

        // when / then — must not propagate; controller still surfaces 204
        useCase.execute(new RequestPasswordResetCommand(email));

        // token write must precede the email attempt (so retries can reuse it)
        verify(passwordResetTokenStore).save(anyString(), eq(accountId), any(Duration.class));
        verify(emailSenderPort).sendPasswordResetEmail(eq(email), anyString());
        // delete must NOT be called on failure — the user may complete via a retry
        verify(passwordResetTokenStore, never()).delete(anyString());
    }

    @Test
    @DisplayName("rate limit 초과 시 등록된 이메일이라도 토큰 발급/이메일 발송이 모두 차단된다 (TASK-BE-144)")
    void execute_rateLimitExceeded_existingEmail_silentDrop() {
        // given — repository would resolve, but counter blocks the request
        String email = "spam-target@example.com";
        given(rateLimitCounter.tryAcquire(anyString())).willReturn(false);

        // when
        useCase.execute(new RequestPasswordResetCommand(email));

        // then — credential lookup must not even fire (cheaper + fewer leaks)
        verifyNoInteractions(credentialRepository);
        verifyNoInteractions(passwordResetTokenStore);
        verifyNoInteractions(emailSenderPort);
    }

    @Test
    @DisplayName("rate limit 초과 시 미등록 이메일도 동일하게 silent drop — enumeration 방지")
    void execute_rateLimitExceeded_unknownEmail_silentDrop() {
        given(rateLimitCounter.tryAcquire(anyString())).willReturn(false);

        useCase.execute(new RequestPasswordResetCommand("ghost@example.com"));

        verifyNoInteractions(credentialRepository);
        verifyNoInteractions(passwordResetTokenStore);
        verifyNoInteractions(emailSenderPort);
    }

    @Test
    @DisplayName("rate limit 카운터는 발견/미발견 분기에 무관하게 호출된다 (timing oracle 방지)")
    void execute_counterCalled_regardlessOfCredentialExistence() {
        given(credentialRepository.findByAccountIdEmail(anyString()))
                .willReturn(Optional.empty());

        useCase.execute(new RequestPasswordResetCommand("any@example.com"));

        verify(rateLimitCounter).tryAcquire(anyString());
    }

    @Test
    @DisplayName("rate limit 카운터는 정규화된 이메일의 해시로 호출된다 (대소문자/공백 변형이 동일 키)")
    void execute_counterReceivesNormalizedHash() {
        // given — two equivalent inputs after normalization
        given(credentialRepository.findByAccountIdEmail(anyString()))
                .willReturn(Optional.empty());

        // when
        useCase.execute(new RequestPasswordResetCommand("  USER@Example.com  "));
        useCase.execute(new RequestPasswordResetCommand("user@example.com"));

        // then — both invocations must reach the counter with the SAME hash
        String expectedHash = RequestPasswordResetUseCase.hashEmail("user@example.com");
        verify(rateLimitCounter, org.mockito.Mockito.times(2)).tryAcquire(expectedHash);
    }
}
