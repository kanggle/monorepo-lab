package com.example.membership.application;

import com.example.membership.domain.plan.PlanLevel;
import com.example.membership.domain.subscription.Subscription;
import com.example.membership.domain.subscription.SubscriptionStatusHistoryEntry;
import com.example.membership.domain.subscription.SubscriptionStatusHistoryRepository;
import com.example.membership.domain.subscription.status.SubscriptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("SubscriptionStatusHistoryRecorder")
class SubscriptionStatusHistoryRecorderTest {

    @Mock SubscriptionStatusHistoryRepository historyRepository;

    SubscriptionStatusHistoryRecorder recorder;

    @BeforeEach
    void setup() {
        recorder = new SubscriptionStatusHistoryRecorder(historyRepository);
    }

    @Test
    @DisplayName("recordTransition 호출 시 historyRepository.append에 입력 파라미터로 구성된 엔트리가 1회 전달된다")
    void recordTransition_validInput_appendsEntryOnce() throws Exception {
        Subscription subscription = subscriptionWithIds("sub-1", "acc-1");
        LocalDateTime now = LocalDateTime.of(2026, 4, 27, 12, 0, 0);

        recorder.recordTransition(subscription,
                SubscriptionStatus.NONE, SubscriptionStatus.ACTIVE,
                "USER_SUBSCRIBE", "USER", now);

        ArgumentCaptor<SubscriptionStatusHistoryEntry> captor =
                ArgumentCaptor.forClass(SubscriptionStatusHistoryEntry.class);
        verify(historyRepository).append(captor.capture());

        SubscriptionStatusHistoryEntry entry = captor.getValue();
        assertThat(entry.subscriptionId()).isEqualTo("sub-1");
        assertThat(entry.accountId()).isEqualTo("acc-1");
        assertThat(entry.fromStatus()).isEqualTo(SubscriptionStatus.NONE);
        assertThat(entry.toStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(entry.reason()).isEqualTo("USER_SUBSCRIBE");
        assertThat(entry.actorType()).isEqualTo("USER");
        assertThat(entry.occurredAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("recordTransition은 subscription의 id와 accountId를 그대로 엔트리에 복사한다")
    void recordTransition_validSubscription_copiesIdAndAccountId() throws Exception {
        Subscription subscription = subscriptionWithIds("sub-cancel-42", "acc-99");
        LocalDateTime now = LocalDateTime.of(2026, 4, 27, 13, 30, 0);

        recorder.recordTransition(subscription,
                SubscriptionStatus.ACTIVE, SubscriptionStatus.CANCELLED,
                "USER_CANCEL", "USER", now);

        ArgumentCaptor<SubscriptionStatusHistoryEntry> captor =
                ArgumentCaptor.forClass(SubscriptionStatusHistoryEntry.class);
        verify(historyRepository).append(captor.capture());
        SubscriptionStatusHistoryEntry entry = captor.getValue();
        assertThat(entry.subscriptionId()).isEqualTo("sub-cancel-42");
        assertThat(entry.accountId()).isEqualTo("acc-99");
    }

    @Test
    @DisplayName("recordTransition은 from·to·operationCode·actorType·occurredAt 파라미터를 변형 없이 엔트리에 전달한다")
    void recordTransition_systemExpire_passesThroughTransitionMetadata() throws Exception {
        Subscription subscription = subscriptionWithIds("sub-2", "acc-2");
        LocalDateTime now = LocalDateTime.of(2026, 5, 1, 0, 0, 0);

        recorder.recordTransition(subscription,
                SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED,
                "SCHEDULED_EXPIRE", "SYSTEM", now);

        ArgumentCaptor<SubscriptionStatusHistoryEntry> captor =
                ArgumentCaptor.forClass(SubscriptionStatusHistoryEntry.class);
        verify(historyRepository).append(captor.capture());
        SubscriptionStatusHistoryEntry entry = captor.getValue();
        assertThat(entry.fromStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(entry.toStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(entry.reason()).isEqualTo("SCHEDULED_EXPIRE");
        assertThat(entry.actorType()).isEqualTo("SYSTEM");
        assertThat(entry.occurredAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("historyRepository.append가 예외를 던지면 recordTransition이 그대로 전파한다")
    void recordTransition_repositoryThrows_propagatesException() throws Exception {
        Subscription subscription = subscriptionWithIds("sub-3", "acc-3");
        LocalDateTime now = LocalDateTime.of(2026, 4, 27, 9, 0, 0);
        doThrow(new IllegalStateException("db down"))
                .when(historyRepository).append(org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> recorder.recordTransition(subscription,
                SubscriptionStatus.NONE, SubscriptionStatus.ACTIVE,
                "USER_SUBSCRIBE", "USER", now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db down");
    }

    private static Subscription subscriptionWithIds(String id, String accountId) throws Exception {
        var ctor = Subscription.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        Subscription s = ctor.newInstance();
        setField(s, "id", id);
        setField(s, "accountId", accountId);
        setField(s, "planLevel", PlanLevel.FAN_CLUB);
        setField(s, "status", SubscriptionStatus.ACTIVE);
        return s;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
