package com.wms.master.application.port.out;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.query.ListPartnersCriteria;
import com.wms.master.domain.model.Partner;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for Partner persistence. Implemented by an adapter in
 * {@code adapter.out.persistence}. Uses domain / shared types only — no JPA
 * or Spring Data types leak through.
 */
public interface PartnerPersistencePort {

    /**
     * Insert a newly-created Partner. Fails with:
     * <ul>
     *   <li>{@link com.wms.master.domain.exception.PartnerCodeDuplicateException}
     *       on duplicate {@code partner_code}
     * </ul>
     */
    Partner insert(Partner newPartner);

    /**
     * Apply mutable-field changes and current state to the existing row. Bumps
     * the optimistic-lock version. Version mismatch surfaces as
     * {@link org.springframework.orm.ObjectOptimisticLockingFailureException}
     * (translated at the application layer).
     */
    Partner update(Partner modified);

    Optional<Partner> findById(UUID id);

    Optional<Partner> findByCode(String partnerCode);

    boolean existsByCode(String partnerCode);

    PageResult<Partner> findPage(ListPartnersCriteria criteria, PageQuery pageQuery);
}
