package com.wms.master.adapter.out.persistence;

import com.wms.master.domain.model.Partner;
import org.springframework.stereotype.Component;

/**
 * Domain ↔ JPA entity translation for Partner. Package-private — only the
 * persistence adapter uses it.
 *
 * <p>Mirrors {@code SkuPersistenceMapper}:
 * <ul>
 *   <li>{@link #toInsertEntity(Partner)} emits {@code version=null} so Hibernate
 *       runs INSERT rather than MERGE + optimistic-lock check.
 *   <li>{@link #toNewEntity(Partner)} is used for update() flows so Hibernate's
 *       merge carries the caller's {@code @Version}.
 * </ul>
 */
@Component
class PartnerPersistenceMapper {

    PartnerJpaEntity toNewEntity(Partner partner) {
        return new PartnerJpaEntity(
                partner.getId(),
                partner.getPartnerCode(),
                partner.getName(),
                partner.getPartnerType(),
                partner.getBusinessNumber(),
                partner.getContactName(),
                partner.getContactEmail(),
                partner.getContactPhone(),
                partner.getAddress(),
                partner.getStatus(),
                partner.getVersion(),
                partner.getCreatedAt(),
                partner.getCreatedBy(),
                partner.getUpdatedAt(),
                partner.getUpdatedBy());
    }

    /**
     * Insert path: emits an entity with {@code version=null} so Spring Data
     * JPA treats it as new and runs INSERT. A non-null {@code @Version} with
     * a pre-assigned id triggers {@code detached entity passed to persist}.
     */
    PartnerJpaEntity toInsertEntity(Partner partner) {
        return new PartnerJpaEntity(
                partner.getId(),
                partner.getPartnerCode(),
                partner.getName(),
                partner.getPartnerType(),
                partner.getBusinessNumber(),
                partner.getContactName(),
                partner.getContactEmail(),
                partner.getContactPhone(),
                partner.getAddress(),
                partner.getStatus(),
                null,
                partner.getCreatedAt(),
                partner.getCreatedBy(),
                partner.getUpdatedAt(),
                partner.getUpdatedBy());
    }

    Partner toDomain(PartnerJpaEntity entity) {
        return Partner.reconstitute(
                entity.getId(),
                entity.getPartnerCode(),
                entity.getName(),
                entity.getPartnerType(),
                entity.getBusinessNumber(),
                entity.getContactName(),
                entity.getContactEmail(),
                entity.getContactPhone(),
                entity.getAddress(),
                entity.getStatus(),
                entity.getVersion() == null ? 0L : entity.getVersion(),
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy());
    }
}
