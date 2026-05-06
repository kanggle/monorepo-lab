package com.example.scmplatform.procurement.infrastructure.supplier;

import com.example.scmplatform.procurement.application.port.outbound.SupplierAdapterPort;
import com.example.scmplatform.procurement.domain.error.SupplierUnavailableException;
import com.example.scmplatform.procurement.domain.po.PurchaseOrder;
import com.example.scmplatform.procurement.domain.po.PurchaseOrderLine;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v1 mock REST {@link SupplierAdapterPort} implementation
 * (rules/traits/integration-heavy.md I7 — adapter layer separates vendor
 * SDK from domain). v2 will add EDI / SFTP siblings using the same port.
 *
 * <p>Resilience4j wraps the supplier call with:
 * <ul>
 *   <li>{@code @CircuitBreaker(name="supplier")} — OPEN on 50% failure rate
 *       across a 10-call sliding window (I2).</li>
 *   <li>{@code @Retry(name="supplier")} — 3 attempts, exponential + random
 *       jitter, 4xx ignored (I3).</li>
 *   <li>{@code @Bulkhead(name="supplier")} — 20 concurrent calls max (I9).</li>
 * </ul>
 *
 * <p>The {@code @CircuitBreaker fallbackMethod} translates Resilience4j /
 * transport failures into a domain
 * {@link SupplierUnavailableException} which the controller advice maps to
 * HTTP 503 {@code SUPPLIER_UNAVAILABLE} (Edge Case #7 — fail-CLOSED so the
 * PO does NOT advance to SUBMITTED).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestSupplierAdapter implements SupplierAdapterPort {

    private final SupplierApiClient client;
    private final IdempotencyKeyGenerator idempotencyKeyGenerator;

    @Override
    @CircuitBreaker(name = "supplier", fallbackMethod = "submitFallback")
    @Retry(name = "supplier")
    @Bulkhead(name = "supplier")
    public SupplierSubmissionResult submitPurchaseOrder(PurchaseOrder po, String idempotencyKey) {
        String key = idempotencyKeyGenerator.forSubmission(po.getId(), idempotencyKey);
        Map<String, Object> body = toSupplierPayload(po);
        Map<String, Object> response = client.postPurchaseOrder(key, body);
        Object receiptRef = response.get("receiptRef");
        Object status = response.get("status");
        return new SupplierSubmissionResult(
                receiptRef == null ? null : receiptRef.toString(),
                status == null ? "UNKNOWN" : status.toString()
        );
    }

    /**
     * Resilience4j fallback. Reachable when the circuit is OPEN
     * ({@link CallNotPermittedException}), retries are exhausted, or the
     * supplier returns a 5xx beyond the retry budget.
     */
    @SuppressWarnings("unused")
    public SupplierSubmissionResult submitFallback(PurchaseOrder po, String idempotencyKey, Throwable t) {
        log.warn("Supplier submitPurchaseOrder fallback for PO {} ({}: {})",
                po.getId(), t.getClass().getSimpleName(), t.getMessage());
        if (t instanceof CallNotPermittedException) {
            throw new SupplierUnavailableException(
                    "Supplier circuit OPEN — submission rejected", t);
        }
        throw new SupplierUnavailableException(
                "Supplier submission failed: " + t.getMessage(), t);
    }

    private static Map<String, Object> toSupplierPayload(PurchaseOrder po) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("poId", po.getId());
        body.put("poNumber", po.getPoNumber());
        body.put("supplierId", po.getSupplierId());
        body.put("currency", po.getTotalAmount().getCurrency());
        body.put("totalAmount", po.getTotalAmount().getAmount().toPlainString());
        List<Map<String, Object>> lines = new ArrayList<>();
        for (PurchaseOrderLine l : po.linesView()) {
            Map<String, Object> ml = new LinkedHashMap<>();
            ml.put("lineNo", l.getLineNo());
            ml.put("sku", l.getSku());
            ml.put("supplierSku", l.getSupplierSku());
            ml.put("quantity", l.getQuantity().toPlainString());
            ml.put("unitPrice", l.getUnitPrice().toPlainString());
            lines.add(ml);
        }
        body.put("lines", lines);
        return body;
    }
}
