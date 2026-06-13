package com.example.settlement.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Per-seller commission-rate override, keyed by {@code (tenant_id, seller_id)} (the
 * surrogate {@code id} is {@code tenant_id + ':' + seller_id}). A row's existence
 * means an operator set an override; absence falls back to the platform default.
 */
@Entity
@Table(name = "seller_commission_rate")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommissionRateJpaEntity {

    @Id
    @Column(name = "id", nullable = false, length = 512)
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "seller_id", nullable = false)
    private String sellerId;

    @Column(name = "rate_bps", nullable = false)
    private int rateBps;

    static String compositeId(String tenantId, String sellerId) {
        return tenantId + ':' + sellerId;
    }

    static CommissionRateJpaEntity of(String tenantId, String sellerId, int rateBps) {
        CommissionRateJpaEntity e = new CommissionRateJpaEntity();
        e.id = compositeId(tenantId, sellerId);
        e.tenantId = tenantId;
        e.sellerId = sellerId;
        e.rateBps = rateBps;
        return e;
    }

    void updateRate(int rateBps) {
        this.rateBps = rateBps;
    }
}
