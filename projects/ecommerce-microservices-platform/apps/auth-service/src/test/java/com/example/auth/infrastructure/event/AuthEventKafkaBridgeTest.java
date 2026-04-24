package com.example.auth.infrastructure.event;

import com.example.auth.domain.event.AuthEvent;
import com.example.auth.domain.event.UserLoggedIn;
import com.example.auth.domain.event.UserSignedUp;
import com.example.auth.domain.service.AuthMetricsRecorder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("AuthEventKafkaBridge 단위 테스트")
class AuthEventKafkaBridgeTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private AuthMetricsRecorder authMetrics;

    private AuthEventKafkaBridge bridge;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        bridge = new AuthEventKafkaBridge(kafkaTemplate, objectMapper, authMetrics);
    }

    @Test
    @DisplayName("UserSignedUp 이벤트 수신 시 auth.user.signed-up 토픽으로 발행한다")
    void handle_userSignedUp_sendsToCorrectTopic() throws Exception {
        UUID userId = UUID.randomUUID();
        AuthEvent event = AuthEvent.of(new UserSignedUp(userId, "user@example.com", "홍길동"));

        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(
                mockSendResult("auth.user.signed-up"));
        given(kafkaTemplate.send(eq("auth.user.signed-up"), anyString(), anyString())).willReturn(future);

        bridge.handle(event);

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        then(kafkaTemplate).should().send(topic.capture(), key.capture(), payload.capture());

        assertThat(topic.getValue()).isEqualTo("auth.user.signed-up");
        assertThat(key.getValue()).isEqualTo(event.eventId().toString());

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = objectMapper.readValue(payload.getValue(), Map.class);
        assertThat(parsed).containsEntry("event_type", "UserSignedUp");
        assertThat(parsed).containsEntry("source", "auth-service");
        assertThat(parsed).containsKey("event_id");
        assertThat(parsed).containsKey("occurred_at");
        assertThat(parsed).containsKey("payload");

        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) parsed.get("payload");
        assertThat(inner).containsEntry("userId", userId.toString());
        assertThat(inner).containsEntry("email", "user@example.com");
        assertThat(inner).containsEntry("name", "홍길동");
    }

    @Test
    @DisplayName("매핑되지 않은 이벤트 타입은 Kafka로 발행하지 않는다")
    void handle_unmappedEvent_skipsKafkaSend() {
        AuthEvent event = AuthEvent.of(new UserLoggedIn(UUID.randomUUID(), "user@example.com", "127.0.0.1", "agent"));

        bridge.handle(event);

        then(kafkaTemplate).should(never()).send(anyString(), anyString(), anyString());
        then(authMetrics).should(never()).incrementEventPublishFailure(anyString());
    }

    @Test
    @DisplayName("Kafka send 비동기 실패 시 event_publish_failure 메트릭이 증가한다")
    void handle_asyncSendFailure_incrementsMetric() {
        AuthEvent event = AuthEvent.of(new UserSignedUp(UUID.randomUUID(), "user@example.com", "홍길동"));
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("broker down"));
        given(kafkaTemplate.send(anyString(), anyString(), anyString())).willReturn(future);

        bridge.handle(event);

        then(authMetrics).should().incrementEventPublishFailure("UserSignedUp");
    }

    @Test
    @DisplayName("Kafka send 동기 예외 발생 시 메트릭을 증가시키고 signup 흐름을 깨지 않는다")
    void handle_syncSendException_incrementsMetricAndSwallows() {
        AuthEvent event = AuthEvent.of(new UserSignedUp(UUID.randomUUID(), "user@example.com", "홍길동"));
        given(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .willThrow(new RuntimeException("kafka unavailable"));

        bridge.handle(event);

        then(authMetrics).should().incrementEventPublishFailure("UserSignedUp");
    }

    @Test
    @DisplayName("직렬화 실패 시 메트릭을 증가시키고 Kafka로 발행하지 않는다")
    void handle_serializationFailure_incrementsMetric() throws Exception {
        ObjectMapper failing = new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws JsonProcessingException {
                throw new JsonProcessingException("boom") {};
            }
        };
        AuthEventKafkaBridge failingBridge = new AuthEventKafkaBridge(kafkaTemplate, failing, authMetrics);
        AuthEvent event = AuthEvent.of(new UserSignedUp(UUID.randomUUID(), "user@example.com", "홍길동"));

        failingBridge.handle(event);

        then(kafkaTemplate).should(never()).send(anyString(), anyString(), anyString());
        then(authMetrics).should().incrementEventPublishFailure("UserSignedUp");
    }

    private SendResult<String, String> mockSendResult(String topic) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, "k", "v");
        RecordMetadata md = new RecordMetadata(new TopicPartition(topic, 0), 0L, 0, 0L, 0, 0);
        return new SendResult<>(record, md);
    }
}
