package com.example.finance.account.domain.account;

import com.example.finance.account.domain.account.status.AccountStatus;
import com.example.finance.account.domain.account.status.AccountStatusMachine;
import com.example.finance.account.domain.error.DomainErrors.AccountFrozenException;
import com.example.finance.account.domain.error.DomainErrors.AccountNotActiveException;
import com.example.finance.account.domain.money.Currency;
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

import java.time.Instant;
import java.util.Objects;

/**
 * Account aggregate root (architecture.md § Account State Machine).
 *
 * <p>Multi-tenant: every account carries non-nullable {@code tenantId}; the
 * repository port always passes tenant_id in {@code WHERE}. State transitions
 * flow through {@link AccountStatusMachine} only — there is NO status setter,
 * so no caller can mutate status off-matrix (F: state machine).
 *
 * <p>JPA annotations are the single allowed domain↔framework exception
 * (architecture.md § Boundary rules); the transition logic itself is pure.
 * {@code @Version} gives optimistic locking (transactional T7).
 */
@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    /**
     * Opaque external owner reference. Regulated identifier — persisted
     * encrypted by the JPA adapter (F7) and never logged/evented in plaintext.
     */
    @Column(name = "owner_ref", length = 512, nullable = false)
    private String ownerRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private AccountStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_level", length = 10, nullable = false)
    private KycLevel kycLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", length = 3, nullable = false)
    private Currency currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static Account open(String id,
                               String tenantId,
                               String ownerRef,
                               Currency currency,
                               KycLevel kycLevel,
                               Instant now) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(ownerRef, "ownerRef");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(kycLevel, "kycLevel");
        Objects.requireNonNull(now, "now");

        Account a = new Account();
        a.id = id;
        a.tenantId = tenantId;
        a.ownerRef = ownerRef;
        a.currency = currency;
        a.kycLevel = kycLevel;
        a.status = AccountStatus.PENDING_KYC;
        a.createdAt = now;
        a.updatedAt = now;
        // Leave version null so Spring Data JPA save() detects a new entity.
        return a;
    }

    public AccountId accountId() {
        return AccountId.of(id);
    }

    /**
     * Drive a status transition through the matrix. Returns the previous
     * status for the append-only history/audit row. Never mutates off-matrix.
     */
    public AccountStatus transitionTo(AccountStatus target, Instant now) {
        AccountStatus previous = this.status;
        AccountStatusMachine.ensureTransitionAllowed(previous, target);
        this.status = target;
        this.updatedAt = now;
        return previous;
    }

    /**
     * Raise the KYC level (operator-driven). Does NOT transition status — the
     * application service decides whether the new level activates the account.
     * Returns the previous level for the audit row.
     */
    public KycLevel raiseKycLevel(KycLevel toLevel, Instant now) {
        Objects.requireNonNull(toLevel, "toLevel");
        KycLevel previous = this.kycLevel;
        this.kycLevel = toLevel;
        this.updatedAt = now;
        return previous;
    }

    public boolean isActive() {
        return this.status == AccountStatus.ACTIVE;
    }

    /**
     * Fund-movement precondition (architecture.md § Account State Machine).
     * Only ACTIVE accounts permit fund movement. FROZEN is a distinct code so
     * callers/operators can tell an investigation freeze from a closed/pending
     * account.
     */
    public void ensureFundMovementAllowed() {
        switch (this.status) {
            case ACTIVE -> { /* permitted */ }
            case FROZEN -> throw new AccountFrozenException(
                    "Account " + id + " is FROZEN — fund movement blocked");
            default -> throw new AccountNotActiveException(
                    "Account " + id + " is " + status + " — fund movement requires ACTIVE");
        }
    }
}
