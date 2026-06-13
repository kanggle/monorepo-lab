package com.example.settlement.application.service;

import com.example.settlement.application.exception.SellerScopeForbiddenException;
import com.example.settlement.domain.model.CommissionAccrual;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.settlement.domain.model.SellerBalance;
import com.example.settlement.domain.repository.CommissionAccrualRepository;
import com.example.settlement.domain.seller.SellerScopeContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operator-plane read use-cases over the accrual ledger. Tenant + seller scope are
 * applied at the repository chokepoint (isolate-then-attribute, AC-8) — this service
 * additionally rejects an explicit {@code sellerId} filter that falls outside the
 * caller's bound seller scope (404-over-403, M3).
 */
@Service
@RequiredArgsConstructor
public class SettlementQueryService {

    private final CommissionAccrualRepository accrualRepository;

    @Transactional(readOnly = true)
    public PageResult<CommissionAccrual> listAccruals(String sellerIdFilter, String orderIdFilter,
                                                      PageQuery pageQuery) {
        assertSellerWithinScope(sellerIdFilter);
        return accrualRepository.findAccruals(blankToNull(sellerIdFilter), blankToNull(orderIdFilter), pageQuery);
    }

    @Transactional(readOnly = true)
    public SellerBalance sellerBalance(String sellerId) {
        assertSellerWithinScope(sellerId);
        return accrualRepository.sellerBalance(sellerId);
    }

    /**
     * A restricted operator that asks for a different seller's rows gets a 404 (no
     * cross-seller existence disclosure, M3). An unrestricted operator ({@code '*'} /
     * absent) may target any seller within the tenant.
     */
    private void assertSellerWithinScope(String requestedSellerId) {
        if (requestedSellerId == null || requestedSellerId.isBlank()) {
            return;
        }
        if (SellerScopeContext.isRestricted()
                && !requestedSellerId.equals(SellerScopeContext.currentSellerScope())) {
            throw new SellerScopeForbiddenException(requestedSellerId);
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
