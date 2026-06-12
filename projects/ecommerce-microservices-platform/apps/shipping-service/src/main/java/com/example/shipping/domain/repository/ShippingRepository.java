package com.example.shipping.domain.repository;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.shipping.domain.model.Shipping;
import com.example.shipping.domain.model.ShippingStatus;

import java.util.List;
import java.util.Optional;

public interface ShippingRepository {

    Shipping save(Shipping shipping);

    Optional<Shipping> findById(String shippingId);

    Optional<Shipping> findByOrderId(String orderId);

    boolean existsByOrderId(String orderId);

    PageResult<Shipping> findAll(PageQuery pageQuery);

    PageResult<Shipping> findByStatus(ShippingStatus status, PageQuery pageQuery);

    /**
     * In-flight shipments eligible for the unattended auto-collect tracking sweep
     * (TASK-BE-360): status is {@code SHIPPED} or {@code IN_TRANSIT} (en route — neither
     * not-yet-shipped {@code PREPARING} nor terminal {@code DELIVERED}) AND both a
     * tracking number and a carrier are present (so the carrier port can be queried).
     * Ordered oldest-updated first and capped at {@code limit} so a single tick processes a
     * bounded batch; overflow carries to the next tick (no unbounded loop — ADR-007 / spec
     * Edge Cases).
     */
    List<Shipping> findInFlightWithTracking(int limit);
}
