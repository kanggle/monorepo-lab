package com.example.product.infrastructure.persistence.entity;

import com.example.product.domain.model.reservation.ReservationStatus;
import com.example.product.domain.model.reservation.StockReservation;
import com.example.product.domain.model.reservation.StockReservationLine;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA mapping of the {@link StockReservation} aggregate (TASK-BE-428). Table
 * {@code stock_reservations} with a unique {@code order_id} and {@code @Version} optimistic
 * lock — concurrent reserve/release/restock-retry on the same order serialize via the version.
 * Lines cascade as a child collection ({@link StockReservationLineJpaEntity}).
 */
@Entity
@Table(name = "stock_reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockReservationJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true, length = 64)
    private String orderId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "payment_received", nullable = false)
    private boolean paymentReceived;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<StockReservationLineJpaEntity> lines = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    public static StockReservationJpaEntity from(StockReservation reservation) {
        StockReservationJpaEntity entity = new StockReservationJpaEntity();
        entity.id = reservation.getId();
        entity.orderId = reservation.getOrderId();
        entity.tenantId = reservation.getTenantId();
        entity.syncFrom(reservation);
        entity.createdAt = reservation.getCreatedAt();
        return entity;
    }

    /** Re-applies the aggregate's mutable state (status, payment flag, lines, updatedAt). */
    public void syncFrom(StockReservation reservation) {
        this.status = reservation.getStatus();
        this.paymentReceived = reservation.isPaymentReceived();
        this.updatedAt = reservation.getUpdatedAt();
        // Lines are append-only in the aggregate (filled once); rebuild only when empty.
        if (this.lines.isEmpty() && !reservation.getLines().isEmpty()) {
            for (StockReservationLine line : reservation.getLines()) {
                this.lines.add(StockReservationLineJpaEntity.from(line, this));
            }
        }
    }

    public StockReservation toDomain() {
        List<StockReservationLine> domainLines = lines.stream()
                .map(StockReservationLineJpaEntity::toDomain)
                .toList();
        return StockReservation.reconstitute(
                id, orderId, tenantId, status, paymentReceived, domainLines, createdAt, updatedAt);
    }
}
