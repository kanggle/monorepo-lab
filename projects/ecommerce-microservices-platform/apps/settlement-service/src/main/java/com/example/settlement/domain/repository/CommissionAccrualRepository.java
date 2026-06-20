package com.example.settlement.domain.repository;

import com.example.settlement.domain.model.CommissionAccrual;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.settlement.domain.model.SellerBalance;

import java.time.Instant;
import java.util.List;

/**
 * Persistence port for the append-only commission-accrual ledger. The read methods
 * apply tenant scoping (and, when bound, the nested net-zero seller-scope filter) at
 * the repository chokepoint — isolate-then-attribute (AC-7/AC-8). The write methods
 * are insert-only (F3 immutability).
 */
public interface CommissionAccrualRepository {

    /** Appends accrual/reversal rows (insert-only — never updates). */
    void appendAll(List<CommissionAccrual> accruals);

    /**
     * {@code true} when an ACCRUAL already exists for {@code (orderId, paymentId)} —
     * the secondary idempotency guard against double-accrual (AC-6).
     */
    boolean existsAccrualFor(String orderId, String paymentId);

    /**
     * {@code true} when a REVERSAL already exists for {@code (orderId, refundPaymentId)} —
     * guards double-reversal (AC-6).
     */
    boolean existsReversalFor(String orderId, String refundPaymentId);

    /** Loads the ACCRUAL rows of an order (to negate them on refund). */
    List<CommissionAccrual> findAccrualsByOrderId(String orderId);

    /**
     * Operator-plane accrual listing for the current tenant + (optional) seller
     * scope, most recent first. {@code sellerIdFilter} / {@code orderIdFilter} are
     * optional ({@code null} = no extra filter).
     */
    PageResult<CommissionAccrual> findAccruals(String sellerIdFilter, String orderIdFilter,
                                               PageQuery pageQuery);

    /** Aggregated balance for one seller in the current tenant + seller scope. */
    SellerBalance sellerBalance(String sellerId);

    /**
     * Period-close aggregation (architecture.md § Close-time aggregation): folds the
     * EXISTING accrual rows whose {@code occurred_at ∈ [from, to)} (half-open,
     * tenant-scoped) per seller into a {@link SellerAccrualFold}. <b>Read-only</b> —
     * the accrual ledger is never mutated (F3). Net-zero skip (decision 7) is applied
     * by the close use-case, not here (this returns every seller's raw fold, including
     * {@code payableNetMinor ≤ 0}). Tenant is passed explicitly (close runs in the
     * operator's tenant context).
     */
    List<SellerAccrualFold> foldByPeriod(String tenantId, Instant from, Instant to);
}
