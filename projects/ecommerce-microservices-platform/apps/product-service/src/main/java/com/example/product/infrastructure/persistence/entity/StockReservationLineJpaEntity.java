package com.example.product.infrastructure.persistence.entity;

import com.example.product.domain.model.reservation.StockReservationLine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Child line of a {@link StockReservationJpaEntity} (TASK-BE-428). Table
 * {@code stock_reservation_lines}, FK {@code reservation_id} → {@code stock_reservations}.
 */
@Entity
@Table(name = "stock_reservation_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockReservationLineJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private StockReservationJpaEntity reservation;

    @Column(name = "variant_id", columnDefinition = "uuid", nullable = false)
    private UUID variantId;

    @Column(name = "product_id", columnDefinition = "uuid", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private int quantity;

    @Version
    private long version;

    public static StockReservationLineJpaEntity from(StockReservationLine line,
                                                     StockReservationJpaEntity reservation) {
        StockReservationLineJpaEntity entity = new StockReservationLineJpaEntity();
        entity.id = UUID.randomUUID();
        entity.reservation = reservation;
        entity.variantId = line.variantId();
        entity.productId = line.productId();
        entity.quantity = line.quantity();
        return entity;
    }

    public StockReservationLine toDomain() {
        return new StockReservationLine(variantId, productId, quantity);
    }
}
