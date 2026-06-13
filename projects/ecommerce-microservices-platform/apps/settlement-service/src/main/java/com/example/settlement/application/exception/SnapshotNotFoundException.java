package com.example.settlement.application.exception;

/**
 * Thrown when a {@code PaymentCompleted} (or {@code PaymentRefunded}) arrives but no
 * {@code OrderPlaced} snapshot exists for its {@code orderId} (F2 — out-of-order /
 * lost placement). The accrual cannot be attributed (no {@code seller_id} /
 * {@code tenant_id}), so the consumer raises and the event is retried → DLQ.
 * v1 assumes placement precedes capture; a buffering mechanism is forward-declared.
 */
public class SnapshotNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SnapshotNotFoundException(String orderId) {
        super("No OrderPlaced snapshot for orderId=" + orderId
                + " — payment cannot be attributed (out-of-order/lost placement, F2)");
    }
}
