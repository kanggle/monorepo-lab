package com.example.membership.application;

import com.example.membership.application.command.ActivateSubscriptionCommand;
import com.example.membership.application.event.MembershipEventPublisher;
import com.example.membership.application.exception.AccountNotEligibleException;
import com.example.membership.application.exception.PlanNotFoundException;
import com.example.membership.application.exception.SubscriptionAlreadyActiveException;
import com.example.membership.application.exception.SubscriptionNotFoundException;
import com.example.membership.application.idempotency.IdempotencyStore;
import com.example.membership.application.result.ActivateSubscriptionResult;
import com.example.membership.application.result.SubscriptionResult;
import com.example.membership.domain.account.AccountStatus;
import com.example.membership.domain.account.AccountStatusChecker;
import com.example.membership.domain.payment.PaymentGateway;
import com.example.membership.domain.plan.MembershipPlan;
import com.example.membership.domain.plan.MembershipPlanRepository;
import com.example.membership.domain.subscription.Subscription;
import com.example.membership.domain.subscription.SubscriptionRepository;
import com.example.membership.domain.subscription.status.SubscriptionStatus;
import com.example.membership.domain.subscription.status.SubscriptionStatusMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivateSubscriptionUseCase {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionStatusHistoryRecorder historyRecorder;
    private final MembershipPlanRepository planRepository;
    private final AccountStatusChecker accountStatusChecker;
    private final PaymentGateway paymentGateway;
    private final MembershipEventPublisher eventPublisher;
    private final IdempotencyStore idempotencyStore;
    private final SubscriptionStatusMachine statusMachine = new SubscriptionStatusMachine();

    @Transactional
    public ActivateSubscriptionResult activate(ActivateSubscriptionCommand command) {
        // 1) Idempotency check — return cached response if present.
        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            Optional<String> existing = idempotencyStore.get(command.idempotencyKey());
            if (existing.isPresent()) {
                Subscription s = subscriptionRepository.findById(existing.get())
                        .orElseThrow(() -> new SubscriptionNotFoundException(existing.get()));
                return ActivateSubscriptionResult.replayed(SubscriptionResult.from(s));
            }
        }

        // 2) Account eligibility — fail-closed.
        AccountStatus accountStatus = accountStatusChecker.check(command.accountId());
        if (accountStatus != AccountStatus.ACTIVE) {
            throw new AccountNotEligibleException(accountStatus);
        }

        // 3) Duplicate active subscription for same plan.
        subscriptionRepository.findActiveByAccountIdAndPlanLevel(command.accountId(), command.planLevel())
                .ifPresent(existing -> {
                    throw new SubscriptionAlreadyActiveException(
                            "Account already has an ACTIVE subscription for plan " + command.planLevel());
                });

        // 4) Fetch plan.
        MembershipPlan plan = planRepository.findByPlanLevel(command.planLevel())
                .orElseThrow(() -> new PlanNotFoundException(command.planLevel().name()));

        // 5) Payment (stub always success for FAN_CLUB, free for FREE).
        if (plan.getPriceKrw() > 0) {
            PaymentGateway.PaymentResult pr = paymentGateway.charge(
                    command.accountId(), plan.getPlanLevel(), plan.getPriceKrw());
            if (!pr.success()) {
                throw new IllegalStateException("Payment failed: " + pr.failureReason());
            }
        }

        // 6) Create subscription.
        LocalDateTime now = LocalDateTime.now();
        Subscription subscription = Subscription.activate(
                command.accountId(),
                plan.getPlanLevel(),
                plan.getDurationDays(),
                now,
                statusMachine);
        Subscription saved = subscriptionRepository.save(subscription);

        // 7) Append audit history.
        historyRecorder.recordTransition(saved,
                SubscriptionStatus.NONE, SubscriptionStatus.ACTIVE,
                "USER_SUBSCRIBE", "USER", now);

        // 8) Publish outbox event.
        eventPublisher.publishActivated(saved);

        // 9) Store idempotency mapping.
        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            idempotencyStore.putIfAbsent(command.idempotencyKey(), saved.getId());
        }

        return ActivateSubscriptionResult.created(SubscriptionResult.from(saved));
    }
}
