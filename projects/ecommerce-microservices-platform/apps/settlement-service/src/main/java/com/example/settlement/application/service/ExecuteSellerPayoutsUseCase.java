package com.example.settlement.application.service;

import com.example.settlement.application.exception.SellerScopeForbiddenException;
import com.example.settlement.application.port.SellerPayoutPort;
import com.example.settlement.application.port.SellerPayoutPort.PayoutExecutionResult;
import com.example.settlement.application.port.SettlementMetricsPort;
import com.example.settlement.application.view.PayoutView;
import com.example.settlement.domain.payout.PayoutStatus;
import com.example.settlement.domain.payout.SellerPayout;
import com.example.settlement.domain.period.PeriodNotClosedException;
import com.example.settlement.domain.period.PeriodNotFoundException;
import com.example.settlement.domain.period.SettlementPeriod;
import com.example.settlement.domain.repository.SellerPayoutRepository;
import com.example.settlement.domain.repository.SettlementPeriodRepository;
import com.example.settlement.domain.seller.SellerScopeContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Executes the simulated payout for a closed settlement period (architecture.md §
 * Period close + simulated payout — TASK-BE-416 AC-2/AC-3/AC-4/AC-6).
 *
 * <p><b>Transaction boundary.</b> One {@code @Transactional} scope: load period
 * (tenant-scoped, 404), require CLOSED (OPEN → 409 {@code PERIOD_NOT_CLOSED}), fetch
 * its PENDING payout rows, apply the {@link SellerPayoutPort} to each, flip
 * {@code PENDING→PAID|FAILED}, save, increment
 * {@code settlement_payout_total{status}} per resolved row. If anything throws, the
 * whole transaction rolls back (no partial state).
 *
 * <p><b>Idempotency (AC-3).</b> Already-PAID or FAILED rows are silently skipped —
 * a re-run processes only the remaining PENDING rows.
 * {@code (period_id, seller_id) UNIQUE} at the DB layer prevents duplicate payout
 * rows from the close step, so the execute step can never double-pay.
 *
 * <p><b>Seller-scope read (AC-5).</b> The list path applies the same
 * {@link SellerScopeContext} ABAC filter as the accrual reads — inside the
 * tenant filter (isolate-then-attribute). The execute path loads all PENDING rows
 * (no seller-scope restriction on execution — an operator executes on behalf of the
 * whole period).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecuteSellerPayoutsUseCase {

    private final SettlementPeriodRepository periodRepository;
    private final SellerPayoutRepository payoutRepository;
    private final SellerPayoutPort payoutPort;
    private final SettlementMetricsPort metrics;
    private final Clock clock;

    /**
     * Execute all PENDING payouts for the given closed period, returning the
     * post-execution state of all payout rows (seller-scope filtered — same ABAC
     * as the accrual read path).
     *
     * @param periodId the period whose PENDING payouts are executed
     * @param tenantId the caller's tenant — cross-tenant period → 404
     * @return all payout rows for the period after execution (seller-scope applied)
     * @throws PeriodNotFoundException  if the period does not exist in the caller's tenant (404)
     * @throws PeriodNotClosedException if the period is still OPEN (409)
     */
    @Transactional
    public List<PayoutView> execute(String periodId, String tenantId) {
        // 1. Load period — tenant-scoped; absent or cross-tenant → 404 (M3).
        SettlementPeriod period = periodRepository.findById(periodId, tenantId)
                .orElseThrow(() -> new PeriodNotFoundException(
                        "settlement period not found: " + periodId));

        // 2. Require CLOSED — OPEN → 409 PERIOD_NOT_CLOSED (AC-4).
        if (!period.isClosed()) {
            throw new PeriodNotClosedException(
                    "period is not CLOSED — execute requires a closed period: " + periodId);
        }

        // 3. Fetch all payout rows for this period (tenant-scoped at the repo level).
        List<SellerPayout> allPayouts = payoutRepository.findByPeriodAndTenant(periodId, tenantId);

        // 4. Process PENDING rows; skip already-PAID/FAILED (idempotency, AC-3).
        List<SellerPayout> processed = new ArrayList<>();
        Instant now = clock.instant();
        for (SellerPayout payout : allPayouts) {
            if (payout.status() != PayoutStatus.PENDING) {
                // Idempotent re-run: skip already-resolved rows (AC-3).
                continue;
            }

            PayoutExecutionResult result = payoutPort.execute(payout);

            if (result.isPaid()) {
                payout.markPaid(result.reference(), now);
                metrics.recordPayoutExecuted("PAID");
                log.info("payout PAID: payoutId={} periodId={} sellerId={} reference={}",
                        payout.payoutId(), periodId, payout.sellerId(), result.reference());
            } else {
                payout.markFailed();
                metrics.recordPayoutExecuted("FAILED");
                log.warn("payout FAILED: payoutId={} periodId={} sellerId={} reason={}",
                        payout.payoutId(), periodId, payout.sellerId(), result.reason());
            }

            payoutRepository.save(payout);
            processed.add(payout);
        }

        log.info("executed payouts for periodId={} tenant={} processed={} total={}",
                periodId, tenantId, processed.size(), allPayouts.size());

        // 5. Return all payout rows (post-execution) with seller-scope ABAC applied.
        return toViews(allPayouts);
    }

    /**
     * List payout rows for a period with seller-scope ABAC (AC-5) — same filter as
     * the accrual read path. Throws 404 for cross-tenant / absent period; does NOT
     * check CLOSED (payouts may be listed on an OPEN period too — the list is
     * read-only).
     *
     * @param periodId the period to query
     * @param tenantId the caller's tenant — cross-tenant → 404
     * @return payout rows visible to the caller (seller-scope filtered)
     * @throws PeriodNotFoundException if the period does not exist in the caller's tenant
     */
    @Transactional(readOnly = true)
    public List<PayoutView> list(String periodId, String tenantId) {
        // Verify the period belongs to this tenant (M3).
        periodRepository.findById(periodId, tenantId)
                .orElseThrow(() -> new PeriodNotFoundException(
                        "settlement period not found: " + periodId));

        List<SellerPayout> payouts = payoutRepository.findByPeriodAndTenant(periodId, tenantId);

        // Apply seller-scope ABAC — inside the tenant filter (isolate-then-attribute).
        if (SellerScopeContext.isRestricted()) {
            String scopedSeller = SellerScopeContext.currentSellerScope();
            boolean anyMatch = payouts.stream()
                    .anyMatch(p -> p.sellerId().equals(scopedSeller));
            if (!anyMatch && !payouts.isEmpty()) {
                // Cross-seller: return empty list rather than 404 for the list endpoint
                // (the list shows 0 rows for the scoped seller; 404 is reserved for the
                // period-not-found case which is already guarded above).
                return List.of();
            }
            payouts = payouts.stream()
                    .filter(p -> p.sellerId().equals(scopedSeller))
                    .toList();
        }

        return toViews(payouts);
    }

    private static List<PayoutView> toViews(List<SellerPayout> payouts) {
        return payouts.stream().map(PayoutView::from).toList();
    }
}
