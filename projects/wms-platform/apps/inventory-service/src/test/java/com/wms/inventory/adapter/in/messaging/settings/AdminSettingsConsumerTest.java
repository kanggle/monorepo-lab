package com.wms.inventory.adapter.in.messaging.settings;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inventory.application.port.out.EventDedupePort;
import com.wms.inventory.application.port.out.LowStockThresholdWriterPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

@DisplayName("AdminSettingsConsumer 단위 테스트 (TASK-BE-459)")
class AdminSettingsConsumerTest {

    private LowStockThresholdWriterPort thresholdWriter;
    private EventDedupePort dedupe;
    private AdminSettingsConsumer consumer;

    /** dedupe mock that runs the supplied work (as APPLIED) — the real dedupe path. */
    private static final Answer<EventDedupePort.Outcome> RUN_WORK = inv -> {
        inv.getArgument(2, Runnable.class).run();
        return EventDedupePort.Outcome.APPLIED;
    };

    private static String envelope(String key, String scope, String valueJson) {
        return """
                {
                  "eventId": "0191d8f0-1f0e-7c40-9d13-4a2c9e3f5678",
                  "eventType": "admin.settings.changed",
                  "eventVersion": 1,
                  "occurredAt": "2026-07-09T10:00:00.000Z",
                  "producer": "admin-service",
                  "aggregateType": "setting",
                  "aggregateId": "%s",
                  "payload": {
                    "key": "%s",
                    "scope": "%s",
                    "warehouseId": null,
                    "valueJson": %s,
                    "previousValueJson": 10,
                    "version": 3
                  }
                }
                """.formatted(key, key, scope, valueJson);
    }

    @BeforeEach
    void setUp() {
        thresholdWriter = Mockito.mock(LowStockThresholdWriterPort.class);
        dedupe = Mockito.mock(EventDedupePort.class);
        consumer = new AdminSettingsConsumer(new SettingsEventParser(new ObjectMapper()), thresholdWriter, dedupe);
    }

    @Test
    @DisplayName("low-stock GLOBAL 키 → 임계값 갱신")
    void lowStockGlobalKey_updatesThreshold() {
        when(dedupe.process(any(), any(), any())).thenAnswer(RUN_WORK);

        consumer.handle(envelope("inventory.low_stock.default_threshold_qty", "GLOBAL", "25"), "k");

        verify(thresholdWriter).updateDefaultThreshold(25);
    }

    @Test
    @DisplayName("다른 setting 키 → dedupe/writer 미호출 (필터로 무시)")
    void otherKey_ignoredBeforeDedupe() {
        consumer.handle(envelope("inventory.reservation.ttl_hours", "GLOBAL", "36"), "k");

        verifyNoInteractions(dedupe);
        verifyNoInteractions(thresholdWriter);
    }

    @Test
    @DisplayName("low-stock 이나 non-GLOBAL scope → dedupe 기록되나 임계값 미갱신")
    void lowStockNonGlobal_recordedButNotApplied() {
        when(dedupe.process(any(), any(), any())).thenAnswer(RUN_WORK);

        consumer.handle(envelope("inventory.low_stock.default_threshold_qty", "WAREHOUSE", "5"), "k");

        verify(dedupe).process(any(), eq("admin.settings.changed"), any());
        verify(thresholdWriter, never()).updateDefaultThreshold(any());
    }

    @Test
    @DisplayName("valueJson 비정수 → IllegalArgumentException (non-retryable → DLT)")
    void nonIntegerValue_throwsNonRetryable() {
        when(dedupe.process(any(), any(), any())).thenAnswer(RUN_WORK);

        assertThatThrownBy(() ->
                consumer.handle(envelope("inventory.low_stock.default_threshold_qty", "GLOBAL", "\"oops\""), "k"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(thresholdWriter, never()).updateDefaultThreshold(any());
    }

    @Test
    @DisplayName("dedupe 중복 → work 미실행 → 임계값 미갱신")
    void duplicateEvent_skipsWork() {
        when(dedupe.process(any(), any(), any())).thenReturn(EventDedupePort.Outcome.IGNORED_DUPLICATE);

        consumer.handle(envelope("inventory.low_stock.default_threshold_qty", "GLOBAL", "25"), "k");

        verify(thresholdWriter, never()).updateDefaultThreshold(any());
    }

    @Test
    @DisplayName("malformed JSON → IllegalArgumentException (non-retryable)")
    void malformedJson_throws() {
        assertThatThrownBy(() -> consumer.handle("{ not json", "k"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
