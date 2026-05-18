package com.example.finance.account.domain.account.status;

import com.example.finance.account.domain.account.ActorType;
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

import java.time.Instant;

/**
 * Append-only account state-transition history (fintech F6, audit-heavy
 * trait). Inserted in the same transaction as the {@code accounts.status}
 * update — no UPDATE/DELETE path exists (the table has no mutators and the
 * adapter only ever {@code save}s new rows).
 *
 * <p>JPA annotations on this entity are the single allowed domain↔framework
 * exception (architecture.md § Boundary rules); there is no business logic
 * here, only the immutable factory.
 */
@Entity
@Table(name = "account_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", length = 36, nullable = false)
    private String accountId;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20, nullable = false)
    private AccountStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 20, nullable = false)
    private AccountStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", length = 20, nullable = false)
    private ActorType actorType;

    @Column(name = "actor_account_id", length = 64)
    private String actorAccountId;

    @Column(name = "reason", length = 256)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public static AccountStatusHistory record(String accountId,
                                              String tenantId,
                                              AccountStatus from,
                                              AccountStatus to,
                                              ActorType actorType,
                                              String actorAccountId,
                                              String reason,
                                              Instant occurredAt) {
        AccountStatusHistory h = new AccountStatusHistory();
        h.accountId = accountId;
        h.tenantId = tenantId;
        h.fromStatus = from;
        h.toStatus = to;
        h.actorType = actorType;
        h.actorAccountId = actorAccountId;
        h.reason = reason;
        h.occurredAt = occurredAt;
        return h;
    }
}
