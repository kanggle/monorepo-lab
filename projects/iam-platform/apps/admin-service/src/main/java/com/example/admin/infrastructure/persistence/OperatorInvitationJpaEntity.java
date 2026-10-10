package com.example.admin.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * TASK-MONO-772 S2 (ADR-MONO-080 D6 · R4) — JPA entity for {@code operator_invitation} (Flyway V0050,
 * data-model.md § {@code operator_invitation}). Lives under the {@code JpaConfig} scan base so it is validated by
 * {@code ddl-auto=validate}.
 *
 * <p>Only {@link #create} goes through the entity. Every state change after that (cancel · resend · delivery
 * result · S3's accept) is a <b>conditional UPDATE</b> in {@link OperatorInvitationJpaRepository} — the
 * {@code WHERE status = 'PENDING'} predicate is what makes one of two racing writers lose, which a read-then-save
 * cannot guarantee.
 *
 * <p>The generated column {@code pending_key} is deliberately NOT mapped: it is never written by the application
 * and its only job is the UNIQUE key «one PENDING per (tenant_id, email)».
 *
 * <p>🔴 There is no field for the raw token — only {@link #tokenHash}. {@code toString} is not generated, so no log
 * line can print the hash either.
 */
@Entity
@Table(name = "operator_invitation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OperatorInvitationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "invitation_id", length = 36, nullable = false, unique = true)
    private String invitationId;

    @Column(name = "tenant_id", length = 32, nullable = false)
    private String tenantId;

    @Column(name = "email", length = 255, nullable = false)
    private String email;

    @Column(name = "display_name", length = 120, nullable = false)
    private String displayName;

    @Column(name = "roles", length = 512, nullable = false)
    private String roles;

    @Column(name = "token_hash", length = 64, nullable = false, unique = true, columnDefinition = "CHAR(64)")
    private String tokenHash;

    @Column(name = "status", length = 16, nullable = false)
    private String status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "invited_by", nullable = false)
    private Long invitedBy;

    @Column(name = "last_delivery_status", length = 20)
    private String lastDeliveryStatus;

    @Column(name = "last_delivery_at")
    private Instant lastDeliveryAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "accepted_account_id", length = 36)
    private String acceptedAccountId;

    @Column(name = "accepted_operator_id")
    private Long acceptedOperatorId;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * The optimistic-lock counter. Deliberately NOT {@code @Version}: the entity is never updated through a merge —
     * every change is a conditional UPDATE that compares and bumps this column itself
     * ({@link OperatorInvitationJpaRepository#rotateIfPending}), so the JPA version machinery would only get in
     * the way of those bulk statements.
     */
    @Column(name = "version", nullable = false)
    private int version;

    /** A new {@code PENDING} invitation. The caller passes the hash, never the token. */
    public static OperatorInvitationJpaEntity create(String invitationId, String tenantId, String email,
                                                     String displayName, String roles, String tokenHash,
                                                     Instant expiresAt, Long invitedBy, Instant now) {
        OperatorInvitationJpaEntity e = new OperatorInvitationJpaEntity();
        e.invitationId = invitationId;
        e.tenantId = tenantId;
        e.email = email;
        e.displayName = displayName;
        e.roles = roles;
        e.tokenHash = tokenHash;
        e.status = "PENDING";
        e.expiresAt = expiresAt;
        e.invitedBy = invitedBy;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }
}
