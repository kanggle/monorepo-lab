package com.example.libs.payment;

/**
 * Coarse PG-side payment state used by a stranded-refund double-refund guard.
 *
 * <p>Reduces a vendor's payment-status field to the only distinction a refund sweeper needs:
 * is the capture <b>already reversed</b> ({@link #CANCELED}), <b>still held</b>
 * ({@link #CAPTURED}), or <b>indeterminate</b> ({@link #UNKNOWN})? A sweeper must never
 * re-issue a reversal against an already-{@code CANCELED} payment (double refund), and must
 * never infer resolution from {@code UNKNOWN}.
 */
public enum PaymentGatewayStatus {
    /** The payment is cancelled at the PG — a prior (transient-failed) cancel actually succeeded. */
    CANCELED,
    /** The payment is still captured/held at the PG — needs a cancel. */
    CAPTURED,
    /** Unparseable / unexpected status — treat as transient; never infer resolution. */
    UNKNOWN
}
