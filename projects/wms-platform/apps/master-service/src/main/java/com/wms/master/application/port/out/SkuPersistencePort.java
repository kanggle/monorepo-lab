package com.wms.master.application.port.out;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.query.ListSkusCriteria;
import com.wms.master.domain.model.Sku;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for SKU persistence. Implemented by an adapter in
 * {@code adapter.out.persistence}. Uses domain / shared types only — no JPA or
 * Spring Data types.
 *
 * <p>{@link #hasActiveLotsFor(UUID)} exists so the SKU deactivation path can
 * enforce the "no active Lots" invariant once Lots ship (TASK-BE-006). In v1
 * the implementation always returns {@code false} because Lots do not yet
 * exist — a documented, 1:1 seam, not speculative generality. Same shape as
 * {@code ZonePersistencePort.hasActiveLocationsFor} used before Locations
 * existed.
 */
public interface SkuPersistencePort {

    /**
     * Insert a newly-created SKU. Fails with:
     * <ul>
     *   <li>{@link com.wms.master.domain.exception.SkuCodeDuplicateException}
     *       on duplicate {@code sku_code} (case-insensitive because storage is
     *       always UPPERCASE)
     *   <li>{@link com.wms.master.domain.exception.BarcodeDuplicateException}
     *       on duplicate non-null {@code barcode}
     * </ul>
     */
    Sku insert(Sku newSku);

    /**
     * Apply mutable-field changes and the current state to the existing row.
     * Bumps the optimistic-lock version. Version mismatch surfaces as
     * {@link org.springframework.orm.ObjectOptimisticLockingFailureException}
     * (translated at the application layer).
     */
    Sku update(Sku modified);

    Optional<Sku> findById(UUID id);

    /**
     * Lookup by normalized (UPPERCASE) sku_code. Caller must uppercase the
     * input before invoking; the port takes the already-normalized form.
     */
    Optional<Sku> findBySkuCode(String skuCodeUpper);

    Optional<Sku> findByBarcode(String barcode);

    PageResult<Sku> findPage(ListSkusCriteria criteria, PageQuery pageQuery);

    /**
     * Returns whether this SKU currently has any ACTIVE child Lot. Stubbed to
     * {@code false} in v1 — Lots arrive in TASK-BE-006 and the real
     * implementation will live behind this same port.
     */
    boolean hasActiveLotsFor(UUID skuId);
}
