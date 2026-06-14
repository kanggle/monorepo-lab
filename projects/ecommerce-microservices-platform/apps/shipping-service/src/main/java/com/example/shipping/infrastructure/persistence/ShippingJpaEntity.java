package com.example.shipping.infrastructure.persistence;

import com.example.shipping.domain.model.ShippingStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "shippings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShippingJpaEntity {

    @Id
    @Column(name = "shipping_id", nullable = false)
    private String shippingId;

    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ShippingStatus status;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "carrier")
    private String carrier;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "shipping", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("changedAt ASC")
    private List<StatusHistoryJpaEntity> statusHistory = new ArrayList<>();

    static ShippingJpaEntity create(String shippingId, String tenantId, String orderId, String userId,
                                     ShippingStatus status, String trackingNumber, String carrier,
                                     Instant createdAt, Instant updatedAt) {
        ShippingJpaEntity entity = new ShippingJpaEntity();
        entity.shippingId = shippingId;
        entity.tenantId = tenantId;
        entity.orderId = orderId;
        entity.userId = userId;
        entity.status = status;
        entity.trackingNumber = trackingNumber;
        entity.carrier = carrier;
        entity.createdAt = createdAt;
        entity.updatedAt = updatedAt;
        return entity;
    }

    void updateFrom(ShippingStatus status, String trackingNumber, String carrier, Instant updatedAt) {
        this.status = status;
        this.trackingNumber = trackingNumber;
        this.carrier = carrier;
        this.updatedAt = updatedAt;
    }

    void addStatusHistory(StatusHistoryJpaEntity history) {
        history.setShipping(this);
        this.statusHistory.add(history);
    }
}
