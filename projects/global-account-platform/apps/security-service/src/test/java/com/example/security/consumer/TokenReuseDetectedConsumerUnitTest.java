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
@DisplayName("TokenReuseDetectedConsumer 단위 테스트")
class TokenReuseDetectedConsumerUnitTest {

    @Mock EventDedupService dedupService;
    @Mock RecordLoginHistoryUseCase recordLoginHistoryUseCase;
    @Mock DetectSuspiciousActivityUseCase detectUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private TokenReuseDetectedConsumer consumer() {
        return new TokenReuseDetectedConsumer(objectMapper, dedupService, recordLoginHistoryUseCase, detectUseCase);
    }

    private ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("auth.token.reuse.detected", 0, 0L, "key", value);
    }

    @Test
    @DisplayName("정상 처리 — outcome=TOKEN_REUSE")
    void onMessage_validEvent_executesWithTokenReuseOutcome() {
        when(dedupService.isDuplicate("evt-reuse-1")).thenReturn(false);
        when(recordLoginHistoryUseCase.execute(any(LoginHistoryEntry.class), anyString())).thenReturn(true);

        String json = """
                {
                  "eventId": "evt-reuse-1",
                  "eventType": "auth.token.reuse.detected",
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
        assertThat(captor.getValue().getOutcome()).isEqualTo(LoginOutcome.TOKEN_REUSE);
        verify(dedupService).markProcessedInRedis("evt-reuse-1");
    }

    @Test
    @DisplayName("Redis dedup hit — execute 미호출")
    void onMessage_redisDedupHit_skipsExecution() {
        when(dedupService.isDuplicate("evt-reuse-dup")).thenReturn(true);

        String json = """
                {
                  "eventId": "evt-reuse-dup",
                  "eventType": "auth.token.reuse.detected",
                  "tenantId": "fan-platform",
                  "occurredAt": "2026-04-29T10:00:00Z",
                  "payload": { "accountId": "acc-2", "timestamp": "2026-04-29T10:00:00Z" }
                }
                """;

        consumer().onMessage(record(json));

        verify(recordLoginHistoryUseCase, never()).execute(any(), any());
    }

    @Test
    @DisplayName("Malformed JSON — RuntimeException 전파 (DLQ 라우팅)")
    void onMessage_malformedJson_throwsRuntimeException() {
        assertThatThrownBy(() -> consumer().onMessage(record("not json {{{")))
                .isInstanceOf(RuntimeException.class);

        verify(recordLoginHistoryUseCase, never()).execute(any(), any());
    }

    @Test
    @DisplayName("tenant_id 누락 (envelope·payload 모두) → MissingTenantIdException → DLQ 라우팅 (TASK-BE-259)")
    void onMessage_missingTenantId_throwsMissingTenantIdException() {
        // No tenantId in envelope, no tenantId in payload — both required positions empty.
        // AbstractAuthEventConsumer.processEvent rejects before dedup/persistence.
        String json = """
                {
                  "eventId": "evt-reuse-no-tenant",
                  "eventType": "auth.token.reuse.detected",
                  "occurredAt": "2026-04-29T10:00:00Z",
                  "payload": {
                    "accountId": "acc-1",
                    "ipMasked": "1.x.x.x",
                    "timestamp": "2026-04-29T10:00:00Z"
                  }
                }
                """;

        assertThatThrownBy(() -> consumer().onMessage(record(json)))
                .isInstanceOf(MissingTenantIdException.class);

        // No history persistence, no dedup mark — strict reject before any side effect.
        verify(recordLoginHistoryUseCase, never()).execute(any(), any());
    }

    @Test
    @DisplayName("tenant_id 누락 + blank string → DLQ 라우팅 (blank tenant_id 도 거부)")
    void onMessage_blankTenantId_throwsMissingTenantIdException() {
        String json = """
                {
                  "eventId": "evt-reuse-blank-tenant",
                  "eventType": "auth.token.reuse.detected",
                  "tenantId": "",
                  "occurredAt": "2026-04-29T10:00:00Z",
                  "payload": {
                    "tenantId": "",
                    "accountId": "acc-1"
                  }
                }
                """;

        assertThatThrownBy(() -> consumer().onMessage(record(json)))
                .isInstanceOf(MissingTenantIdException.class);

        verify(recordLoginHistoryUseCase, never()).execute(any(), any());
    }

    @Test
    @DisplayName("tenant_id 가 envelope 상위에만 있어도 (payload 누락) 정상 처리")
    void onMessage_tenantIdAtEnvelopeOnly_processesNormally() {
        when(dedupService.isDuplicate("evt-reuse-env-tenant")).thenReturn(false);
        when(recordLoginHistoryUseCase.execute(any(LoginHistoryEntry.class), anyString())).thenReturn(true);

        String json = """
                {
                  "eventId": "evt-reuse-env-tenant",
                  "eventType": "auth.token.reuse.detected",
                  "tenantId": "wms",
                  "occurredAt": "2026-04-29T10:00:00Z",
                  "payload": {
                    "accountId": "acc-2",
                    "ipMasked": "1.x.x.x",
                    "timestamp": "2026-04-29T10:00:00Z"
                  }
                }
                """;

        consumer().onMessage(record(json));

        verify(recordLoginHistoryUseCase).execute(any(LoginHistoryEntry.class), anyString());
        verify(dedupService).markProcessedInRedis("evt-reuse-env-tenant");
    }
}
