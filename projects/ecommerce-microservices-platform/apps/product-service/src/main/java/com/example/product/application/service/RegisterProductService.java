package com.example.product.application.service;

import com.example.product.application.command.RegisterProductCommand;
import com.example.product.domain.event.ProductCreatedPayload;
import com.example.product.domain.event.ProductEvent;
import com.example.product.domain.model.Price;
import com.example.product.domain.model.Product;
import com.example.product.domain.model.ProductVariant;
import com.example.product.domain.model.StockQuantity;
import com.example.product.domain.exception.InvalidCategoryException;
import com.example.product.domain.repository.CategoryRepository;
import com.example.product.domain.repository.ProductRepository;
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
