package com.example.product.application.service;

import com.example.product.application.command.UpdateProductCommand;
import com.example.product.domain.event.ProductEvent;
import com.example.product.domain.event.ProductUpdatedPayload;
import com.example.product.domain.exception.ProductNotFoundException;
import com.example.product.domain.model.Price;
import com.example.product.domain.model.Product;
import com.example.product.domain.repository.ProductRepository;
import com.example.product.application.port.ProductMetricPort;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateProductService {

    private final ProductRepository productRepository;
    private final EventPublishingHelper eventPublishingHelper;
    private final ProductMetricPort productMetrics;

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "product-list", allEntries = true),
            // allEntries: the read key carries a seller-scope segment a targeted
            // evict cannot reliably match (TASK-BE-436).
            @CacheEvict(value = "product-detail", allEntries = true)
    })
    public UUID update(UpdateProductCommand command) {
        Product product = productRepository.findById(command.productId())
                .orElseThrow(() -> new ProductNotFoundException(command.productId()));

        if (command.name() != null) {
            product.updateName(command.name());
        }
        if (command.description() != null) {
            product.updateDescription(command.description());
        }
        if (command.price() != null) {
            product.updatePrice(new Price(command.price()));
        }
        if (command.status() != null) {
            product.changeStatus(command.status());
        }
        if (command.thumbnailUrl() != null) {
            product.updateThumbnailUrl(command.thumbnailUrl());
        }

        productRepository.save(product);
        productMetrics.incrementProductUpdated();

        eventPublishingHelper.publishSafely(
                ProductEvent.updated(ProductUpdatedPayload.from(product)),
                "product", product.getId());

        return product.getId();
    }
}
