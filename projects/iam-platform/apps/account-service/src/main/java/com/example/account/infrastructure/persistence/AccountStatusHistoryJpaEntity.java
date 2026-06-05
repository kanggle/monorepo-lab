package com.example.account.infrastructure.persistence;

import com.example.account.domain.history.AccountStatusHistoryEntry;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.status.StatusChangeReason;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "account_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountStatusHistoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    @Column(name = "account_id", nullable = false, length = 36)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 20)
    private AccountStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private AccountStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 50)
    private StatusChangeReason reasonCode;

    @Column(name = "actor_type", nullable = false, length = 20)
    private String actorType;

    @Column(name = "actor_id", length = 36)
    private String actorId;

    @Column(columnDefinition = "JSON")
    private String details;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public static AccountStatusHistoryJpaEntity fromDomain(AccountStatusHistoryEntry entry) {
        AccountStatusHistoryJpaEntity entity = new AccountStatusHistoryJpaEntity();
        entity.id = entry.getId();
        // TASK-BE-231: use tenantId from entry when present; fall back to "fan-platform"
        // for legacy fan-platform records where no explicit tenantId is set.
        entity.tenantId = entry.getTenantId() != null ? entry.getTenantId() : "fan-platform";
        entity.accountId = entry.getAccountId();
        entity.fromStatus = entry.getFromStatus();
        entity.toStatus = entry.getToStatus();
        entity.reasonCode = entry.getReasonCode();
        entity.actorType = entry.getActorType();
        entity.actorId = entry.getActorId();
        entity.details = entry.getDetails();
        entity.occurredAt = entry.getOccurredAt();
        return entity;
    }

    public AccountStatusHistoryEntry toDomain() {
        return AccountStatusHistoryEntry.reconstitute(id, accountId, fromStatus, toStatus,
                reasonCode, actorType, actorId, details, occurredAt);
    }
}
