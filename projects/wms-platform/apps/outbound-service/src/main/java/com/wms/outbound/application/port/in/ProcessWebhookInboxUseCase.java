package com.wms.outbound.application.port.in;

/**
 * In-port for the ERP order webhook inbox processor.
 *
 * <p>Drains a bounded batch of {@code PENDING} rows from
 * {@code erp_order_webhook_inbox} and applies the domain ingest logic.
 *
 * <p><b>TASK-BE-034 stub:</b> the implementation flips rows to
 * {@code APPLIED} without creating an Order. The full
 * {@code ReceiveOrderUseCase} integration lands in TASK-BE-035.
 */
public interface ProcessWebhookInboxUseCase {

    /**
     * Process up to {@code batchSize} pending inbox rows in a single TX.
     *
     * @return number of rows transitioned in this batch
     */
    int processNextBatch();
}
