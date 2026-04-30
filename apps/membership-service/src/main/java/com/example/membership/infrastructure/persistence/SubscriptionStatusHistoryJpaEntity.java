package com.example.membership.infrastructure.persistence;

import com.example.membership.domain.subscription.SubscriptionStatusHistoryEntry;
import com.example.membership.domain.subscription.status.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionStatusHistoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", nullable = false, length = 36)
    private String subscriptionId;

    @Column(name = "account_id", nullable = false, length = 36)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 20)
    private SubscriptionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private SubscriptionStatus toStatus;

    @Column(nullable = false, length = 50)
    private String reason;

    @Column(name = "actor_type", nullable = false, length = 20)
    private String actorType;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    public static SubscriptionStatusHistoryJpaEntity from(SubscriptionStatusHistoryEntry e) {
        SubscriptionStatusHistoryJpaEntity entity = new SubscriptionStatusHistoryJpaEntity();
        entity.subscriptionId = e.subscriptionId();
        entity.accountId = e.accountId();
        entity.fromStatus = e.fromStatus();
        entity.toStatus = e.toStatus();
        entity.reason = e.reason();
        entity.actorType = e.actorType();
        entity.occurredAt = e.occurredAt();
        return entity;
    }
}
