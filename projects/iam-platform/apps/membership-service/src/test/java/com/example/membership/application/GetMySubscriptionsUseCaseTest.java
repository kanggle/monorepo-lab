package com.example.membership.application;

import com.example.membership.application.result.MySubscriptionsResult;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("GetMySubscriptionsUseCase 단위 테스트")
class GetMySubscriptionsUseCaseTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private GetMySubscriptionsUseCase useCase;

    @Test
    @DisplayName("구독이 없으면 activePlanLevel 은 FREE 이고 subscriptions 는 빈 리스트")
    void noSubscriptions_activePlanLevelIsFree() {
        String accountId = UUID.randomUUID().toString();
        given(subscriptionRepository.findByAccountId(accountId)).willReturn(List.of());

        MySubscriptionsResult result = useCase.getMine(accountId);

        assertThat(result.activePlanLevel()).isEqualTo(PlanLevel.FREE);
        assertThat(result.subscriptions()).isEmpty();
        assertThat(result.accountId()).isEqualTo(accountId);
    }

    @Test
    @DisplayName("ACTIVE FAN_CLUB 구독 1개 → activePlanLevel = FAN_CLUB")
    void singleActiveFanClub_activePlanLevelIsFanClub() {
        String accountId = UUID.randomUUID().toString();
        Subscription active = subscription(accountId, PlanLevel.FAN_CLUB, SubscriptionStatus.ACTIVE);
        given(subscriptionRepository.findByAccountId(accountId)).willReturn(List.of(active));

        MySubscriptionsResult result = useCase.getMine(accountId);

        assertThat(result.activePlanLevel()).isEqualTo(PlanLevel.FAN_CLUB);
        assertThat(result.subscriptions()).hasSize(1);
    }

    @Test
    @DisplayName("EXPIRED FAN_CLUB 구독만 있으면 activePlanLevel = FREE (만료 구독은 제외)")
    void onlyExpiredSubscription_activePlanLevelIsFree() {
        String accountId = UUID.randomUUID().toString();
        Subscription expired = subscription(accountId, PlanLevel.FAN_CLUB, SubscriptionStatus.EXPIRED);
        given(subscriptionRepository.findByAccountId(accountId)).willReturn(List.of(expired));

        MySubscriptionsResult result = useCase.getMine(accountId);

        assertThat(result.activePlanLevel()).isEqualTo(PlanLevel.FREE);
        assertThat(result.subscriptions()).hasSize(1);
    }

    private Subscription subscription(String accountId, PlanLevel planLevel, SubscriptionStatus status) {
        LocalDateTime now = LocalDateTime.now();
        return new Subscription(
                UUID.randomUUID().toString(),
                accountId,
                planLevel,
                status,
                now.minusDays(1),
                status == SubscriptionStatus.ACTIVE ? now.plusDays(29) : now.minusHours(1),
                null,
                now.minusDays(1),
                0
        );
    }
}
