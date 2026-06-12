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

    // Cache keys are tenant-prefixed (T(TenantContext).currentTenant()) so a cache
    // hit can never serve one tenant's data to another — the cache is a read path
    // that must honour tenant isolation, not just the DB query (M2).
    @Transactional(readOnly = true)
    @Cacheable(
            value = "product-list",
            key = "T(com.example.product.domain.tenant.TenantContext).currentTenant() + ':' + T(java.util.Objects).toString(#categoryId, 'all') + ':' + T(java.util.Objects).toString(#status, 'all') + ':' + #page + ':' + #size"
    )
    public ProductListResult findAll(UUID categoryId, ProductStatus status, int page, int size) {
        return productQueryPort.findSummaries(categoryId, status, page, size);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "product-detail", key = "T(com.example.product.domain.tenant.TenantContext).currentTenant() + ':' + #productId")
    public ProductDetail findById(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return ProductDetail.from(product);
    }
}
