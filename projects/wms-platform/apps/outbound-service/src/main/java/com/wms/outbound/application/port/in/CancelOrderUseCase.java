package com.wms.outbound.application.port.in;

import com.wms.outbound.application.command.CancelOrderCommand;
import com.wms.outbound.application.result.OrderResult;

/**
 * In-port for the order-cancellation path. Cancels the order, requests saga
 * cancellation, and writes {@code outbound.order.cancelled} (+
 * {@code outbound.picking.cancelled} if reservation is active) to the outbox
 * in one TX. See AC-09.
 */
public interface CancelOrderUseCase {

    OrderResult cancel(CancelOrderCommand command);
}
