package com.wms.master.application.port.out;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.query.ListLotsCriteria;
import com.wms.master.domain.model.Lot;
import com.wms.master.domain.model.LotStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for Lot persistence. Implemented by an adapter in
 * {@code adapter.out.persistence}. Uses domain / shared types only — no JPA
 * or Spring Data types leak across the boundary.
 */
public interface LotPersistencePort {

    /**
     * Insert a newly-created Lot. On
     * {@code uq_lots_sku_lotno} collision raises
     * {@link com.wms.master.domain.exception.LotNoDuplicateException}.
     */
    Lot insert(Lot newLot);

    /**
     * Apply mutable-field changes and the current state to the existing row.
     * Version mismatch surfaces as
     * {@link org.springframework.orm.ObjectOptimisticLockingFailureException}.
     */
    Lot update(Lot modified);

    Optional<Lot> findById(UUID id);

    PageResult<Lot> findPage(ListLotsCriteria criteria, PageQuery pageQuery);

    /**
     * Scheduler query: all Lots in the given status whose {@code expiry_date}
     * is strictly before {@code cutoff} (and whose {@code expiry_date} is not
     * null — null = permanent). Drives the daily expiration batch.
     */
    List<Lot> findAllByStatusAndExpiryDateBefore(LotStatus status, LocalDate cutoff);
}
