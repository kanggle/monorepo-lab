package com.wms.master.adapter.out.persistence;

import com.wms.master.domain.model.Lot;
import org.springframework.stereotype.Component;

/**
 * Domain ↔ JPA entity translation for Lot. Package-private — only the
 * persistence adapter uses it.
 *
 * <p>Mirrors the SKU / Warehouse mappers:
 * <ul>
 *   <li>{@link #toInsertEntity(Lot)} emits {@code version=null} so Hibernate
 *       runs INSERT rather than MERGE + optimistic-lock check.
 *   <li>{@link #toNewEntity(Lot)} is used on update flows so Hibernate's
 *       merge carries the caller's {@code @Version}.
 * </ul>
 */
@Component
class LotPersistenceMapper {

    LotJpaEntity toNewEntity(Lot lot) {
        return new LotJpaEntity(
                lot.getId(),
                lot.getSkuId(),
                lot.getLotNo(),
                lot.getManufacturedDate(),
                lot.getExpiryDate(),
                lot.getSupplierPartnerId(),
                lot.getStatus(),
                lot.getVersion(),
                lot.getCreatedAt(),
                lot.getCreatedBy(),
                lot.getUpdatedAt(),
                lot.getUpdatedBy());
    }

    /**
     * Insert path: emits an entity with {@code version=null} so Spring Data
     * JPA treats it as new and runs INSERT. A non-null {@code @Version} plus
     * a pre-assigned id triggers {@code detached entity passed to persist}.
     */
    LotJpaEntity toInsertEntity(Lot lot) {
        return new LotJpaEntity(
                lot.getId(),
                lot.getSkuId(),
                lot.getLotNo(),
                lot.getManufacturedDate(),
                lot.getExpiryDate(),
                lot.getSupplierPartnerId(),
                lot.getStatus(),
                null,
                lot.getCreatedAt(),
                lot.getCreatedBy(),
                lot.getUpdatedAt(),
                lot.getUpdatedBy());
    }

    Lot toDomain(LotJpaEntity entity) {
        return Lot.reconstitute(
                entity.getId(),
                entity.getSkuId(),
                entity.getLotNo(),
                entity.getManufacturedDate(),
                entity.getExpiryDate(),
                entity.getSupplierPartnerId(),
                entity.getStatus(),
                entity.getVersion() == null ? 0L : entity.getVersion(),
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy());
    }
}
