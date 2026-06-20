package com.example.product.application.service;

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
}
