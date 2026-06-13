package com.example.settlement.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Cached {@code OrderPlaced} snapshot header — keyed by {@code orderId}, carrying the
 * envelope {@code tenant_id} (settlement's authoritative tenant source, AC-7) and its
 * lines. Upserted idempotently on {@code orderId}.
 */
@Entity
@Table(name = "settlement_order_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderSnapshotJpaEntity {

    @Id
    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderColumn(name = "line_index")
    private List<OrderSnapshotLineJpaEntity> lines = new ArrayList<>();

    static OrderSnapshotJpaEntity of(String orderId, String tenantId) {
        OrderSnapshotJpaEntity e = new OrderSnapshotJpaEntity();
        e.orderId = orderId;
        e.tenantId = tenantId;
        return e;
    }

    void replaceLines(List<OrderSnapshotLineJpaEntity> newLines) {
        this.lines.clear();
        for (OrderSnapshotLineJpaEntity line : newLines) {
            line.attachTo(this);
            this.lines.add(line);
        }
    }

    void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
