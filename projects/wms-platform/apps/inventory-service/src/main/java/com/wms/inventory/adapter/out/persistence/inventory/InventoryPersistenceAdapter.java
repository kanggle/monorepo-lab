package com.wms.inventory.adapter.out.persistence.inventory;

import com.wms.inventory.application.port.out.InventoryRepository;
import com.wms.inventory.application.query.InventoryListCriteria;
import com.wms.inventory.application.result.InventoryView;
import com.wms.inventory.application.result.PageView;
import com.wms.inventory.domain.model.Inventory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence adapter for {@link Inventory}.
 *
 * <p>Read-side projections enrich rows with {@code locationCode} / {@code skuCode}
 * / {@code lotNo} via LEFT JOINs against the master read-model snapshot tables.
 * The display fields are nullable to tolerate the snapshot-startup race.
 */
@Component
public class InventoryPersistenceAdapter implements InventoryRepository {

    private final InventoryJpaRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    public InventoryPersistenceAdapter(InventoryJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Inventory> findById(UUID id) {
        return repository.findById(id).map(InventoryPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Inventory> findByKey(UUID locationId, UUID skuId, UUID lotId) {
        return repository.findByKey(locationId, skuId, lotId)
                .map(InventoryPersistenceMapper::toDomain);
    }

    @Override
    public Inventory insert(Inventory inventory) {
        InventoryJpaEntity entity = InventoryPersistenceMapper.toEntity(inventory);
        entityManager.persist(entity);
        return InventoryPersistenceMapper.toDomain(entity);
    }

    @Override
    public Inventory updateWithVersionCheck(Inventory inventory) {
        InventoryJpaEntity managed = repository.findById(inventory.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Inventory row not found for update: " + inventory.id()));
        if (managed.getVersion() != inventory.version()) {
            // The aggregate the caller mutated was loaded at version N; the
            // managed row is at version M ≠ N. This is the optimistic-lock
            // collision — Spring translates the exception thrown below into
            // OptimisticLockingFailureException, which the application
            // service maps to 409 CONFLICT.
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
                    InventoryJpaEntity.class, inventory.id());
        }
        managed.copyMutableFields(
                inventory.availableQty(),
                inventory.reservedQty(),
                inventory.damagedQty(),
                inventory.lastMovementAt(),
                inventory.updatedAt(),
                inventory.updatedBy());
        entityManager.flush();
        return InventoryPersistenceMapper.toDomain(managed);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InventoryView> findViewById(UUID id) {
        var query = entityManager
                .createNativeQuery(buildViewSelect("WHERE i.id = :id"), Object[].class)
                .setParameter("id", id);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = (List<Object[]>) query.getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of(mapRow(rows.get(0)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InventoryView> findViewByKey(UUID locationId, UUID skuId, UUID lotId) {
        String where = lotId == null
                ? "WHERE i.location_id = :locationId AND i.sku_id = :skuId AND i.lot_id IS NULL"
                : "WHERE i.location_id = :locationId AND i.sku_id = :skuId AND i.lot_id = :lotId";
        var query = entityManager
                .createNativeQuery(buildViewSelect(where), Object[].class)
                .setParameter("locationId", locationId)
                .setParameter("skuId", skuId);
        if (lotId != null) {
            query.setParameter("lotId", lotId);
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = (List<Object[]>) query.getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of(mapRow(rows.get(0)));
    }

    @Override
    @Transactional(readOnly = true)
    public PageView<InventoryView> listViews(InventoryListCriteria c) {
        StringBuilder where = new StringBuilder("WHERE 1=1");
        if (c.warehouseId() != null) where.append(" AND i.warehouse_id = :warehouseId");
        if (c.locationId() != null) where.append(" AND i.location_id = :locationId");
        if (c.skuId() != null) where.append(" AND i.sku_id = :skuId");
        if (c.lotId() != null) where.append(" AND i.lot_id = :lotId");
        if (Boolean.TRUE.equals(c.hasStock())) {
            where.append(" AND (i.available_qty > 0 OR i.reserved_qty > 0 OR i.damaged_qty > 0)");
        }
        if (c.minAvailable() != null) {
            where.append(" AND i.available_qty >= :minAvailable");
        }

        String orderBy = " ORDER BY " + sortClause(c.sort());
        String limitOffset = " LIMIT :pageSize OFFSET :pageOffset";

        var dataQuery = entityManager.createNativeQuery(
                buildViewSelect(where.toString()) + orderBy + limitOffset, Object[].class);
        var countQuery = entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM inventory i " + where);

        bindFilters(dataQuery, countQuery, c);
        dataQuery.setParameter("pageSize", c.size());
        dataQuery.setParameter("pageOffset", c.page() * c.size());

        @SuppressWarnings("unchecked")
        List<Object[]> rows = (List<Object[]>) dataQuery.getResultList();
        long total = ((Number) countQuery.getSingleResult()).longValue();

        List<InventoryView> views = rows.stream().map(InventoryPersistenceAdapter::mapRow).toList();
        return PageView.of(views, c.page(), c.size(), total, c.sort());
    }

    private static void bindFilters(jakarta.persistence.Query dataQuery,
                                    jakarta.persistence.Query countQuery,
                                    InventoryListCriteria c) {
        if (c.warehouseId() != null) {
            dataQuery.setParameter("warehouseId", c.warehouseId());
            countQuery.setParameter("warehouseId", c.warehouseId());
        }
        if (c.locationId() != null) {
            dataQuery.setParameter("locationId", c.locationId());
            countQuery.setParameter("locationId", c.locationId());
        }
        if (c.skuId() != null) {
            dataQuery.setParameter("skuId", c.skuId());
            countQuery.setParameter("skuId", c.skuId());
        }
        if (c.lotId() != null) {
            dataQuery.setParameter("lotId", c.lotId());
            countQuery.setParameter("lotId", c.lotId());
        }
        if (c.minAvailable() != null) {
            dataQuery.setParameter("minAvailable", c.minAvailable());
            countQuery.setParameter("minAvailable", c.minAvailable());
        }
    }

    private static String buildViewSelect(String whereClause) {
        return """
                SELECT i.id, i.warehouse_id, i.location_id, ls.location_code,
                       i.sku_id, ss.sku_code, i.lot_id, lots.lot_no,
                       i.available_qty, i.reserved_qty, i.damaged_qty,
                       i.last_movement_at, i.version, i.created_at, i.updated_at
                  FROM inventory i
                  LEFT JOIN location_snapshot ls ON ls.id = i.location_id
                  LEFT JOIN sku_snapshot ss      ON ss.id = i.sku_id
                  LEFT JOIN lot_snapshot lots    ON lots.id = i.lot_id
                """ + whereClause;
    }

    private static String sortClause(String sort) {
        // Whitelist sortable fields. Caller-supplied junk falls back to the default.
        String field = "updated_at";
        String direction = "DESC";
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", 2);
            switch (parts[0].trim()) {
                case "updatedAt" -> field = "updated_at";
                case "createdAt" -> field = "created_at";
                case "availableQty" -> field = "available_qty";
                case "lastMovementAt" -> field = "last_movement_at";
                default -> { /* keep default */ }
            }
            if (parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())) {
                direction = "ASC";
            }
        }
        return "i." + field + " " + direction;
    }

    @SuppressWarnings("unchecked")
    private static InventoryView mapRow(Object[] row) {
        UUID id = (UUID) row[0];
        UUID warehouseId = (UUID) row[1];
        UUID locationId = (UUID) row[2];
        String locationCode = (String) row[3];
        UUID skuId = (UUID) row[4];
        String skuCode = (String) row[5];
        UUID lotId = (UUID) row[6];
        String lotNo = (String) row[7];
        int available = ((Number) row[8]).intValue();
        int reserved = ((Number) row[9]).intValue();
        int damaged = ((Number) row[10]).intValue();
        Instant lastMovement = ((java.sql.Timestamp) row[11]).toInstant();
        long version = ((Number) row[12]).longValue();
        Instant createdAt = ((java.sql.Timestamp) row[13]).toInstant();
        Instant updatedAt = ((java.sql.Timestamp) row[14]).toInstant();
        return new InventoryView(
                id, warehouseId, locationId, locationCode,
                skuId, skuCode, lotId, lotNo,
                available, reserved, damaged, available + reserved + damaged,
                lastMovement, version, createdAt, updatedAt);
    }
}
