package com.example.product.application.service;

import com.example.product.application.dto.VariantDetail;
import com.example.product.domain.exception.ProductNotFoundException;
import com.example.product.domain.model.Price;
import com.example.product.domain.model.Product;
import com.example.product.domain.model.ProductVariant;
import com.example.product.domain.model.StockQuantity;
import com.example.product.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VariantManagementService {

    private final ProductRepository productRepository;

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "product-list", allEntries = true),
            @CacheEvict(value = "product-detail", key = "T(com.example.product.domain.tenant.TenantContext).currentTenant() + ':' + #productId")
    })
    public VariantDetail addVariant(UUID productId, String optionName, int stock, long additionalPrice) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        ProductVariant variant = ProductVariant.create(optionName, new StockQuantity(stock), new Price(additionalPrice));
        product.addVariant(variant);
        productRepository.save(product);

        log.info("Variant added: productId={}, variantId={}", productId, variant.getId());
        return VariantDetail.from(variant);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "product-list", allEntries = true),
            @CacheEvict(value = "product-detail", key = "T(com.example.product.domain.tenant.TenantContext).currentTenant() + ':' + #productId")
    })
    public VariantDetail updateVariant(UUID productId, UUID variantId, String optionName, long additionalPrice) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        ProductVariant updated = product.updateVariant(variantId, optionName, new Price(additionalPrice));
        productRepository.save(product);

        log.info("Variant updated: productId={}, variantId={}", productId, variantId);
        return VariantDetail.from(updated);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "product-list", allEntries = true),
            @CacheEvict(value = "product-detail", key = "T(com.example.product.domain.tenant.TenantContext).currentTenant() + ':' + #productId")
    })
    public void removeVariant(UUID productId, UUID variantId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        product.removeVariant(variantId);
        productRepository.save(product);

        log.info("Variant removed: productId={}, variantId={}", productId, variantId);
    }
}
