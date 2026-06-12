package com.example.product.application.service;

import com.example.product.application.command.RegisterProductCommand;
import com.example.product.domain.event.ProductCreatedPayload;
import com.example.product.domain.event.ProductEvent;
import com.example.product.domain.model.Price;
import com.example.product.domain.model.Product;
import com.example.product.domain.model.ProductVariant;
import com.example.product.domain.model.Seller;
import com.example.product.domain.model.StockQuantity;
import com.example.product.domain.exception.InvalidCategoryException;
import com.example.product.domain.repository.CategoryRepository;
import com.example.product.domain.repository.ProductRepository;
import com.example.product.domain.repository.SellerRepository;
import com.example.product.application.port.ProductMetricPort;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RegisterProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SellerRepository sellerRepository;
    private final SellerOwnershipResolver sellerOwnershipResolver;
    private final EventPublishingHelper eventPublishingHelper;
    private final ProductMetricPort productMetrics;

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "product-list", allEntries = true),
            @CacheEvict(value = "product-detail", allEntries = true)
    })
    public UUID register(RegisterProductCommand command) {
        if (command.categoryId() != null) {
            categoryRepository.findById(command.categoryId())
                    .orElseThrow(() -> new InvalidCategoryException(command.categoryId()));
        }

        // Resolve product ownership: request seller / restricted scope / tenant
        // default (ADR-MONO-030 §3.2). When it resolves to the per-tenant default
        // seller (standalone / no scope, D8), make sure that seller row exists so
        // the ownership key always references a real seller (idempotent).
        String sellerId = sellerOwnershipResolver.resolveForRegister(command.sellerId());
        if (Seller.DEFAULT_SELLER_ID.equals(sellerId)) {
            sellerRepository.ensureDefaultSeller();
        }

        List<ProductVariant> variants = command.variants().stream()
                .map(v -> ProductVariant.create(
                        v.optionName(),
                        new StockQuantity(v.stock()),
                        new Price(v.additionalPrice())))
                .toList();

        Product product = Product.create(
                command.name(),
                command.description(),
                new Price(command.price()),
                command.categoryId(),
                sellerId,
                variants);
        if (command.thumbnailUrl() != null) {
            product.updateThumbnailUrl(command.thumbnailUrl());
        }

        productRepository.save(product);
        productMetrics.incrementProductCreated();

        eventPublishingHelper.publishSafely(
                ProductEvent.created(ProductCreatedPayload.from(product)),
                "product", product.getId());

        return product.getId();
    }
}
