package com.example.scmplatform.procurement.application.port.outbound;

import com.example.scmplatform.procurement.domain.po.PurchaseOrder;

/**
 * Outbound port for external supplier integration (rules/traits/integration-heavy.md
 * I7 — vendor SDK never leaks into application / domain). v1 has a single
 * mock REST adapter implementation; v2 will add EDI / SFTP adapters with the
 * same port.
 *
 * <p>Adapter implementations MUST honor:
 * <ul>
 *   <li>Idempotency-Key (S2): every outbound supplier call carries a stable
 *       key derived from the PO id + attempt so the supplier dedupes.</li>
 *   <li>Resilience4j circuit breaker / retry / bulkhead — wrapped around the
 *       HTTP call inside the adapter, NOT in the application layer.</li>
 *   <li>Translation to internal domain — never returns vendor-shaped DTOs to
 *       callers (I8).</li>
 * </ul>
 */
public interface SupplierAdapterPort {

    /**
     * Send a PO to the supplier system. Returns the supplier's reception
     * reference (acknowledgement may arrive later via webhook).
     *
     * @throws com.example.scmplatform.procurement.domain.error.SupplierUnavailableException
     *         when the circuit breaker is OPEN or retries are exhausted.
     */
    SupplierSubmissionResult submitPurchaseOrder(PurchaseOrder po, String idempotencyKey);

    /** Result envelope translated from the supplier response. */
    record SupplierSubmissionResult(String supplierReceiptRef, String status) {
    }
}
