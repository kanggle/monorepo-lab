package com.example.erp.approval.domain.delegation;

import com.example.erp.approval.domain.error.ApprovalErrors.DelegationInvalidException;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

/**
 * Delegation grant aggregate root (architecture.md § v2.1 amendment — 대결/위임).
 * A standing, windowed grant: while {@code ACTIVE} and {@code now ∈ [validFrom,
 * validTo ?? +∞]}, the delegate ({@code delegateId}, D) may act for the delegator
 * ({@code delegatorId}, A) at ANY stage where A is the approver (1-hop; no
 * transitive chaining — that is v2.2-deferred).
 *
 * <p>Creation invariants (E3 / I4): self-delegation ({@code delegatorId ==
 * delegateId}) → {@link DelegationInvalidException}; an inverted validity window
 * ({@code validTo < validFrom}) → {@link DelegationInvalidException}.
 *
 * <p>JPA annotations are the single allowed domain↔framework exception
 * (architecture.md § Boundary rules); the invariant logic is otherwise pure.
 * {@code @Version} gives optimistic locking (transactional T5).
 */
@Entity
@Table(name = "delegation_grant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DelegationGrant {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    /** The delegator A — the absent stage approver whose authority is delegated. */
    @Column(name = "delegator_id", length = 64, nullable = false)
    private String delegatorId;

    /** The delegate D — the substitute who may act for A while the grant is active. */
    @Column(name = "delegate_id", length = 64, nullable = false)
    private String delegateId;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    /** {@code null} = open-ended (no end bound). */
    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "reason", length = 512)
    private String reason;

    /**
     * Scope of the grant (TASK-ERP-BE-017). {@code GLOBAL} = blanket A→D (default,
     * pre-BE-017 behavior); {@code REQUEST} = narrowed to {@link #scopeRequestId}.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "scope", length = 16, nullable = false)
    private DelegationScope scope;

    /**
     * The single approval request this grant authorizes when {@code scope ==
     * REQUEST}; {@code null} when {@code scope == GLOBAL} (coherence enforced by
     * the factory + the DB CHECK {@code ck_delegation_grant_scope_req}).
     */
    @Column(name = "scope_request_id", length = 64)
    private String scopeRequestId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 16, nullable = false)
    private DelegationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 64, nullable = false)
    private String createdBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by", length = 64)
    private String revokedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * Create an ACTIVE delegation grant A→D. Refuses self-delegation and an
     * inverted validity window ({@code validTo < validFrom}). {@code validTo}
     * {@code null} is allowed (open-ended). {@code createdBy} = the grant creator's
     * JWT sub (= the delegator A, or an admin acting on A's behalf).
     *
     * <p>TASK-ERP-BE-017 — scope coherence (→ {@link DelegationInvalidException}):
     * a {@code null} {@code scope} arg defaults to {@code GLOBAL} (back-compat);
     * {@code scope == REQUEST} requires a non-blank {@code scopeRequestId};
     * {@code scope == GLOBAL} forbids a non-blank {@code scopeRequestId}. The blank
     * {@code scopeRequestId} of a GLOBAL grant is normalized to {@code null}.
     */
    public static DelegationGrant create(String id, String tenantId, String delegatorId,
                                         String delegateId, Instant validFrom,
                                         Instant validTo, String reason,
                                         DelegationScope scope, String scopeRequestId,
                                         String createdBy, Instant now) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(delegatorId, "delegatorId");
        Objects.requireNonNull(delegateId, "delegateId");
        Objects.requireNonNull(validFrom, "validFrom");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(now, "now");
        if (delegatorId.equals(delegateId)) {
            throw new DelegationInvalidException(
                    "self-delegation is not allowed (delegatorId == delegateId == '"
                            + delegatorId + "')");
        }
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw new DelegationInvalidException(
                    "validity window is invalid: validTo (" + validTo
                            + ") is before validFrom (" + validFrom + ")");
        }
        DelegationScope resolvedScope = scope == null ? DelegationScope.GLOBAL : scope;
        boolean hasRequestId = scopeRequestId != null && !scopeRequestId.isBlank();
        if (resolvedScope == DelegationScope.REQUEST && !hasRequestId) {
            throw new DelegationInvalidException(
                    "scope=REQUEST requires a non-blank scopeRequestId");
        }
        if (resolvedScope == DelegationScope.GLOBAL && hasRequestId) {
            throw new DelegationInvalidException(
                    "scope=GLOBAL forbids a scopeRequestId (got '" + scopeRequestId + "')");
        }
        DelegationGrant g = new DelegationGrant();
        g.id = id;
        g.tenantId = tenantId;
        g.delegatorId = delegatorId;
        g.delegateId = delegateId;
        g.validFrom = validFrom;
        g.validTo = validTo;
        g.reason = reason;
        g.scope = resolvedScope;
        g.scopeRequestId = resolvedScope == DelegationScope.REQUEST ? scopeRequestId : null;
        g.status = DelegationStatus.ACTIVE;
        g.createdAt = now;
        g.createdBy = createdBy;
        return g;
    }

    /**
     * ACTIVE → REVOKED. Idempotent — revoking an already-REVOKED grant is a no-op
     * (returns {@code false} so the caller may skip the audit/no-op as appropriate;
     * the first revoke returns {@code true}). The original {@code revokedBy /
     * revokedAt} are preserved on a repeat revoke.
     */
    public boolean revoke(String revokedBy, Instant now) {
        Objects.requireNonNull(revokedBy, "revokedBy");
        Objects.requireNonNull(now, "now");
        if (status == DelegationStatus.REVOKED) {
            return false;
        }
        this.status = DelegationStatus.REVOKED;
        this.revokedAt = now;
        this.revokedBy = revokedBy;
        return true;
    }

    /**
     * True iff the grant authorizes a delegated action at {@code now}:
     * {@code status == ACTIVE} AND {@code now ∈ [validFrom, validTo ?? +∞]}.
     */
    public boolean isActiveAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return status == DelegationStatus.ACTIVE
                && !now.isBefore(validFrom)
                && (validTo == null || !now.isAfter(validTo));
    }

    /**
     * True iff this grant's scope covers {@code approvalRequestId} (TASK-ERP-BE-017).
     * A {@code GLOBAL} grant covers every request; a {@code REQUEST} grant covers
     * only the request whose id equals {@code scopeRequestId}. Pure — the single
     * place the scope semantics live; the resolver applies it as the authoritative
     * in-domain re-check over the SQL predicate (defense-in-depth).
     */
    public boolean coversRequest(String approvalRequestId) {
        return scope == DelegationScope.GLOBAL
                || (scope == DelegationScope.REQUEST
                        && scopeRequestId != null
                        && scopeRequestId.equals(approvalRequestId));
    }
}
