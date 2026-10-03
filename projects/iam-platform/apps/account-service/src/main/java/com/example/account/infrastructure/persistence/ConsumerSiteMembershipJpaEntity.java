package com.example.account.infrastructure.persistence;

import com.example.account.domain.consumerpool.ConsumerSiteLeftBy;
import com.example.account.domain.consumerpool.ConsumerSiteMembership;
import com.example.account.domain.consumerpool.ConsumerSiteMembershipStatus;
import com.example.account.domain.tenant.TenantId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * TASK-BE-614 — JPA mapping for {@code consumer_site_memberships} (V0030).
 *
 * <p>Read side only: rows are written by the native insert in
 * {@link ConsumerSiteMembershipJpaRepository#insertMembership} so the FK to {@code accounts}
 * cannot be reordered by Hibernate's insert sorting. The entity also lets the site-scoped account
 * listing express its membership predicate in JPQL ({@link AccountJpaRepository}).
 */
@Entity
@Table(name = "consumer_site_memberships")
@IdClass(ConsumerSiteMembershipJpaEntity.MembershipId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsumerSiteMembershipJpaEntity {

    @Id
    @Column(name = "account_id", nullable = false, length = 36)
    private String accountId;

    @Id
    @Column(name = "site_tenant_id", nullable = false, length = 32)
    private String siteTenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ConsumerSiteMembershipStatus status;

    @Column(name = "consented_at", nullable = false)
    private Instant consentedAt;

    /** TASK-BE-619 (V0032) — when the membership became LEFT; null while ACTIVE. */
    @Column(name = "left_at")
    private Instant leftAt;

    /** TASK-BE-619 (V0032) — who made it LEFT ({@code SELF} | {@code OPERATOR}); null while ACTIVE. */
    @Enumerated(EnumType.STRING)
    @Column(name = "left_by", length = 20)
    private ConsumerSiteLeftBy leftBy;

    /** TASK-BE-619 (V0032) — the operator id (OPERATOR) or the account id (SELF). */
    @Column(name = "left_by_actor_id", length = 64)
    private String leftByActorId;

    public ConsumerSiteMembership toDomain() {
        return ConsumerSiteMembership.reconstitute(
                accountId, new TenantId(siteTenantId), status, consentedAt, leftAt, leftBy, leftByActorId);
    }

    /** Composite key for {@link IdClass}. */
    public static class MembershipId implements Serializable {

        private String accountId;
        private String siteTenantId;

        public MembershipId() {}

        public MembershipId(String accountId, String siteTenantId) {
            this.accountId = accountId;
            this.siteTenantId = siteTenantId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MembershipId other)) return false;
            return Objects.equals(accountId, other.accountId)
                    && Objects.equals(siteTenantId, other.siteTenantId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(accountId, siteTenantId);
        }
    }
}
