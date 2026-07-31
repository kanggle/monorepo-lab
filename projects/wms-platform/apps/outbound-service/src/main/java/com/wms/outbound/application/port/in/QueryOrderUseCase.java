package com.wms.outbound.application.port.in;

import com.example.common.page.PageResult;
import com.wms.outbound.application.command.OrderQueryCommand;
import com.wms.outbound.application.result.OrderResult;
import com.wms.outbound.application.result.OrderSummaryResult;
import java.util.UUID;

/**
 * In-port for the read-side order endpoints. {@code findById} returns the
 * full result; {@code list} returns paginated summaries with the shared
 * {@link PageResult} carrier (per {@code outbound-service-api.md} §Pagination).
 */
public interface QueryOrderUseCase {

    OrderResult findById(UUID orderId);

    PageResult<OrderSummaryResult> list(OrderQueryCommand command);
}
