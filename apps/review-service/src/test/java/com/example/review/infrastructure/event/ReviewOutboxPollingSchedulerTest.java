package com.example.review.infrastructure.event;

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
@DisplayName("ReviewOutboxPollingScheduler 단위 테스트")
class ReviewOutboxPollingSchedulerTest {

    @Mock
    private OutboxPublisher outboxPublisher;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    @DisplayName("ReviewCreated 이벤트 타입을 올바른 토픽으로 매핑한다")
    void resolveTopic_reviewCreated_returnsCorrectTopic() {
        ReviewOutboxPollingScheduler scheduler =
                new ReviewOutboxPollingScheduler(outboxPublisher, kafkaTemplate);

        String topic = scheduler.resolveTopic("ReviewCreated");

        assertThat(topic).isEqualTo(ReviewOutboxPollingScheduler.TOPIC_REVIEW_CREATED);
    }

    @Test
    @DisplayName("ReviewUpdated 이벤트 타입을 올바른 토픽으로 매핑한다")
    void resolveTopic_reviewUpdated_returnsCorrectTopic() {
        ReviewOutboxPollingScheduler scheduler =
                new ReviewOutboxPollingScheduler(outboxPublisher, kafkaTemplate);

        String topic = scheduler.resolveTopic("ReviewUpdated");

        assertThat(topic).isEqualTo(ReviewOutboxPollingScheduler.TOPIC_REVIEW_UPDATED);
    }

    @Test
    @DisplayName("ReviewDeleted 이벤트 타입을 올바른 토픽으로 매핑한다")
    void resolveTopic_reviewDeleted_returnsCorrectTopic() {
        ReviewOutboxPollingScheduler scheduler =
                new ReviewOutboxPollingScheduler(outboxPublisher, kafkaTemplate);

        String topic = scheduler.resolveTopic("ReviewDeleted");

        assertThat(topic).isEqualTo(ReviewOutboxPollingScheduler.TOPIC_REVIEW_DELETED);
    }

    @Test
    @DisplayName("알 수 없는 이벤트 타입에 대해 예외를 던진다")
    void resolveTopic_unknownEventType_throwsException() {
        ReviewOutboxPollingScheduler scheduler =
                new ReviewOutboxPollingScheduler(outboxPublisher, kafkaTemplate);

        assertThatThrownBy(() -> scheduler.resolveTopic("UnknownEvent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown review event type");
    }

    @Test
    @DisplayName("토픽 상수가 event-driven-policy 규칙을 따른다")
    void topicConstants_followNamingConvention() {
        assertThat(ReviewOutboxPollingScheduler.TOPIC_REVIEW_CREATED)
                .isEqualTo("review.review.created");
        assertThat(ReviewOutboxPollingScheduler.TOPIC_REVIEW_UPDATED)
                .isEqualTo("review.review.updated");
        assertThat(ReviewOutboxPollingScheduler.TOPIC_REVIEW_DELETED)
                .isEqualTo("review.review.deleted");
    }
}
