package com.example.account.infrastructure.kafka;

import com.example.account.application.service.UpdateLastLoginUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link LoginSucceededConsumer} (TASK-BE-103).
 *
 * <p>Covers envelope parsing, mandatory-field validation, timestamp fallback,
 * and JSON-deserialization error escalation. The use-case itself is mocked;
 * its behaviour is exercised in {@code UpdateLastLoginUseCaseTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LoginSucceededConsumer 단위 테스트")
class LoginSucceededConsumerUnitTest {

    @Mock
    private UpdateLastLoginUseCase updateLastLoginUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private LoginSucceededConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new LoginSucceededConsumer(objectMapper, updateLastLoginUseCase);
    }

    private ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("auth.login.succeeded", 0, 0L, "acc-1", value);
    }

    @Test
    @DisplayName("정상 envelope 파싱 → UseCase가 eventId/accountId/timestamp로 호출된다")
    void onMessage_validEnvelope_invokesUseCase() {
        String json = """
                {
                  "eventId": "11111111-1111-1111-1111-111111111111",
                  "eventType": "auth.login.succeeded",
                  "source": "auth-service",
                  "occurredAt": "2026-04-26T10:00:00Z",
                  "schemaVersion": 1,
                  "partitionKey": "acc-1",
                  "payload": {
                    "accountId": "acc-1",
                    "ipMasked": "192.168.*.*",
                    "userAgentFamily": "Chrome 120",
                    "deviceFingerprint": "fp-1",
                    "geoCountry": "KR",
                    "sessionJti": "jti-1",
                    "timestamp": "2026-04-26T10:00:00Z"
                  }
                }
                """;

        consumer.onMessage(record(json));

        verify(updateLastLoginUseCase).execute(
                "11111111-1111-1111-1111-111111111111",
                "acc-1",
                Instant.parse("2026-04-26T10:00:00Z"));
    }

    @Test
    @DisplayName("eventId 누락 → UseCase 미호출, 예외 미전파 (auth-service가 source of truth — 재시도 무의미)")
    void onMessage_missingEventId_skipsSilently() {
        String json = """
                {
                  "payload": {
                    "accountId": "acc-1",
                    "timestamp": "2026-04-26T10:00:00Z"
                  }
                }
                """;

        consumer.onMessage(record(json));

        verifyNoInteractions(updateLastLoginUseCase);
    }

    @Test
    @DisplayName("payload.accountId 누락 → UseCase 미호출, 예외 미전파")
    void onMessage_missingAccountId_skipsSilently() {
        String json = """
                {
                  "eventId": "22222222-2222-2222-2222-222222222222",
                  "payload": {
                    "timestamp": "2026-04-26T10:00:00Z"
                  }
                }
                """;

        consumer.onMessage(record(json));

        verifyNoInteractions(updateLastLoginUseCase);
    }

    @Test
    @DisplayName("payload.timestamp 누락 → UseCase는 호출되지만 timestamp는 now() fallback")
    void onMessage_missingTimestamp_fallsBackToNow() {
        String json = """
                {
                  "eventId": "33333333-3333-3333-3333-333333333333",
                  "payload": {
                    "accountId": "acc-1"
                  }
                }
                """;

        Instant before = Instant.now();
        consumer.onMessage(record(json));
        Instant after = Instant.now();

        verify(updateLastLoginUseCase).execute(
                org.mockito.ArgumentMatchers.eq("33333333-3333-3333-3333-333333333333"),
                org.mockito.ArgumentMatchers.eq("acc-1"),
                org.mockito.ArgumentMatchers.argThat(
                        ts -> !ts.isBefore(before) && !ts.isAfter(after)));
    }

    @Test
    @DisplayName("payload.timestamp 파싱 불가 → UseCase는 호출되지만 timestamp는 now() fallback")
    void onMessage_unparseableTimestamp_fallsBackToNow() {
        String json = """
                {
                  "eventId": "44444444-4444-4444-4444-444444444444",
                  "payload": {
                    "accountId": "acc-1",
                    "timestamp": "not-a-timestamp"
                  }
                }
                """;

        Instant before = Instant.now();
        consumer.onMessage(record(json));
        Instant after = Instant.now();

        verify(updateLastLoginUseCase).execute(
                org.mockito.ArgumentMatchers.eq("44444444-4444-4444-4444-444444444444"),
                org.mockito.ArgumentMatchers.eq("acc-1"),
                org.mockito.ArgumentMatchers.argThat(
                        ts -> !ts.isBefore(before) && !ts.isAfter(after)));
    }

    @Test
    @DisplayName("Malformed JSON → RuntimeException 전파 (DefaultErrorHandler가 retry/DLQ 처리)")
    void onMessage_malformedJson_throwsRuntimeException() {
        assertThatThrownBy(() -> consumer.onMessage(record("not json {{{")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("auth.login.succeeded deserialization failed");

        verify(updateLastLoginUseCase, never()).execute(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
