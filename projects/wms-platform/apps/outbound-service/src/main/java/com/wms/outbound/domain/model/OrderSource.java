package com.wms.outbound.domain.model;

/**
 * Origin of an outbound {@link Order}.
 *
 * <p>Authoritative reference:
 * {@code specs/services/outbound-service/domain-model.md} §1.
 *
 * <ul>
 *   <li>{@link #MANUAL} — operator-driven REST entry
 *       ({@code POST /api/v1/outbound/orders}).</li>
 *   <li>{@link #WEBHOOK_ERP} — pushed by the ERP via
 *       {@code POST /webhooks/erp/order} and applied by the inbox processor.</li>
 * </ul>
 */
public enum OrderSource {
    MANUAL,
    WEBHOOK_ERP
}
