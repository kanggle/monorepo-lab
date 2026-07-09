package com.wms.inventory.application.port.out;

/**
 * Out-port for updating the low-stock <em>default</em> threshold at runtime.
 *
 * <p>TASK-BE-459 (ADR — Option B): the {@code AdminSettingsConsumer} calls this
 * when an {@code admin.settings.changed} event for
 * {@code inventory.low_stock.default_threshold_qty} (GLOBAL) arrives, so
 * operators can change the threshold live without a redeploy. The read side
 * ({@link LowStockThresholdPort#findThreshold}) and this write side are backed
 * by the <em>same</em> in-memory holder instance (see {@code AlertConfig}).
 *
 * <p><b>Bootstrap</b>: the initial value comes from the
 * {@code inventory.alert.low-stock.default-threshold} config property (not from
 * admin-service). Restart-durability of an operator-set value is intentionally
 * deferred — it would require a startup read over WMS service-to-service HTTP,
 * which does not exist yet (Option A).
 */
public interface LowStockThresholdWriterPort {

    /**
     * Set the global default low-stock threshold. {@code null} clears it
     * (low-stock detection then relies on per-row overrides only).
     */
    void updateDefaultThreshold(Integer threshold);
}
