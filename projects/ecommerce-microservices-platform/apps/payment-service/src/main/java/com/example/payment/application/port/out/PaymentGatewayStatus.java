package com.example.payment.application.port.out;

/**
 * Coarse PG-side payment state used by the stranded-refund double-refund guard (TASK-BE-438).
 *
 * <p>Maps the Toss Payments {@code GET /v1/payments/{paymentKey}} {@code status} field down to
 * the only distinction the sweeper needs: is the capture <b>already reversed</b> ({@code CANCELED}),
 * <b>still held</b> ({@code CAPTURED}), or <b>indeterminate</b> ({@code UNKNOWN})? The sweeper must
 * never re-issue a cancel against an already-{@code CANCELED} payment (double refund, F1), and must
 * never infer resolution from {@code UNKNOWN}.
 */
public enum PaymentGatewayStatus {
    /** The payment is cancelled at the PG — the original (transient-failed) cancel actually succeeded. */
    CANCELED,
    /** The payment is still captured/held at the PG ({@code DONE} / {@code PARTIAL_CANCELED}) — needs a cancel. */
    CAPTURED,
    /** Unparseable / unexpected status — treat as transient; never infer resolution. */
    UNKNOWN
}
