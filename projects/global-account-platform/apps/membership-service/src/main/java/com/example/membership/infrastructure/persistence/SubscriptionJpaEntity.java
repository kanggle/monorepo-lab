package com.example.membership.infrastructure.persistence;

import com.example.membership.domain.plan.PlanLevel;
import com.example.membership.domain.subscription.Subscription;
import com.example.membership.domain.subscription.status.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Persistence representation of the {@link Subscription} aggregate. JPA annotations
 * live here — the domain POJO remains framework-free.
 */
@Entity
@Table(name = "subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionJpaEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "account_id", nullable = false, length = 36)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_level", nullable = false, length = 20)
    private PlanLevel planLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(nullable = false)
    private int version;

    public static SubscriptionJpaEntity fromDomain(Subscription s) {
        SubscriptionJpaEntity e = new SubscriptionJpaEntity();
        e.id = s.getId();
        e.accountId = s.getAccountId();
        e.planLevel = s.getPlanLevel();
        e.status = s.getStatus();
        e.startedAt = s.getStartedAt();
        e.expiresAt = s.getExpiresAt();
        e.cancelledAt = s.getCancelledAt();
        e.createdAt = s.getCreatedAt();
        e.version = s.getVersion();
        return e;
    }

    /**
     * Copy in-place from the domain aggregate while preserving JPA identity so the
     * optimistic lock {@code @Version} column behaves correctly on merge/save.
     */
    public void updateFromDomain(Subscription s) {
        this.accountId = s.getAccountId();
        this.planLevel = s.getPlanLevel();
        this.status = s.getStatus();
        this.startedAt = s.getStartedAt();
        this.expiresAt = s.getExpiresAt();
        this.cancelledAt = s.getCancelledAt();
        this.createdAt = s.getCreatedAt();
        // version is managed by Hibernate; do not overwrite
    }

    public Subscription toDomain() {
        return new Subscription(
                id, accountId, planLevel, status,
                startedAt, expiresAt, cancelledAt, createdAt, version);
    }
}
