package com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "reorder_policy")
@IdClass(ReorderPolicyJpaEntity.PK.class)
@Getter
@Setter
@NoArgsConstructor
public class ReorderPolicyJpaEntity {

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
