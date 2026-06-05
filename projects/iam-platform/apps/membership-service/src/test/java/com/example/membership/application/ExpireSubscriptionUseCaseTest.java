package com.example.membership.application;

import com.example.membership.application.event.MembershipEventPublisher;
import com.example.membership.application.exception.SubscriptionNotFoundException;
import com.example.membership.domain.plan.PlanLevel;
import com.example.membership.domain.subscription.Subscription;
import com.example.membership.domain.subscription.SubscriptionRepository;
import com.example.membership.domain.subscription.status.SubscriptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("ExpireSubscriptionUseCase 단위 테스트")
class ExpireSubscriptionUseCaseTest {

    @Mock SubscriptionRepository subscriptionRepository;
    @Mock SubscriptionStatusHistoryRecorder historyRecorder;
    @Mock MembershipEventPublisher eventPublisher;

    @InjectMocks ExpireSubscriptionUseCase useCase;

    @Test
    @DisplayName("ACTIVE 구독 만료 시 저장·히스토리·이벤트 발행이 모두 1회 호출된다")
    void expire_activeSubscription_savesHistoryAndPublishesEvent() throws Exception {
        Subscription s = subscriptionWith("sub-1", "acc-1", SubscriptionStatus.ACTIVE);
        given(subscriptionRepository.findById("sub-1")).willReturn(Optional.of(s));
        given(subscriptionRepository.save(s)).willReturn(s);

        useCase.expire("sub-1");

        verify(subscriptionRepository).save(s);
        verify(historyRecorder).recordTransition(
                eq(s),
                eq(SubscriptionStatus.ACTIVE),
                eq(SubscriptionStatus.EXPIRED),
                eq("SCHEDULED_EXPIRE"),
                eq("SYSTEM"),
                any(LocalDateTime.class));
        verify(eventPublisher).publishExpired(s);
    }

    @Test
    @DisplayName("ACTIVE 상태가 아닌 구독은 만료 처리를 스킵하고 히스토리·이벤트를 발행하지 않는다")
    void expire_nonActiveSubscription_skipsHistoryAndEvent() throws Exception {
        Subscription s = subscriptionWith("sub-1", "acc-1", SubscriptionStatus.EXPIRED);
        given(subscriptionRepository.findById("sub-1")).willReturn(Optional.of(s));

        useCase.expire("sub-1");

        verify(subscriptionRepository, never()).save(any());
        verify(historyRecorder, never()).recordTransition(any(), any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishExpired(any());
    }

    @Test
    @DisplayName("구독 ID에 해당하는 구독이 없으면 SubscriptionNotFoundException을 던진다")
    void expire_subscriptionNotFound_throwsNotFoundException() {
        given(subscriptionRepository.findById("missing")).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.expire("missing"))
                .isInstanceOf(SubscriptionNotFoundException.class);

        verify(subscriptionRepository, never()).save(any());
        verify(historyRecorder, never()).recordTransition(any(), any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishExpired(any());
    }

    private static Subscription subscriptionWith(String id, String accountId, SubscriptionStatus status)
            throws Exception {
        var ctor = Subscription.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        Subscription s = ctor.newInstance();
        setField(s, "id", id);
        setField(s, "accountId", accountId);
        setField(s, "planLevel", PlanLevel.FAN_CLUB);
        setField(s, "status", status);
        setField(s, "startedAt", LocalDateTime.of(2026, 4, 1, 0, 0, 0));
        setField(s, "expiresAt", LocalDateTime.of(2026, 4, 30, 0, 0, 0));
        setField(s, "createdAt", LocalDateTime.of(2026, 4, 1, 0, 0, 0));
        return s;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
