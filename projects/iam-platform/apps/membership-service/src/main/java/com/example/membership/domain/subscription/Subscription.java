package com.example.membership.domain.subscription;

import com.example.membership.domain.event.MembershipDomainEvent;
import com.example.membership.domain.plan.PlanLevel;
import com.example.membership.domain.subscription.status.SubscriptionStatus;
import com.example.membership.domain.subscription.status.SubscriptionStatusMachine;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Subscription aggregate root. POJO domain object — persistence mapping lives in
 * {@code infrastructure/persistence/SubscriptionJpaEntity}. State transitions must
 * go through {@link SubscriptionStatusMachine} — direct UPDATE of status is forbidden.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription {

    private String id;
    private String accountId;
    private PlanLevel planLevel;
    private SubscriptionStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
    private int version;

    /**
     * Reconstitution constructor used by the persistence adapter to rebuild a
     * Subscription aggregate from its JPA entity counterpart.
     */
    public Subscription(String id,
                        String accountId,
                        PlanLevel planLevel,
                        SubscriptionStatus status,
                        LocalDateTime startedAt,
                        LocalDateTime expiresAt,
                        LocalDateTime cancelledAt,
                        LocalDateTime createdAt,
                        int version) {
        this.id = id;
        this.accountId = accountId;
        this.planLevel = planLevel;
        this.status = status;
        this.startedAt = startedAt;
        this.expiresAt = expiresAt;
        this.cancelledAt = cancelledAt;
        this.createdAt = createdAt;
        this.version = version;
    }

    /**
     * Factory: creates a new ACTIVE subscription (transitions NONE → ACTIVE).
     * For FREE plan, expiresAt is null (permanent, scheduler-exempt).
     */
    public static Subscription activate(String accountId,
                                        PlanLevel planLevel,
                                        int durationDays,
                                        LocalDateTime now,
                                        SubscriptionStatusMachine machine) {
        machine.transition(SubscriptionStatus.NONE, SubscriptionStatus.ACTIVE);

        Subscription s = new Subscription();
        s.id = UUID.randomUUID().toString();
        s.accountId = accountId;
        s.planLevel = planLevel;
        s.status = SubscriptionStatus.ACTIVE;
        s.startedAt = now;
        s.expiresAt = (planLevel == PlanLevel.FREE || durationDays <= 0)
                ? null
                : now.plusDays(durationDays);
        s.createdAt = now;
        return s;
    }

    public void expire(LocalDateTime now, SubscriptionStatusMachine machine) {
        machine.transition(this.status, SubscriptionStatus.EXPIRED);
        this.status = SubscriptionStatus.EXPIRED;
        if (this.expiresAt == null) {
            this.expiresAt = now;
        }
    }

    public void cancel(LocalDateTime now, SubscriptionStatusMachine machine) {
        machine.transition(this.status, SubscriptionStatus.CANCELLED);
        this.status = SubscriptionStatus.CANCELLED;
        this.cancelledAt = now;
    }

    public boolean isActive() {
        return this.status == SubscriptionStatus.ACTIVE;
    }

    /**
     * Ownership check: returns true if this subscription belongs to the given account.
     */
    public boolean belongsTo(String candidateAccountId) {
        return this.accountId.equals(candidateAccountId);
    }

    /**
     * Test/reconstitution-only hook for backfilling an expired-at in the past. Kept
     * package-private so only tests within the same package can reach it.
     */
    void unsafeSetExpiresAtForTest(LocalDateTime value) {
        this.expiresAt = value;
    }

    public MembershipDomainEvent buildActivatedEvent() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subscriptionId", id);
        payload.put("accountId", accountId);
        payload.put("planLevel", planLevel.name());
        payload.put("startedAt", toIsoString(startedAt));
        payload.put("expiresAt", toIsoString(expiresAt));
        return new MembershipDomainEvent("membership.subscription.activated", payload);
    }

    public MembershipDomainEvent buildExpiredEvent() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subscriptionId", id);
        payload.put("accountId", accountId);
        payload.put("planLevel", planLevel.name());
        payload.put("expiredAt", toIsoString(expiresAt));
        return new MembershipDomainEvent("membership.subscription.expired", payload);
    }

    public MembershipDomainEvent buildCancelledEvent() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subscriptionId", id);
        payload.put("accountId", accountId);
        payload.put("planLevel", planLevel.name());
        payload.put("cancelledAt", toIsoString(cancelledAt));
        return new MembershipDomainEvent("membership.subscription.cancelled", payload);
    }

    private static String toIsoString(LocalDateTime ldt) {
        return ldt == null ? null : ldt.toInstant(ZoneOffset.UTC).toString();
    }
}
