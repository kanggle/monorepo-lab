package com.example.auth.infrastructure.config;

import com.example.auth.infrastructure.messaging.InvalidEventPayloadException;
import com.example.auth.infrastructure.messaging.MissingTenantIdException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * TASK-BE-601 — which failures are retried before the DLQ and which go straight there.
 * A failed revoke MUST be retried (a transient DB/Redis error must not park a lock in the
 * DLQ on the first try); a payload that cannot be read MUST NOT be.
 */
@DisplayName("KafkaConsumerConfig — 재시도 분류 (TASK-BE-601)")
class KafkaConsumerConfigTest {

    @SuppressWarnings("unchecked")
    private final DefaultErrorHandler handler = new KafkaConsumerConfig().kafkaConsumerErrorHandler(
            mock(KafkaTemplate.class), new SimpleMeterRegistry());

    /** DefaultErrorHandler#removeClassification returns the previous verdict (true = retryable). */
    private boolean retryable(Class<? extends Exception> type) {
        Boolean previous = handler.removeClassification(type);
        return previous == null || previous;
    }

    @Test
    @DisplayName("tenantId 누락 · 읽을 수 없는 봉투 → 재시도 없이 DLQ")
    void unprocessablePayloads_areNotRetried() {
        assertThat(retryable(MissingTenantIdException.class)).isFalse();
        assertThat(retryable(InvalidEventPayloadException.class)).isFalse();
    }

    @Test
    @DisplayName("폐기 실패(일반 런타임 예외) → 재시도 대상")
    void revokeFailures_areRetried() {
        assertThat(retryable(IllegalStateException.class)).isTrue();
    }

    @Test
    @DisplayName("hasCause — 리스너 래핑 예외 안의 원인을 찾는다 (DLQ reason 태그)")
    void hasCause_seesThroughListenerWrapping() {
        Exception wrapped = new ListenerExecutionFailedException("listener failed",
                new MissingTenantIdException("e-1", "account.locked"));
        assertThat(KafkaConsumerConfig.hasCause(wrapped, MissingTenantIdException.class)).isTrue();
        assertThat(KafkaConsumerConfig.hasCause(wrapped, InvalidEventPayloadException.class)).isFalse();
    }
}
