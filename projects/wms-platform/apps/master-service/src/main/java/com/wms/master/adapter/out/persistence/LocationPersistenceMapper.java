package com.wms.master.adapter.out.persistence;

import com.wms.master.domain.model.Location;
import org.springframework.stereotype.Component;

/**
 * Domain ↔ JPA entity translation for Location. Package-private — only the
 * persistence adapter uses it.
 *
 * <p>Mirrors {@link WarehousePersistenceMapper} / {@link ZonePersistenceMapper}:
 * <ul>
 *   <li>{@link #toInsertEntity(Location)} emits {@code version=null} so
 *       Hibernate runs INSERT rather than MERGE + optimistic-lock check
 *       (fix from TASK-BE-001).
 *   <li>{@link #toNewEntity(Location)} is used for update() flows that need to
 *       carry the caller's {@code @Version} through merge.
 *   <li>{@link #mergeMutable(Location, LocationJpaEntity)} copies mutable fields
 *       onto a managed entity so Hibernate dirty-checking bumps {@code @Version}.
 * </ul>
 */
@Component
class LocationPersistenceMapper {

    LocationJpaEntity toNewEntity(Location location) {
        return new LocationJpaEntity(
                location.getId(),
                location.getWarehouseId(),
                location.getZoneId(),
                location.getLocationCode(),
                location.getAisle(),
                location.getRack(),
                location.getLevel(),
                location.getBin(),
                location.getLocationType(),
                location.getCapacityUnits(),
                location.getStatus(),
                location.getVersion(),
                location.getCreatedAt(),
                location.getCreatedBy(),
                location.getUpdatedAt(),
                location.getUpdatedBy());
    }

    /**
     * Insert path: emits an entity with {@code version=null} so Spring Data JPA
     * treats it as new and runs INSERT. See
     * {@link WarehousePersistenceMapper#toInsertEntity} for the full rationale.
     */
    LocationJpaEntity toInsertEntity(Location location) {
        return new LocationJpaEntity(
                location.getId(),
                location.getWarehouseId(),
                location.getZoneId(),
                location.getLocationCode(),
                location.getAisle(),
                location.getRack(),
                location.getLevel(),
                location.getBin(),
                location.getLocationType(),
                location.getCapacityUnits(),
                location.getStatus(),
                null,
                location.getCreatedAt(),
                location.getCreatedBy(),
                location.getUpdatedAt(),
                location.getUpdatedBy());
    }

    /**
     * Copy mutable fields from the domain aggregate onto a managed JPA entity so
     * Hibernate dirty-checking detects the change and bumps {@code @Version}.
     */
    void mergeMutable(Location source, LocationJpaEntity target) {
        target.applyMutableFields(
                source.getAisle(),
                source.getRack(),
                source.getLevel(),
                source.getBin(),
                source.getLocationType(),
                source.getCapacityUnits(),
                source.getStatus(),
                source.getUpdatedAt(),
                source.getUpdatedBy());
    }

    Location toDomain(LocationJpaEntity entity) {
        return Location.reconstitute(
                entity.getId(),
                entity.getWarehouseId(),
                entity.getZoneId(),
                entity.getLocationCode(),
                entity.getAisle(),
                entity.getRack(),
                entity.getLevel(),
                entity.getBin(),
                entity.getLocationType(),
                entity.getCapacityUnits(),
                entity.getStatus(),
                entity.getVersion() == null ? 0L : entity.getVersion(),
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy());
    }
}
