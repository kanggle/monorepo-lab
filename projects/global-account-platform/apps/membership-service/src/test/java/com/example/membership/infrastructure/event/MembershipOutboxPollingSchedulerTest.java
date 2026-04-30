package com.example.membership.infrastructure.event;

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
@DisplayName("MembershipOutboxPollingScheduler 단위 테스트")
class MembershipOutboxPollingSchedulerTest {

    @Mock
    private OutboxPublisher outboxPublisher;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private MembershipOutboxPollingScheduler scheduler() {
        return new MembershipOutboxPollingScheduler(outboxPublisher, kafkaTemplate);
    }

    @Test
    @DisplayName("membership.subscription.* 이벤트 타입을 동일 이름의 토픽으로 매핑한다")
    void resolveTopic_membershipEventTypes_mappedToIdenticalTopic() {
        MembershipOutboxPollingScheduler s = scheduler();

        assertThat(s.resolveTopic("membership.subscription.activated"))
                .isEqualTo("membership.subscription.activated");
        assertThat(s.resolveTopic("membership.subscription.expired"))
                .isEqualTo("membership.subscription.expired");
        assertThat(s.resolveTopic("membership.subscription.cancelled"))
                .isEqualTo("membership.subscription.cancelled");
    }

    @Test
    @DisplayName("알 수 없는 이벤트 타입은 IllegalArgumentException을 던진다")
    void resolveTopic_unknownEventType_throws() {
        assertThatThrownBy(() -> scheduler().resolveTopic("unknown.event"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown membership event type");
    }
}
