package com.wms.outbound.application.service;

import java.util.UUID;

/**
 * Application event published from {@code ConfirmShippingService} after the
 * domain transaction commits, picked up by {@link ShipmentNotificationListener}
 * to fire the TMS push outside the saga TX.
 *
 * <p>Per {@code external-integrations.md} §2.10, the TMS call cannot be inside
 * the saga TX (T2 — no distributed TX). The post-commit event listener
 * pattern keeps the side-effect off the critical path while preserving the
 * "exactly one TMS push per shipment" guarantee via {@code tms_request_dedupe}.
 */
public record ShipmentNotifyTrigger(UUID sagaId, UUID shipmentId) {
}
