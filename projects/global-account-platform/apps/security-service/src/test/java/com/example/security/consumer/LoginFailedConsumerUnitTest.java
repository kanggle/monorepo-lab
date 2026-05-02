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
@DisplayName("LoginFailedConsumer 단위 테스트")
class LoginFailedConsumerUnitTest {

    @Mock EventDedupService dedupService;
    @Mock RecordLoginHistoryUseCase recordLoginHistoryUseCase;
    @Mock DetectSuspiciousActivityUseCase detectUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private LoginFailedConsumer consumer() {
        return new LoginFailedConsumer(objectMapper, dedupService, recordLoginHistoryUseCase, detectUseCase);
    }

    private ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("auth.login.failed", 0, 0L, "key", value);
    }

    @Test
    @DisplayName("failureReason 없음 → outcome=FAILURE")
    void onMessage_noFailureReason_executesWithFailureOutcome() {
        when(dedupService.isDuplicate("evt-fail-1")).thenReturn(false);
        when(recordLoginHistoryUseCase.execute(any(LoginHistoryEntry.class), anyString())).thenReturn(true);

        // TASK-BE-248 Phase 2a: tenantId required — consumer rejects events without it.
        String json = """
                {
                  "eventId": "evt-fail-1",
                  "eventType": "auth.login.failed",
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
        assertThat(captor.getValue().getOutcome()).isEqualTo(LoginOutcome.FAILURE);
    }

    @Test
    @DisplayName("failureReason=RATE_LIMITED → outcome=RATE_LIMITED (resolveOutcome 재정의)")
    void onMessage_rateLimitedReason_executesWithRateLimitedOutcome() {
        when(dedupService.isDuplicate("evt-fail-2")).thenReturn(false);
        when(recordLoginHistoryUseCase.execute(any(LoginHistoryEntry.class), anyString())).thenReturn(true);

        String json = """
                {
                  "eventId": "evt-fail-2",
                  "eventType": "auth.login.failed",
                  "tenantId": "fan-platform",
                  "occurredAt": "2026-04-29T10:00:00Z",
                  "payload": {
                    "accountId": "acc-2",
                    "failureReason": "RATE_LIMITED",
                    "timestamp": "2026-04-29T10:00:00Z"
                  }
                }
                """;

        consumer().onMessage(record(json));

        ArgumentCaptor<LoginHistoryEntry> captor = ArgumentCaptor.forClass(LoginHistoryEntry.class);
        verify(recordLoginHistoryUseCase).execute(captor.capture(), anyString());
        assertThat(captor.getValue().getOutcome()).isEqualTo(LoginOutcome.RATE_LIMITED);
    }

    @Test
    @DisplayName("Redis dedup hit — execute 미호출")
    void onMessage_redisDedupHit_skipsExecution() {
        when(dedupService.isDuplicate("evt-fail-dup")).thenReturn(true);

        String json = """
                {
                  "eventId": "evt-fail-dup",
                  "eventType": "auth.login.failed",
                  "tenantId": "fan-platform",
                  "occurredAt": "2026-04-29T10:00:00Z",
                  "payload": { "accountId": "acc-3", "timestamp": "2026-04-29T10:00:00Z" }
                }
                """;

        consumer().onMessage(record(json));

        verify(recordLoginHistoryUseCase, never()).execute(any(), any());
    }

    @Test
    @DisplayName("TASK-BE-248: tenant_id 누락 메시지 → MissingTenantIdException (DLQ 라우팅 트리거)")
    void onMessage_missingTenantId_throwsMissingTenantIdException() {
        String json = """
                {
                  "eventId": "evt-no-tenant-fail",
                  "eventType": "auth.login.failed",
                  "occurredAt": "2026-04-29T10:00:00Z",
                  "payload": { "accountId": "acc-x", "timestamp": "2026-04-29T10:00:00Z" }
                }
                """;

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> consumer().onMessage(record(json)))
                .isInstanceOf(MissingTenantIdException.class)
                .hasMessageContaining("evt-no-tenant-fail");

        verify(recordLoginHistoryUseCase, never()).execute(any(), any());
    }
}
