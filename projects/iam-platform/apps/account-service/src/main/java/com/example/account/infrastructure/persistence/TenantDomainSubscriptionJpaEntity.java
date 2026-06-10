package com.example.account.infrastructure.persistence;

import com.example.account.domain.tenant.SubscriptionStatus;
import com.example.account.domain.tenant.TenantDomainSubscription;
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
 * TASK-BE-322: JPA mapping for {@code tenant_domain_subscription}.
 *
 * <p>Composite natural primary key {@code (tenant_id, domain_key)} (V0019).
 * Read-only in step 1 — no {@code fromDomain}/save path is required by the
 * single read use-case.
 */
@Entity
@Table(name = "tenant_domain_subscription")
@IdClass(TenantDomainSubscriptionJpaEntity.SubscriptionId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantDomainSubscriptionJpaEntity {

    @Id
    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    @Id
    @Column(name = "domain_key", nullable = false, length = 32)
    private String domainKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private SubscriptionStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TenantDomainSubscription toDomain() {
        return TenantDomainSubscription.reconstitute(
                new TenantId(tenantId), domainKey, status, createdAt, updatedAt);
    }

    /**
     * Composite primary key class required by {@link IdClass}. Mirrors the two
     * {@code @Id} columns above and supplies value-based equality.
     */
    public static class SubscriptionId implements Serializable {

        private String tenantId;
        private String domainKey;

        public SubscriptionId() {}

        public SubscriptionId(String tenantId, String domainKey) {
            this.tenantId = tenantId;
            this.domainKey = domainKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SubscriptionId other)) return false;
            return Objects.equals(tenantId, other.tenantId)
                    && Objects.equals(domainKey, other.domainKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, domainKey);
        }
    }
}
