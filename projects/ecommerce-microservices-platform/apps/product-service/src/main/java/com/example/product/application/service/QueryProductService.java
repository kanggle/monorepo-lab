package com.example.product.application.service;

import com.example.common.summary.PeriodSummary;
import com.example.common.time.KstPeriodBounds;
import com.example.product.application.dto.ProductDetail;
import com.example.product.application.dto.ProductListResult;
import com.example.product.application.port.ProductQueryPort;
import com.example.product.domain.exception.ProductNotFoundException;
import com.example.product.domain.model.Product;
import com.example.product.domain.model.ProductStatus;
import com.example.product.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QueryProductService {

    private final ProductRepository productRepository;
    private final ProductQueryPort productQueryPort;

    // Cache keys are tenant-prefixed AND seller-scope-prefixed so a cache hit can
    // never serve one tenant's data to another (M2), nor serve a tenant-wide entry
    // to a seller-scoped read (or vice versa) — the cache is a read path that must
    // honour both isolation (tenant) and attribution (seller scope, AC-6). The
    // scope segment resolves to 'all' when unrestricted (net-zero, F1).
    @Transactional(readOnly = true)
    @Cacheable(
            value = "product-list",
            // The 'all' sentinel for #name is safe for categoryId/status (UUID/enum
            // can never equal "all"); for the free-text #name a literal ?name=all
            // shares the no-filter cache slot — a known minor limitation (low
            // probability, short cache TTL). A collision-proof key would need richer
            // SpEL that the per-PR CI does not exercise (the product-service
            // @Cacheable path runs only under the @Tag("integration") IT, nightly),
            // so it is deferred rather than shipped unvalidated.
            key = "T(com.example.product.domain.tenant.TenantContext).currentTenant() + ':' + T(java.util.Objects).toString(T(com.example.product.domain.seller.SellerScopeContext).currentSellerScope(), 'all') + ':' + T(java.util.Objects).toString(#categoryId, 'all') + ':' + T(java.util.Objects).toString(#status, 'all') + ':' + T(java.util.Objects).toString(#name, 'all') + ':' + #page + ':' + #size"
    )
    public ProductListResult findAll(UUID categoryId, ProductStatus status, String name, int page, int size) {
        return productQueryPort.findSummaries(categoryId, status, name, page, size);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "product-detail", key = "T(com.example.product.domain.tenant.TenantContext).currentTenant() + ':' + T(java.util.Objects).toString(T(com.example.product.domain.seller.SellerScopeContext).currentSellerScope(), 'all') + ':' + #productId")
    public ProductDetail findById(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return ProductDetail.from(product);
    }

    /**
     * Returns tenant-scoped KST calendar-period-to-date product counts for the
     * admin summary dashboard (TASK-BE-468, TASK-MONO-322).
     *
     * <p>Boundaries are computed in Asia/Seoul (KST) so that "today", "this week",
     * and "this month" align with the Korean business calendar rather than UTC.
     * All three period starts are inclusive; {@code now} is the exclusive upper bound.
     */
    @Transactional(readOnly = true)
    public PeriodSummary getPeriodSummary() {
        KstPeriodBounds b = KstPeriodBounds.now();

        long total = productRepository.countAllForTenant();
        long today = productRepository.countCreatedBetween(b.todayStartInstant(), b.nowInstant());
        long week  = productRepository.countCreatedBetween(b.weekStartInstant(), b.nowInstant());
        long month = productRepository.countCreatedBetween(b.monthStartInstant(), b.nowInstant());

        return new PeriodSummary(today, week, month, total);
    }
}
