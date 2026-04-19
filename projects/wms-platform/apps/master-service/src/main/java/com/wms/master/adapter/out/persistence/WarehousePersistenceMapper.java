package com.wms.master.adapter.out.persistence;

import com.wms.master.domain.model.Warehouse;
import org.springframework.stereotype.Component;

/**
 * Domain ↔ JPA entity translation. Package-private — only the persistence
 * adapter uses it.
 *
 * <p>Domain {@link Warehouse} is a framework-free POJO. JPA manages
 * {@link WarehouseJpaEntity}. All persistence-side concerns (optimistic
 * locking version bumping, JPA-managed entity mutation) stay in this adapter.
 */
@Component
class WarehousePersistenceMapper {

    WarehouseJpaEntity toNewEntity(Warehouse warehouse) {
        return new WarehouseJpaEntity(
                warehouse.getId(),
                warehouse.getWarehouseCode(),
                warehouse.getName(),
                warehouse.getAddress(),
                warehouse.getTimezone(),
                warehouse.getStatus(),
                warehouse.getVersion(),
                warehouse.getCreatedAt(),
                warehouse.getCreatedBy(),
                warehouse.getUpdatedAt(),
                warehouse.getUpdatedBy());
    }

    /**
     * Insert path: emits an entity with {@code version=null} so Hibernate treats
     * it as unpersisted and runs INSERT (otherwise a non-null {@code @Version}
     * + pre-assigned id triggers {@code detached entity passed to persist}).
     */
    WarehouseJpaEntity toInsertEntity(Warehouse warehouse) {
        return new WarehouseJpaEntity(
                warehouse.getId(),
                warehouse.getWarehouseCode(),
                warehouse.getName(),
                warehouse.getAddress(),
                warehouse.getTimezone(),
                warehouse.getStatus(),
                null,
                warehouse.getCreatedAt(),
                warehouse.getCreatedBy(),
                warehouse.getUpdatedAt(),
                warehouse.getUpdatedBy());
    }

    /**
     * Copy mutable fields from the domain aggregate onto a managed JPA entity
     * so Hibernate dirty-checking detects the change and bumps {@code @Version}.
     * Used on update / deactivate / reactivate paths.
     */
    void mergeMutable(Warehouse source, WarehouseJpaEntity target) {
        target.applyMutableFields(
                source.getName(),
                source.getAddress(),
                source.getTimezone(),
                source.getStatus(),
                source.getUpdatedAt(),
                source.getUpdatedBy());
    }

    Warehouse toDomain(WarehouseJpaEntity entity) {
        return Warehouse.reconstitute(
                entity.getId(),
                entity.getWarehouseCode(),
                entity.getName(),
                entity.getAddress(),
                entity.getTimezone(),
                entity.getStatus(),
                entity.getVersion() == null ? 0L : entity.getVersion(),
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy());
    }
}
