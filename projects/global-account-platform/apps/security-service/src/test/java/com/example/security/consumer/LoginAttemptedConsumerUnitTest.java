package com.example.security.consumer;

import com.example.security.application.DetectSuspiciousActivityUseCase;
import com.example.security.application.RecordLoginHistoryUseCase;
import com.example.security.consumer.handler.EventDedupService;
import com.example.security.domain.history.LoginHistoryEntry;
import com.example.security.domain.history.LoginOutcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginAttemptedConsumer 단위 테스트")
class LoginAttemptedConsumerUnitTest {

    @Mock EventDedupService dedupService;
    @Mock RecordLoginHistoryUseCase recordLoginHistoryUseCase;
    @Mock DetectSuspiciousActivityUseCase detectUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private LoginAttemptedConsumer consumer() {
        return new LoginAttemptedConsumer(objectMapper, dedupService, recordLoginHistoryUseCase, detectUseCase);
    }

    private ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("auth.login.attempted", 0, 0L, "key", value);
    }

    @Test
    @DisplayName("정상 처리 — outcome=ATTEMPTED 로 execute 호출")
    void onMessage_validEvent_executesWithAttemptedOutcome() {
        when(dedupService.isDuplicate("evt-atm-1")).thenReturn(false);
        when(recordLoginHistoryUseCase.execute(any(LoginHistoryEntry.class), anyString())).thenReturn(true);

        String json = """
                {
                  "eventId": "evt-atm-1",
                  "eventType": "auth.login.attempted",
                  "tenantId": "fan-platform",
                  "occurredAt": "2026-04-29T10:00:00Z",
                  "payload": {
                    "accountId": "acc-1",
                    "ipMasked": "1.x.x.x",
                    "timestamp": "2026-04-29T10:00:00Z"
                  }
                }
                """;

        consumer().onMessage(record(json));

        ArgumentCaptor<LoginHistoryEntry> captor = ArgumentCaptor.forClass(LoginHistoryEntry.class);
        verify(recordLoginHistoryUseCase).execute(captor.capture(), anyString());
        assertThat(captor.getValue().getOutcome()).isEqualTo(LoginOutcome.ATTEMPTED);
        verify(dedupService).markProcessedInRedis("evt-atm-1");
    }

    @Test
    @DisplayName("Redis dedup hit — execute 미호출")
    void onMessage_redisDedupHit_skipsExecution() {
        when(dedupService.isDuplicate("evt-atm-dup")).thenReturn(true);

        String json = """
                {
                  "eventId": "evt-atm-dup",
                  "eventType": "auth.login.attempted",
                  "tenantId": "fan-platform",
                  "occurredAt": "2026-04-29T10:00:00Z",
                  "payload": { "accountId": "acc-2", "timestamp": "2026-04-29T10:00:00Z" }
                }
                """;

        consumer().onMessage(record(json));

        verify(recordLoginHistoryUseCase, never()).execute(any(), any());
    }

    @Test
    @DisplayName("eventId 공백 — 조용히 skip, execute 미호출")
    void onMessage_blankEventId_silentlySkips() {
        String json = """
                {
                  "eventId": "",
                  "eventType": "auth.login.attempted",
                  "occurredAt": "2026-04-29T10:00:00Z",
                  "payload": { "accountId": "acc-3", "timestamp": "2026-04-29T10:00:00Z" }
                }
                """;

        consumer().onMessage(record(json));

        verify(dedupService, never()).isDuplicate(anyString());
        verify(recordLoginHistoryUseCase, never()).execute(any(), any());
    }
}
