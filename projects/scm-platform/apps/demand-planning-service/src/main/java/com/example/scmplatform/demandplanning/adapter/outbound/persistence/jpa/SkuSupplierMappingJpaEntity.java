package com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "sku_supplier_map")
@IdClass(SkuSupplierMappingJpaEntity.PK.class)
@Getter
@Setter
@NoArgsConstructor
public class SkuSupplierMappingJpaEntity {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Id
    @Column(name = "sku_code", nullable = false)
    private String skuCode;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "default_order_qty", nullable = false)
    private int defaultOrderQty;

    @Column(name = "lead_time_days", nullable = false)
    private int leadTimeDays;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

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
