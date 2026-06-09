package com.example.fanplatform.membership.domain.membership;

import com.example.fanplatform.membership.domain.membership.status.MembershipStateMachine;
import com.example.fanplatform.membership.domain.membership.status.MembershipStatus;
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

/**
 * Membership aggregate — one windowed subscription held by a fan account.
 *
 * <p>Status transitions flow ONLY through {@link MembershipStateMachine}; there
 * is no public {@code setStatus}. A subscribe creates the row directly in
 * {@code ACTIVE}; {@link #cancel(Instant)} moves ACTIVE → CANCELED (validated by
 * the state machine). The {@code domain} layer depends only on
 * {@code jakarta.persistence} (the pragmatic JPA exception) — no Spring imports.
 */
@Entity
@Table(name = "memberships")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Membership {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "account_id", length = 36, nullable = false)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", length = 20, nullable = false)
    private MembershipTier tier;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private MembershipStatus status;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to", nullable = false)
    private Instant validTo;

    @Column(name = "plan_months", nullable = false)
    private int planMonths;

    @Column(name = "payment_ref", length = 80, nullable = false)
    private String paymentRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * Factory for a brand-new ACTIVE membership (subscribe → ACTIVE). The window
     * timestamps MUST already be truncated to micros by the caller (§15) so the
     * in-memory response equals the DB re-read.
     */
    public static Membership activate(String id, String tenantId, String accountId,
                                      MembershipTier tier, Instant validFrom, Instant validTo,
                                      int planMonths, String paymentRef, Instant createdAt) {
        Membership m = new Membership();
        m.id = id;
        m.tenantId = tenantId;
        m.accountId = accountId;
        m.tier = tier;
        m.status = MembershipStatus.ACTIVE;
        m.validFrom = validFrom;
        m.validTo = validTo;
        m.planMonths = planMonths;
        m.paymentRef = paymentRef;
        m.createdAt = createdAt;
        m.canceledAt = null;
        return m;
    }

    /**
     * Moves ACTIVE → CANCELED. Validated by {@link MembershipStateMachine}; an
     * idempotent re-cancel of an already-CANCELED membership MUST be
     * short-circuited by the use case before calling this method.
     */
    public void cancel(Instant canceledAt) {
        MembershipStateMachine.ensureTransitionAllowed(this.status, MembershipStatus.CANCELED);
        this.status = MembershipStatus.CANCELED;
        this.canceledAt = canceledAt;
    }

    public boolean isCanceled() {
        return this.status == MembershipStatus.CANCELED;
    }
}
