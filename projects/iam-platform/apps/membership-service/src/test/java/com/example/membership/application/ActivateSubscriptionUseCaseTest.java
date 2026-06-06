package com.example.membership.application;

import com.example.membership.application.command.ActivateSubscriptionCommand;
import com.example.membership.application.event.MembershipEventPublisher;
import com.example.membership.application.exception.AccountNotEligibleException;
import com.example.membership.application.exception.SubscriptionAlreadyActiveException;
import com.example.membership.application.idempotency.IdempotencyStore;
import com.example.membership.application.result.ActivateSubscriptionResult;
import com.example.membership.domain.account.AccountStatus;
import com.example.membership.domain.account.AccountStatusChecker;
import com.example.membership.domain.payment.PaymentGateway;
import com.example.membership.domain.plan.MembershipPlan;
import com.example.membership.domain.plan.MembershipPlanRepository;
import com.example.membership.domain.plan.PlanLevel;
import com.example.membership.domain.subscription.Subscription;
import com.example.membership.domain.subscription.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("ActivateSubscriptionUseCase")
class ActivateSubscriptionUseCaseTest {

    @Mock SubscriptionRepository subscriptionRepository;
    @Mock SubscriptionStatusHistoryRecorder historyRecorder;
    @Mock MembershipPlanRepository planRepository;
    @Mock AccountStatusChecker accountStatusChecker;
    @Mock PaymentGateway paymentGateway;
    @Mock MembershipEventPublisher eventPublisher;
    @Mock IdempotencyStore idempotencyStore;

    @InjectMocks ActivateSubscriptionUseCase useCase;

    private MembershipPlan plan;

    private static <T> T newInstance(Class<T> cls) throws Exception {
        var ctor = cls.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    @BeforeEach
    void setup() throws Exception {
        plan = newInstance(MembershipPlan.class);
        // reflective setup since MembershipPlan has no public ctor
        setField(plan, "id", "plan-fanclub");
        setField(plan, "planLevel", PlanLevel.FAN_CLUB);
        setField(plan, "name", "팬 클럽");
        setField(plan, "priceKrw", 9900);
        setField(plan, "durationDays", 30);
        setField(plan, "active", true);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    @DisplayName("happy path: ACTIVE account + fresh key → created + event published")
    void happyPath() {
        ActivateSubscriptionCommand cmd = new ActivateSubscriptionCommand(
                "acc-1", PlanLevel.FAN_CLUB, "key-1");
        given(idempotencyStore.get("key-1")).willReturn(Optional.empty());
        given(accountStatusChecker.check("acc-1")).willReturn(AccountStatus.ACTIVE);
        given(subscriptionRepository.findActiveByAccountIdAndPlanLevel("acc-1", PlanLevel.FAN_CLUB))
                .willReturn(Optional.empty());
        given(planRepository.findByPlanLevel(PlanLevel.FAN_CLUB)).willReturn(Optional.of(plan));
        given(paymentGateway.charge(any(), any(), anyInt()))
                .willReturn(PaymentGateway.PaymentResult.success("tx"));
        given(subscriptionRepository.save(any(Subscription.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(idempotencyStore.putIfAbsent(any(), any())).willReturn(true);

        ActivateSubscriptionResult result = useCase.activate(cmd);

        assertThat(result.created()).isTrue();
        assertThat(result.subscription().planLevel()).isEqualTo(PlanLevel.FAN_CLUB);
        verify(eventPublisher).publishActivated(any(Subscription.class));
        verify(historyRecorder).recordTransition(any(Subscription.class), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("LOCKED account → AccountNotEligibleException, no save, no event")
    void lockedAccount() {
        ActivateSubscriptionCommand cmd = new ActivateSubscriptionCommand(
                "acc-1", PlanLevel.FAN_CLUB, "key-1");
        given(idempotencyStore.get("key-1")).willReturn(Optional.empty());
        given(accountStatusChecker.check("acc-1")).willReturn(AccountStatus.LOCKED);

        assertThatThrownBy(() -> useCase.activate(cmd))
                .isInstanceOf(AccountNotEligibleException.class);

        verify(subscriptionRepository, never()).save(any());
        verify(eventPublisher, never()).publishActivated(any());
    }

    @Test
    @DisplayName("duplicate active → SubscriptionAlreadyActiveException")
    void duplicateActive() throws Exception {
        ActivateSubscriptionCommand cmd = new ActivateSubscriptionCommand(
                "acc-1", PlanLevel.FAN_CLUB, "key-1");
        given(idempotencyStore.get("key-1")).willReturn(Optional.empty());
        given(accountStatusChecker.check("acc-1")).willReturn(AccountStatus.ACTIVE);
        given(subscriptionRepository.findActiveByAccountIdAndPlanLevel("acc-1", PlanLevel.FAN_CLUB))
                .willReturn(Optional.of(newInstance(Subscription.class)));

        assertThatThrownBy(() -> useCase.activate(cmd))
                .isInstanceOf(SubscriptionAlreadyActiveException.class);

        verify(subscriptionRepository, never()).save(any());
        verify(eventPublisher, never()).publishActivated(any());
    }

    @Test
    @DisplayName("idempotent replay → returns replayed result, no save/event")
    void idempotentReplay() throws Exception {
        ActivateSubscriptionCommand cmd = new ActivateSubscriptionCommand(
                "acc-1", PlanLevel.FAN_CLUB, "key-1");

        Subscription existing = newInstance(Subscription.class);
        setField(existing, "id", "sub-existing");
        setField(existing, "accountId", "acc-1");
        setField(existing, "planLevel", PlanLevel.FAN_CLUB);
        setField(existing, "status", com.example.membership.domain.subscription.status.SubscriptionStatus.ACTIVE);
        setField(existing, "startedAt", java.time.LocalDateTime.now());
        setField(existing, "createdAt", java.time.LocalDateTime.now());

        given(idempotencyStore.get("key-1")).willReturn(Optional.of("sub-existing"));
        given(subscriptionRepository.findById("sub-existing")).willReturn(Optional.of(existing));

        ActivateSubscriptionResult result = useCase.activate(cmd);
        assertThat(result.created()).isFalse();
        assertThat(result.subscription().subscriptionId()).isEqualTo("sub-existing");
        verify(subscriptionRepository, never()).save(any());
        verify(eventPublisher, never()).publishActivated(any());
    }
}
