package com.wms.inventory.adapter.out.persistence.movement;

import com.wms.inventory.application.port.out.InventoryMovementRepository;
import com.wms.inventory.application.query.MovementListCriteria;
import com.wms.inventory.application.result.MovementView;
import com.wms.inventory.application.result.PageView;
import com.wms.inventory.domain.model.Bucket;
import com.wms.inventory.domain.model.InventoryMovement;
import com.wms.inventory.domain.model.MovementType;
import com.wms.inventory.domain.model.ReasonCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InventoryMovementPersistenceAdapter implements InventoryMovementRepository {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final InventoryMovementJpaRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    public InventoryMovementPersistenceAdapter(InventoryMovementJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(InventoryMovement movement) {
        InventoryMovementJpaEntity entity = new InventoryMovementJpaEntity(
                movement.id(),
                movement.inventoryId(),
                movement.movementType().name(),
                movement.bucket().name(),
                movement.delta(),
                movement.qtyBefore(),
                movement.qtyAfter(),
                movement.reasonCode().name(),
                movement.reasonNote(),
                movement.reservationId(),
                movement.transferId(),
                movement.adjustmentId(),
                movement.sourceEventId(),
                movement.actorId(),
                movement.occurredAt());
        repository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public PageView<MovementView> list(MovementListCriteria c) {
        StringBuilder where = new StringBuilder("WHERE 1=1");
        if (c.inventoryId() != null) where.append(" AND m.inventory_id = :inventoryId");
        if (c.movementType() != null) where.append(" AND m.movement_type = :movementType");
        if (c.bucket() != null) where.append(" AND m.bucket = :bucket");
        if (c.reasonCode() != null) where.append(" AND m.reason_code = :reasonCode");
        if (c.occurredAfter() != null) where.append(" AND m.occurred_at >= :occurredAfter");
        if (c.occurredBefore() != null) where.append(" AND m.occurred_at <= :occurredBefore");
        if (c.locationId() != null || c.skuId() != null) {
            where.append(" AND m.inventory_id IN ("
                    + "SELECT i.id FROM inventory i WHERE 1=1");
            if (c.locationId() != null) where.append(" AND i.location_id = :locationId");
            if (c.skuId() != null) where.append(" AND i.sku_id = :skuId");
            where.append(")");
        }

        String selectFields = """
                SELECT m.id, m.inventory_id, m.movement_type, m.bucket, m.delta,
                       m.qty_before, m.qty_after, m.reason_code, m.reason_note,
                       m.reservation_id, m.transfer_id, m.adjustment_id,
                       m.source_event_id, m.actor_id, m.occurred_at
                  FROM inventory_movement m
                """;
        String orderBy = " ORDER BY m.occurred_at DESC";
        String limitOffset = " LIMIT :pageSize OFFSET :pageOffset";

        var dataQuery = entityManager.createNativeQuery(
                selectFields + where + orderBy + limitOffset, Object[].class);
        var countQuery = entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM inventory_movement m " + where);

        bindFilters(dataQuery, countQuery, c);
        int size = c.size() <= 0 ? DEFAULT_PAGE_SIZE : c.size();
        dataQuery.setParameter("pageSize", size);
        dataQuery.setParameter("pageOffset", c.page() * size);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = (List<Object[]>) dataQuery.getResultList();
        long total = ((Number) countQuery.getSingleResult()).longValue();

        List<MovementView> views = rows.stream()
                .map(InventoryMovementPersistenceAdapter::mapRow).toList();
        return PageView.of(views, c.page(), size, total, "occurredAt,desc");
    }

    private static void bindFilters(jakarta.persistence.Query dataQuery,
                                    jakarta.persistence.Query countQuery,
                                    MovementListCriteria c) {
        if (c.inventoryId() != null) {
            dataQuery.setParameter("inventoryId", c.inventoryId());
            countQuery.setParameter("inventoryId", c.inventoryId());
        }
        if (c.movementType() != null) {
            dataQuery.setParameter("movementType", c.movementType().name());
            countQuery.setParameter("movementType", c.movementType().name());
        }
        if (c.bucket() != null) {
            dataQuery.setParameter("bucket", c.bucket().name());
            countQuery.setParameter("bucket", c.bucket().name());
        }
        if (c.reasonCode() != null) {
            dataQuery.setParameter("reasonCode", c.reasonCode().name());
            countQuery.setParameter("reasonCode", c.reasonCode().name());
        }
        if (c.occurredAfter() != null) {
            dataQuery.setParameter("occurredAfter", c.occurredAfter());
            countQuery.setParameter("occurredAfter", c.occurredAfter());
        }
        if (c.occurredBefore() != null) {
            dataQuery.setParameter("occurredBefore", c.occurredBefore());
            countQuery.setParameter("occurredBefore", c.occurredBefore());
        }
        if (c.locationId() != null) {
            dataQuery.setParameter("locationId", c.locationId());
            countQuery.setParameter("locationId", c.locationId());
        }
        if (c.skuId() != null) {
            dataQuery.setParameter("skuId", c.skuId());
            countQuery.setParameter("skuId", c.skuId());
        }
    }

    @SuppressWarnings("unchecked")
    private static MovementView mapRow(Object[] row) {
        return new MovementView(
                (UUID) row[0],
                (UUID) row[1],
                MovementType.valueOf((String) row[2]),
                Bucket.valueOf((String) row[3]),
                ((Number) row[4]).intValue(),
                ((Number) row[5]).intValue(),
                ((Number) row[6]).intValue(),
                ReasonCode.valueOf((String) row[7]),
                (String) row[8],
                (UUID) row[9],
                (UUID) row[10],
                (UUID) row[11],
                (UUID) row[12],
                (String) row[13],
                ((java.sql.Timestamp) row[14]).toInstant());
    }

}
