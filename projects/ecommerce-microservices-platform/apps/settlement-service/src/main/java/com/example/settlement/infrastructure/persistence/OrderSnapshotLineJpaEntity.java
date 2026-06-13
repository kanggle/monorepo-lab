package com.example.settlement.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One cached snapshot line — the seller this line is attributed to and its gross
 * amount in minor units ({@code unitPrice × quantity}).
 */
@Entity
@Table(name = "settlement_order_snapshot_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderSnapshotLineJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderSnapshotJpaEntity snapshot;

    @Column(name = "seller_id", nullable = false)
    private String sellerId;

    @Column(name = "gross_minor", nullable = false)
    private long grossMinor;

    static OrderSnapshotLineJpaEntity of(String sellerId, long grossMinor) {
        OrderSnapshotLineJpaEntity e = new OrderSnapshotLineJpaEntity();
        e.sellerId = sellerId;
        e.grossMinor = grossMinor;
        return e;
    }

    void attachTo(OrderSnapshotJpaEntity snapshot) {
        this.snapshot = snapshot;
    }
}
