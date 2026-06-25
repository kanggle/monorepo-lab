package com.wms.outbound.application.port.out;

import com.wms.outbound.application.command.OrderQueryCommand;
import com.wms.outbound.application.result.OrderSummaryResult;
import com.wms.outbound.domain.model.Order;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Out-port for {@link Order} aggregate persistence.
 *
 * <p>The query method intentionally returns {@link OrderSummaryResult}
 * directly — projecting at the persistence layer keeps the read path
 * efficient and avoids loading every line for list responses.
 */
public interface OrderPersistencePort {

    Order save(Order order);

    Optional<Order> findById(UUID id);

    /**
     * Resolves an {@link Order} by its business {@code orderNo} (the
     * cross-service correlation key, D5). Used by the manual ship-confirm
     * consumer (TASK-MONO-305 / ADR-MONO-022 D4 v2(c)) to map an inbound
     * ecommerce {@code orderNo} back to the wms outbound order. A miss is a
     * legitimate no-op (the order never routed through wms).
     */
    Optional<Order> findByOrderNo(String orderNo);

    boolean existsByOrderNo(String orderNo);

    List<OrderSummaryResult> findSummaries(OrderQueryCommand query);

    long count(OrderQueryCommand query);
}
