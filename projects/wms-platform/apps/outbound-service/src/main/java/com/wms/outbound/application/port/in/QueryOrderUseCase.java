package com.wms.outbound.application.port.in;

import com.wms.outbound.application.command.OrderQueryCommand;
import com.wms.outbound.application.result.OrderResult;
import com.wms.outbound.application.result.OrderSummaryResult;
import java.util.List;
import java.util.UUID;

/**
 * In-port for the read-side order endpoints. {@code findById} returns the
 * full result; {@code list} returns paginated summaries with total count.
 */
public interface QueryOrderUseCase {

    OrderResult findById(UUID orderId);

    PageResult list(OrderQueryCommand command);

    record PageResult(List<OrderSummaryResult> items, long total) {}
}
