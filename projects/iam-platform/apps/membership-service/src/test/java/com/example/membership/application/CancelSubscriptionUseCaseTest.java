package com.example.membership.application;

import com.example.membership.application.event.MembershipEventPublisher;
import com.example.membership.application.exception.SubscriptionNotActiveException;
import com.example.membership.application.exception.SubscriptionNotFoundException;
import com.example.membership.application.exception.SubscriptionPermissionDeniedException;
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

import java.time.LocalDateTime;
import java.util.Optional;

import static com.example.membership.support.ReflectionTestSupport.setField;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("CancelSubscriptionUseCase 단위 테스트")
class CancelSubscriptionUseCaseTest {

    @Mock SubscriptionRepository subscriptionRepository;
    @Mock SubscriptionStatusHistoryRecorder historyRecorder;
    @Mock MembershipEventPublisher eventPublisher;

    @InjectMocks CancelSubscriptionUseCase useCase;

    @Test
    @DisplayName("소유자가 ACTIVE 구독 해지 시 저장·히스토리·이벤트 발행이 모두 1회 호출된다")
    void cancel_ownerCancelsActiveSubscription_savesHistoryAndPublishesEvent() throws Exception {
        Subscription s = subscriptionWith("sub-1", "acc-1", SubscriptionStatus.ACTIVE);
        given(subscriptionRepository.findById("sub-1")).willReturn(Optional.of(s));
        given(subscriptionRepository.save(s)).willReturn(s);

        useCase.cancel("sub-1", "acc-1");

        verify(subscriptionRepository).save(s);
        verify(historyRecorder).recordTransition(
                eq(s),
                eq(SubscriptionStatus.ACTIVE),
                eq(SubscriptionStatus.CANCELLED),
                eq("USER_CANCEL"),
                eq("USER"),
                any(LocalDateTime.class));
        verify(eventPublisher).publishCancelled(s);
    }

    @Test
    @DisplayName("구독 ID에 해당하는 구독이 없으면 SubscriptionNotFoundException을 던진다")
    void cancel_subscriptionNotFound_throwsNotFoundException() {
        given(subscriptionRepository.findById("missing")).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.cancel("missing", "acc-1"))
                .isInstanceOf(SubscriptionNotFoundException.class);

        verify(subscriptionRepository, never()).save(any());
        verify(historyRecorder, never()).recordTransition(any(), any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishCancelled(any());
    }

    @Test
    @DisplayName("타인의 구독을 해지하려 하면 SubscriptionPermissionDeniedException을 던진다")
    void cancel_requesterIsNotOwner_throwsPermissionDeniedException() throws Exception {
        Subscription s = subscriptionWith("sub-1", "acc-owner", SubscriptionStatus.ACTIVE);
        given(subscriptionRepository.findById("sub-1")).willReturn(Optional.of(s));

        assertThatThrownBy(() -> useCase.cancel("sub-1", "acc-other"))
                .isInstanceOf(SubscriptionPermissionDeniedException.class);

        verify(subscriptionRepository, never()).save(any());
        verify(historyRecorder, never()).recordTransition(any(), any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishCancelled(any());
    }

    @Test
    @DisplayName("ACTIVE 상태가 아닌 구독 해지 시도 시 SubscriptionNotActiveException을 던진다")
    void cancel_subscriptionNotActive_throwsNotActiveException() throws Exception {
        Subscription s = subscriptionWith("sub-1", "acc-1", SubscriptionStatus.EXPIRED);
        given(subscriptionRepository.findById("sub-1")).willReturn(Optional.of(s));

        assertThatThrownBy(() -> useCase.cancel("sub-1", "acc-1"))
                .isInstanceOf(SubscriptionNotActiveException.class);

        verify(subscriptionRepository, never()).save(any());
        verify(historyRecorder, never()).recordTransition(any(), any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishCancelled(any());
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
        setField(s, "createdAt", LocalDateTime.of(2026, 4, 1, 0, 0, 0));
        return s;
    }
}
