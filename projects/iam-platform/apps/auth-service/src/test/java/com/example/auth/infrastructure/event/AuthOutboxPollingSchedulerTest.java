package com.example.auth.infrastructure.event;

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
@DisplayName("AuthOutboxPollingScheduler 단위 테스트")
class AuthOutboxPollingSchedulerTest {

    @Mock
    private OutboxPublisher outboxPublisher;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private AuthOutboxPollingScheduler scheduler() {
        return new AuthOutboxPollingScheduler(outboxPublisher, kafkaTemplate);
    }

    @Test
    @DisplayName("auth.* 이벤트 타입을 동일 이름의 토픽으로 매핑한다")
    void resolveTopic_authEventTypes_mappedToIdenticalTopic() {
        AuthOutboxPollingScheduler s = scheduler();

        assertThat(s.resolveTopic("auth.login.attempted")).isEqualTo("auth.login.attempted");
        assertThat(s.resolveTopic("auth.login.failed")).isEqualTo("auth.login.failed");
        assertThat(s.resolveTopic("auth.login.succeeded")).isEqualTo("auth.login.succeeded");
        assertThat(s.resolveTopic("auth.token.refreshed")).isEqualTo("auth.token.refreshed");
        assertThat(s.resolveTopic("auth.token.reuse.detected")).isEqualTo("auth.token.reuse.detected");
        assertThat(s.resolveTopic("auth.token.tenant.mismatch")).isEqualTo("auth.token.tenant.mismatch");
        assertThat(s.resolveTopic("auth.session.created")).isEqualTo("auth.session.created");
        assertThat(s.resolveTopic("auth.session.revoked")).isEqualTo("auth.session.revoked");
    }

    @Test
    @DisplayName("알 수 없는 이벤트 타입은 IllegalArgumentException을 던진다")
    void resolveTopic_unknownEventType_throws() {
        assertThatThrownBy(() -> scheduler().resolveTopic("unknown.event"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown auth event type");
    }
}
