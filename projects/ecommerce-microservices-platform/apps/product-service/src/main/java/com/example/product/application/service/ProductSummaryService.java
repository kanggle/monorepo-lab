package com.example.product.application.service;

import com.example.product.application.dto.ProductPeriodSummary;
import com.example.product.application.util.KstPeriodBoundary;
import com.example.product.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads tenant-scoped KST calendar-period-to-date product counts.
 * Tenant isolation is enforced in the repository adapter via
 * {@code TenantContext.currentTenant()} — identical to the existing
 * list/detail reads.
 */
@Service
@RequiredArgsConstructor
public class ProductSummaryService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public ProductPeriodSummary getSummary() {
        KstPeriodBoundary.Boundaries b = KstPeriodBoundary.now();
        long total = productRepository.countByTenant();
        long today = productRepository.countByTenantCreatedBetween(b.todayStart(), b.now());
        long week  = productRepository.countByTenantCreatedBetween(b.weekStart(),  b.now());
        long month = productRepository.countByTenantCreatedBetween(b.monthStart(), b.now());
        return new ProductPeriodSummary(today, week, month, total);
    }
}
