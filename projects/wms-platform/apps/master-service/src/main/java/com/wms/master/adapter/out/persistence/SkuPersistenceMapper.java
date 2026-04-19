package com.wms.master.adapter.out.persistence;

import com.wms.master.domain.model.Sku;
import org.springframework.stereotype.Component;

/**
 * Domain ↔ JPA entity translation for SKU. Package-private — only the
 * persistence adapter uses it.
 *
 * <p>Mirrors {@code WarehousePersistenceMapper} / {@code ZonePersistenceMapper}:
 * <ul>
 *   <li>{@link #toInsertEntity(Sku)} emits {@code version=null} so Hibernate
 *       runs INSERT rather than MERGE + optimistic-lock check (critical fix
 *       from TASK-BE-001).
 *   <li>{@link #toNewEntity(Sku)} is used for update() flows so Hibernate's
 *       merge carries the caller's {@code @Version}.
 *   <li>{@link #mergeMutable(Sku, SkuJpaEntity)} copies mutable fields onto a
 *       managed entity so dirty-checking bumps {@code @Version}.
 * </ul>
 */
@Component
class SkuPersistenceMapper {

    SkuJpaEntity toNewEntity(Sku sku) {
        return new SkuJpaEntity(
                sku.getId(),
                sku.getSkuCode(),
                sku.getName(),
                sku.getDescription(),
                sku.getBarcode(),
                sku.getBaseUom(),
                sku.getTrackingType(),
                sku.getWeightGrams(),
                sku.getVolumeMl(),
                sku.getHazardClass(),
                sku.getShelfLifeDays(),
                sku.getStatus(),
                sku.getVersion(),
                sku.getCreatedAt(),
                sku.getCreatedBy(),
                sku.getUpdatedAt(),
                sku.getUpdatedBy());
    }

    /**
     * Insert path: emits an entity with {@code version=null} so Spring Data
     * JPA treats it as new and runs INSERT. A non-null {@code @Version} with a
     * pre-assigned id triggers {@code detached entity passed to persist}.
     */
    SkuJpaEntity toInsertEntity(Sku sku) {
        return new SkuJpaEntity(
                sku.getId(),
                sku.getSkuCode(),
                sku.getName(),
                sku.getDescription(),
                sku.getBarcode(),
                sku.getBaseUom(),
                sku.getTrackingType(),
                sku.getWeightGrams(),
                sku.getVolumeMl(),
                sku.getHazardClass(),
                sku.getShelfLifeDays(),
                sku.getStatus(),
                null,
                sku.getCreatedAt(),
                sku.getCreatedBy(),
                sku.getUpdatedAt(),
                sku.getUpdatedBy());
    }

    /**
     * Copy mutable fields from the domain aggregate onto a managed JPA entity
     * so Hibernate dirty-checking detects the change and bumps {@code @Version}.
     */
    void mergeMutable(Sku source, SkuJpaEntity target) {
        target.applyMutableFields(
                source.getName(),
                source.getDescription(),
                source.getBarcode(),
                source.getWeightGrams(),
                source.getVolumeMl(),
                source.getHazardClass(),
                source.getShelfLifeDays(),
                source.getStatus(),
                source.getUpdatedAt(),
                source.getUpdatedBy());
    }

    Sku toDomain(SkuJpaEntity entity) {
        return Sku.reconstitute(
                entity.getId(),
                entity.getSkuCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getBarcode(),
                entity.getBaseUom(),
                entity.getTrackingType(),
                entity.getWeightGrams(),
                entity.getVolumeMl(),
                entity.getHazardClass(),
                entity.getShelfLifeDays(),
                entity.getStatus(),
                entity.getVersion() == null ? 0L : entity.getVersion(),
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy());
    }
}
