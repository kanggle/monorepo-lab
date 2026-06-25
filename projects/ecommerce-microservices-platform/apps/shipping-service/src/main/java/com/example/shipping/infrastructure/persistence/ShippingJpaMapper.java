package com.example.shipping.infrastructure.persistence;

import com.example.shipping.domain.model.Shipping;
import com.example.shipping.domain.model.StatusHistoryEntry;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ShippingJpaMapper {

    public Shipping toDomain(ShippingJpaEntity entity) {
        List<StatusHistoryEntry> history = entity.getStatusHistory().stream()
                .map(h -> new StatusHistoryEntry(h.getStatus(), h.getChangedAt()))
                .toList();

        return Shipping.reconstitute(
                entity.getShippingId(),
                entity.getTenantId(),
                entity.getOrderId(),
                entity.getUserId(),
                entity.getStatus(),
                entity.getTrackingNumber(),
                entity.getCarrier(),
                entity.isWmsRouted(),
                history,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public ShippingJpaEntity toEntity(Shipping shipping) {
        ShippingJpaEntity entity = ShippingJpaEntity.create(
                shipping.getShippingId(),
                shipping.getTenantId(),
                shipping.getOrderId(),
                shipping.getUserId(),
                shipping.getStatus(),
                shipping.getTrackingNumber(),
                shipping.getCarrier(),
                shipping.isWmsRouted(),
                shipping.getCreatedAt(),
                shipping.getUpdatedAt()
        );

        for (StatusHistoryEntry entry : shipping.getStatusHistory()) {
            StatusHistoryJpaEntity historyEntity = StatusHistoryJpaEntity.create(entry.status(), entry.changedAt());
            entity.addStatusHistory(historyEntity);
        }

        return entity;
    }
}
