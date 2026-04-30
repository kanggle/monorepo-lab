package com.example.messaging.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link OutboxPollingScheduler} lifecycle (TASK-BE-079) and
 * topic resolution (TASK-BE-120, indirect via {@code pollAndPublish}).
 *
 * <p>Verifies that:
 * <ul>
 *   <li>{@link OutboxPollingScheduler#start()} registers a fixed-delay task on
 *       the injected {@link ThreadPoolTaskScheduler}.</li>
 *   <li>{@link OutboxPollingScheduler#stop()} cancels the returned
 *       {@link ScheduledFuture}.</li>
 *   <li>Once stopped, {@link OutboxPollingScheduler#pollAndPublish()} returns
 *       early without touching {@link OutboxPublisher}.</li>
 *   <li>Topic resolution (now {@code private}) is exercised indirectly via
 *       {@code pollAndPublish()}: known event types are routed to the mapped
 *       Kafka topic, unknown event types cause a Kafka send to be skipped and
 *       trigger the {@link OutboxFailureHandler}.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("OutboxPollingScheduler 수명주기 단위 테스트")
class OutboxPollingSchedulerTest {

    @Mock private OutboxPublisher outboxPublisher;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private ThreadPoolTaskScheduler taskScheduler;
    @Mock private ScheduledFuture<?> scheduledFuture;
    @Mock private OutboxFailureHandler failureHandler;

    private OutboxPollingScheduler scheduler;

    @BeforeEach
    void setUp() {
        OutboxProperties props = new OutboxProperties();
        props.setTopicMapping(Map.of("test.event", "test-topic"));
        scheduler = new OutboxPollingScheduler(outboxPublisher, kafkaTemplate, taskScheduler, props, failureHandler);
        ReflectionTestUtils.setField(scheduler, "intervalMs", 1000L);
    }

    @Test
    @DisplayName("start() 호출 시 taskScheduler.scheduleWithFixedDelay 로 폴링 태스크를 등록한다")
    void start_registersFixedDelayTask() {
        given(taskScheduler.scheduleWithFixedDelay(any(Runnable.class), any(Duration.class)))
                .willAnswer(invocation -> scheduledFuture);

        scheduler.start();

        verify(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
    }

    @Test
    @DisplayName("stop() 호출 시 ScheduledFuture.cancel(false) 로 태스크를 취소한다")
    void stop_cancelsScheduledFuture() {
        given(taskScheduler.scheduleWithFixedDelay(any(Runnable.class), any(Duration.class)))
                .willAnswer(invocation -> scheduledFuture);
        scheduler.start();

        scheduler.stop();

        verify(scheduledFuture).cancel(false);
    }

    @Test
    @DisplayName("running=false 상태에서 pollAndPublish() 는 조기 반환하여 publisher 를 호출하지 않는다")
    void pollAndPublish_afterStop_returnsEarly() {
        given(taskScheduler.scheduleWithFixedDelay(any(Runnable.class), any(Duration.class)))
                .willAnswer(invocation -> scheduledFuture);
        scheduler.start();
        scheduler.stop();

        scheduler.pollAndPublish();

        verifyNoInteractions(outboxPublisher);
    }

    @Test
    @DisplayName("topicMapping 에 있는 이벤트 타입은 매핑된 Kafka 토픽으로 send 된다 (resolveTopic 간접 검증)")
    void pollAndPublish_knownEventType_sendsToMappedTopic() {
        // Stub the publisher to invoke the EventSender lambda once with a known eventType.
        doAnswer(invocation -> {
            OutboxPublisher.EventSender sender = invocation.getArgument(0);
            sender.send("test.event", "agg-1", "{\"k\":\"v\"}");
            return null;
        }).when(outboxPublisher).publishPendingEvents(any());

        // Kafka send returns a successful future so sendToKafka() returns true.
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        given(kafkaTemplate.send(anyString(), anyString(), anyString())).willReturn(future);

        scheduler.pollAndPublish();

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), eq("agg-1"), eq("{\"k\":\"v\"}"));
        assertThat(topicCaptor.getValue()).isEqualTo("test-topic");
        verify(failureHandler, never()).onFailure(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("topicMapping 에 없는 이벤트 타입은 Kafka 로 send 하지 않고 failureHandler 를 호출한다 (resolveTopic 간접 검증)")
    void pollAndPublish_unknownEventType_invokesFailureHandlerAndSkipsSend() {
        doAnswer(invocation -> {
            OutboxPublisher.EventSender sender = invocation.getArgument(0);
            boolean ok = sender.send("unknown.event", "agg-2", "payload");
            assertThat(ok).isFalse();
            return null;
        }).when(outboxPublisher).publishPendingEvents(any());

        scheduler.pollAndPublish();

        // resolveTopic throws BEFORE kafkaTemplate.send is invoked (the throw
        // happens inside sendToKafka before the kafkaTemplate.send call).
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        verify(failureHandler, atLeastOnce())
                .onFailure(eq("unknown.event"), eq("agg-2"), any(IllegalStateException.class));
    }
}
