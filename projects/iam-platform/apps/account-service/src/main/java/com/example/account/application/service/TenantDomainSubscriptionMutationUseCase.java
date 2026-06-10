package com.example.account.application.service;

import com.example.account.application.event.TenantDomainSubscriptionEventPublisher;
import com.example.account.application.exception.SubscriptionAlreadyExistsException;
import com.example.account.application.exception.SubscriptionNotFoundException;
import com.example.account.application.result.SubscriptionMutationResult;
import com.example.account.domain.repository.TenantDomainSubscriptionRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.application.exception.TenantNotFoundException;
import com.example.account.domain.tenant.SubscriptionStatus;
import com.example.account.domain.tenant.TenantDomainSubscription;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * TASK-BE-342 (ADR-MONO-023 § 3.3 step 2 — D1/D3/D4): the entitlement-plane
 * mutation surface for tenant↔domain subscriptions. account-service owns its own
 * writes (D3-A); admin-service (the IAM plane) gates the operator action with
 * {@code subscription.manage} + audit, then delegates here (account-service never
 * reads IAM — D2 one-way dependency).
 *
 * <p>Every mutation is {@code @Transactional} and, in the same transaction:
 * applies the {@link SubscriptionStatus} state-machine guard → saves → emits the
 * {@code tenant.subscription.changed} outbox event (D4).
 */
@Service
@RequiredArgsConstructor
public class TenantDomainSubscriptionMutationUseCase {

    private final TenantDomainSubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    private final TenantDomainSubscriptionEventPublisher eventPublisher;

    /**
     * {@code subscribe} — create a new subscription. {@code status} defaults to
     * {@link SubscriptionStatus#ACTIVE}; only {@code creatable()} states
     * ({@code PENDING}/{@code ACTIVE}) are accepted (else 400 via
     * {@code IllegalArgumentException} from {@link TenantDomainSubscription#create}).
     *
     * @throws TenantNotFoundException          the tenant is not registered (404)
     * @throws SubscriptionAlreadyExistsException a subscription for this
     *                                          {@code (tenantId, domainKey)} already exists (409)
     */
    @Transactional
    public SubscriptionMutationResult subscribe(String tenantId, String domainKey,
                                                SubscriptionStatus status,
                                                String actorType, String actorId, String reason) {
        SubscriptionStatus target = (status != null) ? status : SubscriptionStatus.ACTIVE;
        if (!tenantRepository.existsById(new TenantId(tenantId))) {
            throw new TenantNotFoundException(tenantId);
        }
        if (subscriptionRepository.findByTenantIdAndDomainKey(tenantId, domainKey).isPresent()) {
            throw new SubscriptionAlreadyExistsException(tenantId, domainKey);
        }
        Instant now = Instant.now();
        TenantDomainSubscription created =
                TenantDomainSubscription.create(new TenantId(tenantId), domainKey, target, now);
        subscriptionRepository.save(created);
        eventPublisher.publishSubscriptionChanged(
                tenantId, domainKey, null, target.name(), reason, actorType, actorId, now);
        return new SubscriptionMutationResult(tenantId, domainKey, null, target, now);
    }

    /**
     * Transition an existing subscription ({@code suspend}/{@code resume}/{@code cancel}).
     * The {@link SubscriptionStatus} guard rejects illegal transitions.
     *
     * @throws SubscriptionNotFoundException            no subscription for this pair (404)
     * @throws com.example.account.domain.tenant.IllegalSubscriptionTransitionException
     *                                                 illegal transition (409)
     */
    @Transactional
    public SubscriptionMutationResult changeStatus(String tenantId, String domainKey,
                                                   SubscriptionStatus target,
                                                   String actorType, String actorId, String reason) {
        TenantDomainSubscription sub = subscriptionRepository
                .findByTenantIdAndDomainKey(tenantId, domainKey)
                .orElseThrow(() -> new SubscriptionNotFoundException(tenantId, domainKey));
        Instant now = Instant.now();
        SubscriptionStatus previous = sub.changeStatus(target, now); // guard → 409 if illegal
        subscriptionRepository.save(sub);
        eventPublisher.publishSubscriptionChanged(
                tenantId, domainKey, previous.name(), target.name(), reason, actorType, actorId, now);
        return new SubscriptionMutationResult(tenantId, domainKey, previous, target, now);
    }
}
