package com.example.security.consumer;

import com.example.security.application.DetectSuspiciousActivityUseCase;
import com.example.security.application.RecordLoginHistoryUseCase;
import com.example.security.consumer.handler.EventDedupService;
import com.example.security.domain.detection.EvaluationContext;
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
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenRefreshedConsumer 단위 테스트")
class TokenRefreshedConsumerUnitTest {

    @Mock EventDedupService dedupService;
    @Mock RecordLoginHistoryUseCase recordLoginHistoryUseCase;
    @Mock DetectSuspiciousActivityUseCase detectUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private TokenRefreshedConsumer consumer() {
        return new TokenRefreshedConsumer(objectMapper, dedupService, recordLoginHistoryUseCase, detectUseCase);
    }

    private ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("auth.token.refreshed", 0, 0L, "key", value);
    }

    @Test
    @DisplayName("정상 처리 — outcome=REFRESH")
    void onMessage_validEvent_executesWithRefreshOutcome() {
        when(dedupService.isDuplicate("evt-ref-1")).thenReturn(false);
        when(recordLoginHistoryUseCase.execute(any(LoginHistoryEntry.class), anyString())).thenReturn(true);

        String json = """
                {
                  "eventId": "evt-ref-1",
                  "eventType": "auth.token.refreshed",
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
        assertThat(captor.getValue().getOutcome()).isEqualTo(LoginOutcome.REFRESH);
    }

    @Test
    @DisplayName("detection 오류 — 예외 흡수, history는 정상 기록")
    void onMessage_detectionThrows_exceptionSwallowed() {
        when(dedupService.isDuplicate("evt-ref-det")).thenReturn(false);
        when(recordLoginHistoryUseCase.execute(any(LoginHistoryEntry.class), anyString())).thenReturn(true);
        doThrow(new RuntimeException("detection failure")).when(detectUseCase).detect(any(EvaluationContext.class));

        String json = """
                {
                  "eventId": "evt-ref-det",
                  "eventType": "auth.token.refreshed",
                  "tenantId": "fan-platform",
                  "occurredAt": "2026-04-29T10:00:00Z",
                  "payload": {
                    "accountId": "acc-2",
                    "ipMasked": "2.x.x.x",
                    "timestamp": "2026-04-29T10:00:00Z"
                  }
                }
                """;

        assertThatNoException().isThrownBy(() -> consumer().onMessage(record(json)));
        verify(dedupService).markProcessedInRedis("evt-ref-det");
    }

    @Test
    @DisplayName("eventId 공백 — 조용히 skip, execute 미호출")
    void onMessage_blankEventId_silentlySkips() {
        String json = """
                {
                  "eventId": "  ",
                  "eventType": "auth.token.refreshed",
                  "occurredAt": "2026-04-29T10:00:00Z",
                  "payload": { "accountId": "acc-3", "timestamp": "2026-04-29T10:00:00Z" }
                }
                """;

        consumer().onMessage(record(json));

        verify(dedupService, never()).isDuplicate(anyString());
        verify(recordLoginHistoryUseCase, never()).execute(any(), any());
    }
}
