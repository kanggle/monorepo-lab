package com.example.messaging.mdc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MessagingMdc")
class MessagingMdcTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("withTraceId pushes value and restores on close")
    void withTraceIdPushesAndRestores() {
        try (var ignored = MessagingMdc.withTraceId("trace-1")) {
            assertThat(MDC.get(MessagingMdc.TRACE_ID_KEY)).isEqualTo("trace-1");
        }
        assertThat(MDC.get(MessagingMdc.TRACE_ID_KEY)).isNull();
    }

    @Test
    @DisplayName("withTraceId restores previous value on close")
    void withTraceIdRestoresPrevious() {
        MDC.put(MessagingMdc.TRACE_ID_KEY, "outer");
        try (var ignored = MessagingMdc.withTraceId("inner")) {
            assertThat(MDC.get(MessagingMdc.TRACE_ID_KEY)).isEqualTo("inner");
        }
        assertThat(MDC.get(MessagingMdc.TRACE_ID_KEY)).isEqualTo("outer");
    }

    @Test
    @DisplayName("blank value is a no-op")
    void blankValueIsNoop() {
        MDC.put(MessagingMdc.EVENT_ID_KEY, "outer");
        try (var ignored = MessagingMdc.withEventId(" ")) {
            assertThat(MDC.get(MessagingMdc.EVENT_ID_KEY)).isEqualTo("outer");
        }
        assertThat(MDC.get(MessagingMdc.EVENT_ID_KEY)).isEqualTo("outer");
    }

    @Test
    @DisplayName("null value is a no-op")
    void nullValueIsNoop() {
        try (var ignored = MessagingMdc.withConsumerLabel(null)) {
            assertThat(MDC.get(MessagingMdc.CONSUMER_LABEL_KEY)).isNull();
        }
    }

    @Test
    @DisplayName("withEnvelope pushes both keys and restores both")
    void withEnvelopePushesBoth() {
        try (var ignored = MessagingMdc.withEnvelope("trace-1", "evt-1")) {
            assertThat(MDC.get(MessagingMdc.TRACE_ID_KEY)).isEqualTo("trace-1");
            assertThat(MDC.get(MessagingMdc.EVENT_ID_KEY)).isEqualTo("evt-1");
        }
        assertThat(MDC.get(MessagingMdc.TRACE_ID_KEY)).isNull();
        assertThat(MDC.get(MessagingMdc.EVENT_ID_KEY)).isNull();
    }
}
