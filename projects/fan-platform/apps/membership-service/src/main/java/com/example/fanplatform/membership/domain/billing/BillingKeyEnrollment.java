package com.example.fanplatform.membership.domain.billing;

import com.example.fanplatform.membership.domain.membership.MembershipTier;
import com.example.fanplatform.membership.infrastructure.crypto.BillingKeyEncryptionConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
 * A fan's stored recurring-billing enrollment (ADR-002 §D1). Decoupled from
 * {@code Membership}/{@code MembershipStatus}: "how this membership renews"
 * (payment method) is a separate axis from "is this membership active now"
 * (access). This entity holds the vendor-opaque {@code billingKey} that the
 * scheduler charges against on renewal.
 *
 * <p><b>At most one ACTIVE enrollment per (accountId, tenantId, tier)</b> —
 * enforced by the partial unique index {@code uq_bke_active_account_tier}
 * (V4 migration) AND by a check-then-replace in {@code EnrollBillingKeyUseCase}.
 * Re-enrolling the same tier deactivates the prior row and inserts a new one
 * (never stacks two chargeable enrollments).
 *
 * <p><b>billingKey secrecy (ADR-002 §D5).</b> The key is a durable capability to
 * charge on the owner's behalf — treated as secret-grade: encrypted at rest via
 * {@link BillingKeyEncryptionConverter} (AES-GCM), NEVER logged, and NEVER placed
 * in any response DTO. The in-memory field holds the plaintext (the converter
 * decrypts on read); {@link #getBillingKey()} is consumed ONLY by the charge path
 * ({@code AutoRenewMembershipsUseCase}). Deliberately no Lombok {@code @ToString}
 * so an accidental {@code log.info("{}", enrollment)} cannot leak it.
 *
 * <p>Follows {@code Membership}'s style: protected no-args ctor, a static factory,
 * {@code @Version} optimistic locking, {@code @Enumerated(STRING)} tier. The
 * {@code domain} layer's only framework dependency is {@code jakarta.persistence}
 * (the same pragmatic JPA exception {@code Membership} already takes); the
 * {@code @Convert} reference to the infrastructure converter is the standard
 * column-encryption wiring and is documented as an extension of that exception.
 */
@Entity
@Table(name = "billing_key_enrollments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingKeyEnrollment {

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

    /**
     * The vendor-opaque billing key. Stored encrypted (column
     * {@code billing_key_encrypted}) via {@link BillingKeyEncryptionConverter};
     * this field is the decrypted plaintext in memory. Never log / never serialize.
     */
    @Convert(converter = BillingKeyEncryptionConverter.class)
    @Column(name = "billing_key_encrypted", length = 1024, nullable = false)
    private String billingKey;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * Factory for a brand-new ACTIVE enrollment. {@code createdAt} MUST already be
     * truncated to micros by the caller (§15) so the in-memory value equals a DB
     * re-read.
     */
    public static BillingKeyEnrollment enroll(String id, String tenantId, String accountId,
                                              MembershipTier tier, String billingKey, Instant createdAt) {
        BillingKeyEnrollment e = new BillingKeyEnrollment();
        e.id = id;
        e.tenantId = tenantId;
        e.accountId = accountId;
        e.tier = tier;
        e.billingKey = billingKey;
        e.active = true;
        e.createdAt = createdAt;
        return e;
    }

    /** Soft-deactivate (cancel auto-renew / supersede on re-enroll). Idempotent. */
    public void deactivate() {
        this.active = false;
    }
}
