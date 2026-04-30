package com.example.membership.application;

import com.example.membership.application.result.AccessCheckResult;
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
@DisplayName("CheckContentAccessUseCase 단위 테스트")
class CheckContentAccessUseCaseTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private CheckContentAccessUseCase useCase;

    @Test
    @DisplayName("ACTIVE FAN_CLUB 구독 + FAN_CLUB 요구 → allowed=true, activePlanLevel=FAN_CLUB")
    void activeFanClub_fanClubRequired_allowed() {
        String accountId = UUID.randomUUID().toString();
        given(subscriptionRepository.findActiveByAccountId(accountId))
                .willReturn(List.of(activeSubscription(accountId, PlanLevel.FAN_CLUB)));

        AccessCheckResult result = useCase.check(accountId, PlanLevel.FAN_CLUB);

        assertThat(result.allowed()).isTrue();
        assertThat(result.activePlanLevel()).isEqualTo(PlanLevel.FAN_CLUB);
        assertThat(result.requiredPlanLevel()).isEqualTo(PlanLevel.FAN_CLUB);
    }

    @Test
    @DisplayName("ACTIVE 구독 없음 + FAN_CLUB 요구 → allowed=false (FREE < FAN_CLUB)")
    void noActiveSubscription_fanClubRequired_denied() {
        String accountId = UUID.randomUUID().toString();
        given(subscriptionRepository.findActiveByAccountId(accountId)).willReturn(List.of());

        AccessCheckResult result = useCase.check(accountId, PlanLevel.FAN_CLUB);

        assertThat(result.allowed()).isFalse();
        assertThat(result.activePlanLevel()).isEqualTo(PlanLevel.FREE);
    }

    @Test
    @DisplayName("ACTIVE FAN_CLUB 구독 + FREE 요구 → allowed=true (상위 플랜은 하위 요구 충족)")
    void activeFanClub_freeRequired_allowed() {
        String accountId = UUID.randomUUID().toString();
        given(subscriptionRepository.findActiveByAccountId(accountId))
                .willReturn(List.of(activeSubscription(accountId, PlanLevel.FAN_CLUB)));

        AccessCheckResult result = useCase.check(accountId, PlanLevel.FREE);

        assertThat(result.allowed()).isTrue();
        assertThat(result.activePlanLevel()).isEqualTo(PlanLevel.FAN_CLUB);
    }

    private Subscription activeSubscription(String accountId, PlanLevel planLevel) {
        LocalDateTime now = LocalDateTime.now();
        return new Subscription(
                UUID.randomUUID().toString(),
                accountId,
                planLevel,
                SubscriptionStatus.ACTIVE,
                now.minusDays(1),
                now.plusDays(29),
                null,
                now.minusDays(1),
                0
        );
    }
}
