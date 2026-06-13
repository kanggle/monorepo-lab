package com.example.settlement.application.service;

import com.example.settlement.application.exception.SellerScopeForbiddenException;
import com.example.settlement.domain.model.CommissionRate;
import com.example.settlement.domain.model.InvalidCommissionRateException;
import com.example.settlement.domain.repository.CommissionRateRepository;
import com.example.settlement.domain.seller.SellerScopeContext;
import com.example.settlement.domain.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operator-plane commission-rate admin: read the effective rate (override-or-default)
 * and set a per-seller override. Setting a rate is <b>prospective</b> — it never
 * rewrites booked accruals (F3 / AC-4). A restricted operator may only read/set
 * their own seller's rate (M3).
 */
@Service
@RequiredArgsConstructor
public class CommissionRateAdminService {

    private final CommissionRateRepository rateRepository;
    private final CommissionRateResolver rateResolver;

    @Transactional(readOnly = true)
    public CommissionRate getEffectiveRate(String sellerId) {
        assertSellerWithinScope(sellerId);
        return rateResolver.resolve(TenantContext.currentTenant(), sellerId);
    }

    /**
     * Sets the per-seller override. {@code rateBps ∉ [0, 10000]} →
     * {@link InvalidCommissionRateException} (422). Returns the stored rate
     * ({@code source = SELLER_OVERRIDE}).
     */
    @Transactional
    public CommissionRate setRate(String sellerId, int rateBps) {
        assertSellerWithinScope(sellerId);
        if (!CommissionRate.isValidBps(rateBps)) {
            throw new InvalidCommissionRateException(rateBps);
        }
        rateRepository.upsert(TenantContext.currentTenant(), sellerId, rateBps);
        return CommissionRate.sellerOverride(rateBps);
    }

    private void assertSellerWithinScope(String sellerId) {
        if (SellerScopeContext.isRestricted()
                && !sellerId.equals(SellerScopeContext.currentSellerScope())) {
            throw new SellerScopeForbiddenException(sellerId);
        }
    }
}
