package com.wms.inventory.adapter.out.persistence.transfer;

import com.wms.inventory.application.port.out.StockTransferRepository;
import com.wms.inventory.application.query.TransferListCriteria;
import com.wms.inventory.application.result.PageView;
import com.wms.inventory.application.result.TransferView;
import com.wms.inventory.domain.model.StockTransfer;
import com.wms.inventory.domain.model.TransferReasonCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StockTransferPersistenceAdapter implements StockTransferRepository {

    private final StockTransferJpaRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    public StockTransferPersistenceAdapter(StockTransferJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public StockTransfer insert(StockTransfer t) {
        StockTransferJpaEntity entity = new StockTransferJpaEntity(
                t.id(), t.warehouseId(),
                t.sourceLocationId(), t.targetLocationId(),
                t.skuId(), t.lotId(), t.quantity(),
                t.reasonCode().name(), t.reasonNote(),
                t.actorId(), t.idempotencyKey(),
                t.version(), t.createdAt(), t.createdBy(),
                t.updatedAt(), t.updatedBy());
        entityManager.persist(entity);
        return toDomain(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StockTransfer> findById(UUID id) {
        return repository.findById(id).map(StockTransferPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageView<TransferView> list(TransferListCriteria c) {
        StringBuilder where = new StringBuilder("WHERE 1=1");
        List<Object[]> params = new ArrayList<>();
        if (c.warehouseId() != null) {
            where.append(" AND t.warehouse_id = :warehouseId");
            params.add(new Object[]{"warehouseId", c.warehouseId()});
        }
        if (c.sourceLocationId() != null) {
            where.append(" AND t.source_location_id = :sourceLocationId");
            params.add(new Object[]{"sourceLocationId", c.sourceLocationId()});
        }
        if (c.targetLocationId() != null) {
            where.append(" AND t.target_location_id = :targetLocationId");
            params.add(new Object[]{"targetLocationId", c.targetLocationId()});
        }
        if (c.skuId() != null) {
            where.append(" AND t.sku_id = :skuId");
            params.add(new Object[]{"skuId", c.skuId()});
        }
        if (c.reasonCode() != null) {
            where.append(" AND t.reason_code = :reasonCode");
            params.add(new Object[]{"reasonCode", c.reasonCode().name()});
        }
        if (c.createdAfter() != null) {
            where.append(" AND t.created_at >= :createdAfter");
            params.add(new Object[]{"createdAfter", c.createdAfter()});
        }
        if (c.createdBefore() != null) {
            where.append(" AND t.created_at <= :createdBefore");
            params.add(new Object[]{"createdBefore", c.createdBefore()});
        }

        String dataSql = "SELECT t.id, t.warehouse_id, t.source_location_id, t.target_location_id, "
                + "t.sku_id, t.lot_id, t.quantity, t.reason_code, t.reason_note, "
                + "t.actor_id, t.created_at "
                + "FROM stock_transfer t " + where
                + " ORDER BY t.created_at DESC LIMIT :pageSize OFFSET :pageOffset";
        String countSql = "SELECT COUNT(*) FROM stock_transfer t " + where;

        var dataQuery = entityManager.createNativeQuery(dataSql, Object[].class);
        var countQuery = entityManager.createNativeQuery(countSql);
        for (Object[] p : params) {
            dataQuery.setParameter((String) p[0], p[1]);
            countQuery.setParameter((String) p[0], p[1]);
        }
        dataQuery.setParameter("pageSize", c.size());
        dataQuery.setParameter("pageOffset", c.page() * c.size());

        @SuppressWarnings("unchecked")
        List<Object[]> rows = (List<Object[]>) dataQuery.getResultList();
        long total = ((Number) countQuery.getSingleResult()).longValue();

        List<TransferView> views = rows.stream().map(row -> new TransferView(
                (UUID) row[0],
                (UUID) row[1],
                (UUID) row[2],
                (UUID) row[3],
                (UUID) row[4],
                (UUID) row[5],
                ((Number) row[6]).intValue(),
                TransferReasonCode.valueOf((String) row[7]),
                (String) row[8],
                (String) row[9],
                ((java.sql.Timestamp) row[10]).toInstant())).toList();
        return PageView.of(views, c.page(), c.size(), total, "createdAt,desc");
    }

    private static StockTransfer toDomain(StockTransferJpaEntity e) {
        return StockTransfer.restore(
                e.getId(), e.getWarehouseId(),
                e.getSourceLocationId(), e.getTargetLocationId(),
                e.getSkuId(), e.getLotId(), e.getQuantity(),
                TransferReasonCode.valueOf(e.getReasonCode()), e.getReasonNote(),
                e.getActorId(), e.getIdempotencyKey(),
                e.getVersion(), e.getCreatedAt(), e.getCreatedBy(),
                e.getUpdatedAt(), e.getUpdatedBy());
    }
}
