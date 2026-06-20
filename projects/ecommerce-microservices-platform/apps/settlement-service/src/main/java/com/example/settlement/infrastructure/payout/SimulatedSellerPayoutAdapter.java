package com.example.settlement.infrastructure.payout;

import com.example.settlement.application.port.SellerPayoutPort;
import com.example.settlement.domain.payout.SellerPayout;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Simulated seller-payout adapter (architecture.md § Period close + simulated payout).
 * Active when {@code settlement.payout.mode=simulated} (the default — {@code
 * matchIfMissing=true}).
 *
 * <p><b>No green-washing (erp {@code NoopExternalChannelAdapter} discipline).</b>
 * This adapter does NOT perform any real money movement. It generates a
 * <em>synthetic</em> reference with a {@code SIM-} prefix that makes simulation
 * self-evident at a glance. Every execution is logged with an explicit statement
 * that it is a simulation and that no actual disbursement occurred.
 *
 * <p>A REAL banking/PG adapter would be wired as:
 * <pre>
 *   &#64;ConditionalOnProperty(name = "settlement.payout.mode", havingValue = "bank")
 * </pre>
 * That seam is left <b>unimplemented</b> — a future increment will provide it once
 * seller bank-account management and the actual PG/bank API are in scope.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "settlement.payout.mode", havingValue = "simulated",
        matchIfMissing = true)
public class SimulatedSellerPayoutAdapter implements SellerPayoutPort {

    /**
     * Prefix that makes every simulated reference immediately self-evident.
     * Format: {@code SIM-{periodId}-{sellerId}-{uuid}}
     */
    private static final String SIM_PREFIX = "SIM-";

    /**
     * Execute a simulated payout. Generates a synthetic {@code SIM-} reference and
     * returns PAID. <b>No real disbursement occurs.</b>
     *
     * <p>The log entry at INFO level always contains the phrase "simulated payout
     * (NOT a real disbursement)" so that operators can immediately identify simulation
     * mode in production logs.
     */
    @Override
    public PayoutExecutionResult execute(SellerPayout payout) {
        String reference = SIM_PREFIX
                + payout.periodId() + "-"
                + payout.sellerId() + "-"
                + UUID.randomUUID();

        log.info("simulated payout (NOT a real disbursement): payoutId={} periodId={} "
                        + "sellerId={} tenantId={} payableNetMinor={} reference={}",
                payout.payoutId(), payout.periodId(), payout.sellerId(), payout.tenantId(),
                payout.payableNetMinor(), reference);

        return PayoutExecutionResult.paid(reference);
    }
}
