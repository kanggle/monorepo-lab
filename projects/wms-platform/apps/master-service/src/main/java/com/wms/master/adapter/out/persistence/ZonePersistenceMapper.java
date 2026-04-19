package com.wms.master.adapter.out.persistence;

import com.wms.master.domain.model.Zone;
import org.springframework.stereotype.Component;

/**
 * Domain ↔ JPA entity translation for Zone. Package-private — only the
 * persistence adapter uses it.
 *
 * <p>Mirrors {@link WarehousePersistenceMapper}:
 * <ul>
 *   <li>{@link #toInsertEntity(Zone)} emits {@code version=null} so Hibernate
 *       runs INSERT rather than MERGE + optimistic-lock check (fix from TASK-BE-001).
 *   <li>{@link #toNewEntity(Zone)} is used for update() flows that need to carry
 *       the caller's {@code @Version} through merge.
 *   <li>{@link #mergeMutable(Zone, ZoneJpaEntity)} copies mutable fields onto a
 *       managed entity so Hibernate dirty-checking bumps {@code @Version}.
 * </ul>
 */
@Component
class ZonePersistenceMapper {

    ZoneJpaEntity toNewEntity(Zone zone) {
        return new ZoneJpaEntity(
                zone.getId(),
                zone.getWarehouseId(),
                zone.getZoneCode(),
                zone.getName(),
                zone.getZoneType(),
                zone.getStatus(),
                zone.getVersion(),
                zone.getCreatedAt(),
                zone.getCreatedBy(),
                zone.getUpdatedAt(),
                zone.getUpdatedBy());
    }

    /**
     * Insert path: emits an entity with {@code version=null} so Spring Data JPA
     * treats it as new and runs INSERT (otherwise a non-null {@code @Version}
     * with a pre-assigned id triggers {@code detached entity passed to persist}).
     */
    ZoneJpaEntity toInsertEntity(Zone zone) {
        return new ZoneJpaEntity(
                zone.getId(),
                zone.getWarehouseId(),
                zone.getZoneCode(),
                zone.getName(),
                zone.getZoneType(),
                zone.getStatus(),
                null,
                zone.getCreatedAt(),
                zone.getCreatedBy(),
                zone.getUpdatedAt(),
                zone.getUpdatedBy());
    }

    /**
     * Copy mutable fields from the domain aggregate onto a managed JPA entity so
     * Hibernate dirty-checking detects the change and bumps {@code @Version}.
     * Used on update / deactivate / reactivate paths.
     */
    void mergeMutable(Zone source, ZoneJpaEntity target) {
        target.applyMutableFields(
                source.getName(),
                source.getZoneType(),
                source.getStatus(),
                source.getUpdatedAt(),
                source.getUpdatedBy());
    }

    Zone toDomain(ZoneJpaEntity entity) {
        return Zone.reconstitute(
                entity.getId(),
                entity.getWarehouseId(),
                entity.getZoneCode(),
                entity.getName(),
                entity.getZoneType(),
                entity.getStatus(),
                entity.getVersion() == null ? 0L : entity.getVersion(),
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy());
    }
}
