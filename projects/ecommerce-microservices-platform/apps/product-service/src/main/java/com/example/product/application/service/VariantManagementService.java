package com.example.product.application.service;

import com.example.product.application.dto.VariantDetail;
import com.example.product.domain.exception.DuplicateVariantOptionException;
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
import org.springframework.dao.DataIntegrityViolationException;
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
            // allEntries: the read key carries a seller-scope segment a targeted
            // evict cannot reliably match (TASK-BE-436).
            @CacheEvict(value = "product-detail", allEntries = true)
    })
    public VariantDetail addVariant(UUID productId, String optionName, int stock, long additionalPrice) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        ProductVariant variant = ProductVariant.create(optionName, new StockQuantity(stock), new Price(additionalPrice));
        product.addVariant(variant);

        // TASK-BE-536 AC-0 re-measure finding: `uq_product_variants_option UNIQUE
        // (product_id, option_name)` already exists (Flyway V5, pre-dates this task) —
        // so no new migration is added here. What was missing is the WIRING: a plain
        // save() defers the child INSERT to commit-time flush (past this try/catch,
        // past the controller), so a duplicate optionName previously escaped as a raw
        // 500 INTERNAL_ERROR instead of a clean 409. saveAndFlush forces the INSERT
        // synchronously so the violation is catchable here (same shape as
        // RefundRequestRepositoryImpl.insert, TASK-BE-535). The constraint itself is
        // also the arbiter for BOTH the sequential and the concurrent duplicate here:
        // Product.addVariant performs no optionName check at all (it only rejects null),
        // so every duplicate — not just a concurrent one — is detected by the INSERT.
        // Corrected in TASK-BE-541: the original comment claimed an "in-memory
        // addVariant() check" that does not exist.
        try {
            productRepository.saveAndFlush(product);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateVariantOptionException(productId, optionName, e);
        }

        log.info("Variant added: productId={}, variantId={}", productId, variant.getId());
        return VariantDetail.from(variant);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "product-list", allEntries = true),
            // allEntries: the read key carries a seller-scope segment a targeted
            // evict cannot reliably match (TASK-BE-436).
            @CacheEvict(value = "product-detail", allEntries = true)
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
            // allEntries: the read key carries a seller-scope segment a targeted
            // evict cannot reliably match (TASK-BE-436).
            @CacheEvict(value = "product-detail", allEntries = true)
    })
    public void removeVariant(UUID productId, UUID variantId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        product.removeVariant(variantId);
        productRepository.save(product);

        log.info("Variant removed: productId={}, variantId={}", productId, variantId);
    }
}
