package com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;
import java.time.Instant;

// Persistable (like ReorderSuggestion): an assigned id + @Version makes Spring
// Data's default isNew() merge a fresh entity -> Hibernate treats it as detached
// -> UPDATE ... WHERE version -> 0 rows -> StaleObjectStateException. The adapter's
// findBy-then-save upsert still works (a loaded entity is marked persisted=true via
// @PostLoad -> merge/update), while a brand-new row inserts cleanly.
@Entity
@Table(name = "reorder_policy")
@IdClass(ReorderPolicyJpaEntity.PK.class)
@Getter
@Setter
@NoArgsConstructor
public class ReorderPolicyJpaEntity implements Persistable<ReorderPolicyJpaEntity.PK> {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Id
    @Column(name = "sku_code", nullable = false)
    private String skuCode;

    @Column(name = "reorder_point", nullable = false)
    private int reorderPoint;

    @Column(name = "safety_stock", nullable = false)
    private int safetyStock;

    @Column(name = "reorder_qty", nullable = false)
    private int reorderQty;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    private boolean persisted;

    @Override
    public PK getId() {
        return new PK(tenantId, skuCode);
    }

    @Override
    public boolean isNew() {
        return !persisted;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        this.persisted = true;
    }

    public static class PK implements Serializable {
        public String tenantId;
        public String skuCode;

        public PK() {}
        public PK(String tenantId, String skuCode) {
            this.tenantId = tenantId;
            this.skuCode = skuCode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return java.util.Objects.equals(tenantId, pk.tenantId)
                    && java.util.Objects.equals(skuCode, pk.skuCode);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(tenantId, skuCode);
        }
    }
}
