package com.example.account.infrastructure.event;

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
@DisplayName("AccountOutboxPollingScheduler 단위 테스트")
class AccountOutboxPollingSchedulerTest {

    @Mock
    private OutboxPublisher outboxPublisher;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private AccountOutboxPollingScheduler scheduler() {
        return new AccountOutboxPollingScheduler(outboxPublisher, kafkaTemplate);
    }

    @Test
    @DisplayName("account.* 이벤트 타입을 동일 이름의 토픽으로 매핑한다")
    void resolveTopic_accountEventTypes_mappedToIdenticalTopic() {
        AccountOutboxPollingScheduler s = scheduler();

        assertThat(s.resolveTopic("account.created")).isEqualTo("account.created");
        assertThat(s.resolveTopic("account.status.changed")).isEqualTo("account.status.changed");
        assertThat(s.resolveTopic("account.locked")).isEqualTo("account.locked");
        assertThat(s.resolveTopic("account.unlocked")).isEqualTo("account.unlocked");
        assertThat(s.resolveTopic("account.roles.changed")).isEqualTo("account.roles.changed");
        assertThat(s.resolveTopic("account.deleted")).isEqualTo("account.deleted");
    }

    @Test
    @DisplayName("TASK-BE-348 — tenant.subscription.changed 를 동일 이름의 토픽으로 매핑한다 (BE-342 누락 보완)")
    void resolveTopic_subscriptionChanged_mappedToContractTopic() {
        // account-events.md § tenant.subscription.changed declares Topic =
        // "tenant.subscription.changed"; the resolver must honour it so the
        // entitlement-plane event is published instead of terminally FAILED.
        assertThat(scheduler().resolveTopic("tenant.subscription.changed"))
                .isEqualTo("tenant.subscription.changed");
    }

    @Test
    @DisplayName("알 수 없는 이벤트 타입은 IllegalArgumentException을 던진다")
    void resolveTopic_unknownEventType_throws() {
        assertThatThrownBy(() -> scheduler().resolveTopic("unknown.event"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown account event type");
    }
}
