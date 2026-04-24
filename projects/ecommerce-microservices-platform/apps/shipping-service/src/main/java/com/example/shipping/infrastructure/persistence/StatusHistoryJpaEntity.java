package com.example.shipping.infrastructure.persistence;

import com.example.shipping.domain.model.ShippingStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "shipping_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StatusHistoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipping_id", nullable = false)
    @Setter(AccessLevel.PACKAGE)
    private ShippingJpaEntity shipping;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ShippingStatus status;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    static StatusHistoryJpaEntity create(ShippingStatus status, Instant changedAt) {
        StatusHistoryJpaEntity entity = new StatusHistoryJpaEntity();
        entity.status = status;
        entity.changedAt = changedAt;
        return entity;
    }
}
