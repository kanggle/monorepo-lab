package com.example.security.infrastructure.event;

import com.example.messaging.outbox.OutboxPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityOutboxPollingScheduler 단위 테스트")
class SecurityOutboxPollingSchedulerTest {

    @Mock
    private OutboxPublisher outboxPublisher;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private SecurityOutboxPollingScheduler scheduler() {
        return new SecurityOutboxPollingScheduler(outboxPublisher, kafkaTemplate);
    }

    @Test
    @DisplayName("security.* 이벤트 타입을 동일 이름의 토픽으로 매핑한다")
    void resolveTopic_securityEventTypes_mappedToIdenticalTopic() {
        SecurityOutboxPollingScheduler s = scheduler();

        assertThat(s.resolveTopic("security.suspicious.detected"))
                .isEqualTo("security.suspicious.detected");
        assertThat(s.resolveTopic("security.auto.lock.triggered"))
                .isEqualTo("security.auto.lock.triggered");
        assertThat(s.resolveTopic("security.auto.lock.pending"))
                .isEqualTo("security.auto.lock.pending");
    }

    @Test
    @DisplayName("알 수 없는 이벤트 타입은 IllegalArgumentException을 던진다")
    void resolveTopic_unknownEventType_throws() {
        assertThatThrownBy(() -> scheduler().resolveTopic("unknown.event"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown security event type");
    }
}
