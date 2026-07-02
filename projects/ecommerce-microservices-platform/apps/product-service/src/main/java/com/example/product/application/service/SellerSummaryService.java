package com.example.product.application.service;

import com.example.product.application.dto.SellerPeriodSummary;
import com.example.product.application.util.KstPeriodBoundary;
import com.example.product.domain.repository.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads tenant-scoped KST calendar-period-to-date seller counts.
 * Tenant isolation is enforced in the repository adapter via
 * {@code TenantContext.currentTenant()} — identical to the existing
 * list/detail reads.
 */
@Service
@RequiredArgsConstructor
public class SellerSummaryService {

    private final SellerRepository sellerRepository;

    @Transactional(readOnly = true)
    public SellerPeriodSummary getSummary() {
        KstPeriodBoundary.Boundaries b = KstPeriodBoundary.now();
        long total = sellerRepository.countByTenant();
        long today = sellerRepository.countByTenantCreatedBetween(b.todayStart(), b.now());
        long week  = sellerRepository.countByTenantCreatedBetween(b.weekStart(),  b.now());
        long month = sellerRepository.countByTenantCreatedBetween(b.monthStart(), b.now());
        return new SellerPeriodSummary(today, week, month, total);
    }
}
