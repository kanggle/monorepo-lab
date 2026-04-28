package com.wms.inventory.adapter.out.persistence.adjustment;

import com.wms.inventory.application.port.out.StockAdjustmentRepository;
import com.wms.inventory.application.query.AdjustmentListCriteria;
import com.wms.inventory.application.result.AdjustmentView;
import com.wms.inventory.application.result.PageView;
import com.wms.inventory.domain.model.Bucket;
import com.wms.inventory.domain.model.ReasonCode;
import com.wms.inventory.domain.model.StockAdjustment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StockAdjustmentPersistenceAdapter implements StockAdjustmentRepository {

    private final StockAdjustmentJpaRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    public StockAdjustmentPersistenceAdapter(StockAdjustmentJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public StockAdjustment insert(StockAdjustment a) {
        StockAdjustmentJpaEntity entity = new StockAdjustmentJpaEntity(
                a.id(), a.inventoryId(), a.bucket().name(), a.delta(),
                a.reasonCode().name(), a.reasonNote(), a.actorId(), a.idempotencyKey(),
                a.version(), a.createdAt(), a.createdBy(),
                a.updatedAt(), a.updatedBy());
        entityManager.persist(entity);
        return toDomain(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StockAdjustment> findById(UUID id) {
        return repository.findById(id).map(StockAdjustmentPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageView<AdjustmentView> list(AdjustmentListCriteria c) {
        StringBuilder where = new StringBuilder("WHERE 1=1");
        List<Object[]> params = new ArrayList<>();
        if (c.inventoryId() != null) {
            where.append(" AND a.inventory_id = :inventoryId");
            params.add(new Object[]{"inventoryId", c.inventoryId()});
        }
        if (c.reasonCode() != null) {
            where.append(" AND a.reason_code = :reasonCode");
            params.add(new Object[]{"reasonCode", c.reasonCode().name()});
        }
        if (c.createdAfter() != null) {
            where.append(" AND a.created_at >= :createdAfter");
            params.add(new Object[]{"createdAfter", c.createdAfter()});
        }
        if (c.createdBefore() != null) {
            where.append(" AND a.created_at <= :createdBefore");
            params.add(new Object[]{"createdBefore", c.createdBefore()});
        }

        String dataSql = "SELECT a.id, a.inventory_id, a.bucket, a.delta, a.reason_code, "
                + "a.reason_note, a.actor_id, a.created_at "
                + "FROM stock_adjustment a " + where
                + " ORDER BY a.created_at DESC LIMIT :pageSize OFFSET :pageOffset";
        String countSql = "SELECT COUNT(*) FROM stock_adjustment a " + where;

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

        List<AdjustmentView> views = rows.stream().map(row -> new AdjustmentView(
                (UUID) row[0],
                (UUID) row[1],
                Bucket.valueOf((String) row[2]),
                ((Number) row[3]).intValue(),
                ReasonCode.valueOf((String) row[4]),
                (String) row[5],
                (String) row[6],
                ((java.sql.Timestamp) row[7]).toInstant())).toList();
        return PageView.of(views, c.page(), c.size(), total, "createdAt,desc");
    }

    private static StockAdjustment toDomain(StockAdjustmentJpaEntity e) {
        return StockAdjustment.restore(
                e.getId(), e.getInventoryId(),
                Bucket.valueOf(e.getBucket()), e.getDelta(),
                ReasonCode.valueOf(e.getReasonCode()), e.getReasonNote(),
                e.getActorId(), e.getIdempotencyKey(),
                e.getVersion(), e.getCreatedAt(), e.getCreatedBy(),
                e.getUpdatedAt(), e.getUpdatedBy());
    }
}
