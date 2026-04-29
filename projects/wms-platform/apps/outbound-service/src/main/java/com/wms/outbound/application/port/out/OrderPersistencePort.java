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

    boolean existsByOrderNo(String orderNo);

    List<OrderSummaryResult> findSummaries(OrderQueryCommand query);

    long count(OrderQueryCommand query);
}
