package com.example.community.infrastructure.event;

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
@DisplayName("CommunityOutboxPollingScheduler 단위 테스트")
class CommunityOutboxPollingSchedulerTest {

    @Mock
    private OutboxPublisher outboxPublisher;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private CommunityOutboxPollingScheduler scheduler() {
        return new CommunityOutboxPollingScheduler(outboxPublisher, kafkaTemplate);
    }

    @Test
    @DisplayName("community.* 이벤트 타입을 동일 이름의 토픽으로 매핑한다")
    void resolveTopic_communityEventTypes_mappedToIdenticalTopic() {
        CommunityOutboxPollingScheduler s = scheduler();

        assertThat(s.resolveTopic("community.post.published")).isEqualTo("community.post.published");
        assertThat(s.resolveTopic("community.comment.created")).isEqualTo("community.comment.created");
        assertThat(s.resolveTopic("community.reaction.added")).isEqualTo("community.reaction.added");
    }

    @Test
    @DisplayName("알 수 없는 이벤트 타입은 IllegalArgumentException을 던진다")
    void resolveTopic_unknownEventType_throws() {
        assertThatThrownBy(() -> scheduler().resolveTopic("unknown.event"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown community event type");
    }
}
