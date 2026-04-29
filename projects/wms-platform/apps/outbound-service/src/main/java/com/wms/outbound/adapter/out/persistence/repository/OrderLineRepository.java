package com.wms.outbound.adapter.out.persistence.repository;

import com.wms.outbound.adapter.out.persistence.entity.OrderLineEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderLineRepository extends JpaRepository<OrderLineEntity, UUID> {

    List<OrderLineEntity> findByOrderIdOrderByLineNumberAsc(UUID orderId);

    long countByOrderId(UUID orderId);

    void deleteByOrderId(UUID orderId);

    /**
     * Bulk aggregate row used by {@code OrderQueryService.list} so the list
     * endpoint runs O(1) queries irrespective of page size.
     *
     * <p>Returns rows of {@code (orderId, lineCount, totalQty)} for every
     * orderId in the input collection. Orders with no lines (legitimately
     * impossible for the Order aggregate, but possible at row level) are
     * absent from the result.
     */
    @Query("""
            SELECT l.orderId AS orderId, COUNT(l) AS lineCount, COALESCE(SUM(l.requestedQty), 0) AS totalQty
            FROM OrderLineEntity l
            WHERE l.orderId IN :orderIds
            GROUP BY l.orderId
            """)
    List<OrderLineSummaryRow> findLineSummariesByOrderIds(@Param("orderIds") Collection<UUID> orderIds);

    /** Projection record for {@link #findLineSummariesByOrderIds}. */
    interface OrderLineSummaryRow {
        UUID getOrderId();
        long getLineCount();
        long getTotalQty();
    }
}
