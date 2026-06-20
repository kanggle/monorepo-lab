package com.example.settlement.application.port;

import com.example.settlement.domain.payout.SellerPayout;

/**
 * Outbound application port for seller-payout execution (architecture.md §
 * Period close + simulated payout — SellerPayoutPort). The only adapter in this
 * increment is a SIMULATED one ({@code settlement.payout.mode=simulated,
 * matchIfMissing=true}). A REAL banking/PG adapter ({@code =bank}) is a
 * forward-declared seam — left unimplemented until a later increment.
 *
 * <p>The port abstracts "execute a payout for this {@code seller_payout} row"
 * and returns a typed outcome:
 * <ul>
 *   <li>{@link PayoutExecutionResult#paid(String)} — payout succeeded; the
 *       {@code payoutReference} must be stored on the aggregate.</li>
 *   <li>{@link PayoutExecutionResult#failed(String)} — payout failed; a reason
 *       is captured for diagnostics.</li>
 * </ul>
 *
 * <p><b>No green-washing.</b> Adapters must never claim a real money movement
 * occurred unless an actual external disbursement API was called successfully.
 * The simulated adapter is required to log + prefix its reference so that
 * simulation is immediately self-evident (erp {@code NoopExternalChannelAdapter}
 * discipline).
 */
public interface SellerPayoutPort {

    /**
     * Execute the payout for one {@link SellerPayout} row.
     *
     * @param payout the PENDING payout to execute (callers must ensure PENDING status)
     * @return the execution outcome — PAID with a reference, or FAILED with a reason
     */
    PayoutExecutionResult execute(SellerPayout payout);

    /**
     * The typed outcome of a single payout execution.
     *
     * @param status    the outcome status: {@code PAID} or {@code FAILED}
     * @param reference the disbursement reference when {@code PAID} (non-null); {@code null} for
     *                  {@code FAILED}
     * @param reason    human-readable failure reason for {@code FAILED}; {@code null} for
     *                  {@code PAID}
     */
    record PayoutExecutionResult(OutcomeStatus status, String reference, String reason) {

        /** Successful payout outcome with a non-null disbursement reference. */
        public static PayoutExecutionResult paid(String reference) {
            if (reference == null || reference.isBlank()) {
                throw new IllegalArgumentException("payout reference must not be blank for PAID outcome");
            }
            return new PayoutExecutionResult(OutcomeStatus.PAID, reference, null);
        }

        /** Failed payout outcome with a diagnostic reason. */
        public static PayoutExecutionResult failed(String reason) {
            return new PayoutExecutionResult(OutcomeStatus.FAILED, null, reason);
        }

        public boolean isPaid() {
            return status == OutcomeStatus.PAID;
        }

        public enum OutcomeStatus {
            PAID, FAILED
        }
    }
}
