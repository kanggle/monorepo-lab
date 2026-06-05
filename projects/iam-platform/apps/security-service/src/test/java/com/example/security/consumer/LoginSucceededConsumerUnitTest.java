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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginSucceededConsumer 단위 테스트")
class LoginSucceededConsumerUnitTest {

    @Mock EventDedupService dedupService;
    @Mock RecordLoginHistoryUseCase recordLoginHistoryUseCase;
    @Mock DetectSuspiciousActivityUseCase detectUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private LoginSucceededConsumer consumer() {
        return new LoginSucceededConsumer(objectMapper, dedupService, recordLoginHistoryUseCase, detectUseCase);
    }

    private ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("auth.login.succeeded", 0, 0L, "key", value);
    }

    @Test
    @DisplayName("정상 처리 — outcome=SUCCESS, dedup mark 호출")
    void onMessage_validEvent_executesWithSuccessOutcomeAndMarksDedup() {
        when(dedupService.isDuplicate("evt-ok-1")).thenReturn(false);
        when(recordLoginHistoryUseCase.execute(any(LoginHistoryEntry.class), anyString())).thenReturn(true);

        // TASK-BE-248 Phase 2a: tenantId required in envelope.
        String json = """
                {
                  "eventId": "evt-ok-1",
                  "eventType": "auth.login.succeeded",
                  "tenantId": "fan-platform",
                  "occurredAt": "2026-04-29T10:00:00Z",
                  "payload": {
                    "accountId": "acc-1",
                    "ipMasked": "1.x.x.x",
                    "geoCountry": "KR",
                    "timestamp": "2026-04-29T10:00:00Z"
                  }
                }
                """;

        consumer().onMessage(record(json));

        ArgumentCaptor<LoginHistoryEntry> captor = ArgumentCaptor.forClass(LoginHistoryEntry.class);
        verify(recordLoginHistoryUseCase).execute(captor.capture(), anyString());
        assertThat(captor.getValue().getOutcome()).isEqualTo(LoginOutcome.SUCCESS);
        verify(dedupService).markProcessedInRedis("evt-ok-1");
    }

    @Test
    @DisplayName("DB 중복 — execute=false → markProcessedInRedis 미호출")
    void onMessage_dbDuplicate_doesNotMarkRedis() {
        when(dedupService.isDuplicate("evt-db-dup")).thenReturn(false);
        when(recordLoginHistoryUseCase.execute(any(LoginHistoryEntry.class), anyString())).thenReturn(false);

        String json = """
                {
                  "eventId": "evt-db-dup",
                  "eventType": "auth.login.succeeded",
                  "tenantId": "fan-platform",
                  "occurredAt": "2026-04-29T10:00:00Z",
                  "payload": { "accountId": "acc-2", "timestamp": "2026-04-29T10:00:00Z" }
                }
                """;

        consumer().onMessage(record(json));

        verify(dedupService, never()).markProcessedInRedis(anyString());
        verify(detectUseCase, never()).detect(any());
    }

    @Test
    @DisplayName("Malformed JSON — RuntimeException 전파 (DLQ 라우팅)")
    void onMessage_malformedJson_throwsRuntimeException() {
        assertThatThrownBy(() -> consumer().onMessage(record("{ not valid json {{{")))
                .isInstanceOf(RuntimeException.class);

        verify(recordLoginHistoryUseCase, never()).execute(any(), any());
    }

    @Test
    @DisplayName("TASK-BE-248: tenant_id 누락 메시지 → MissingTenantIdException (DLQ 라우팅 트리거)")
    void onMessage_missingTenantId_throwsMissingTenantIdException() {
        String json = """
                {
                  "eventId": "evt-no-tenant-ok",
                  "eventType": "auth.login.succeeded",
                  "occurredAt": "2026-04-29T10:00:00Z",
                  "payload": { "accountId": "acc-x", "timestamp": "2026-04-29T10:00:00Z" }
                }
                """;

        assertThatThrownBy(() -> consumer().onMessage(record(json)))
                .isInstanceOf(MissingTenantIdException.class)
                .hasMessageContaining("evt-no-tenant-ok");

        verify(recordLoginHistoryUseCase, never()).execute(any(), any());
    }
}
