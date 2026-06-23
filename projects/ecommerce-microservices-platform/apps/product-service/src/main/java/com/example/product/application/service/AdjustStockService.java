package com.example.product.application.service;

import com.example.product.application.command.AdjustStockCommand;
import com.example.product.application.dto.AdjustStockResult;
import com.example.product.application.dto.StockAdjustmentType;
import com.example.product.domain.event.ProductEvent;
import com.example.product.domain.event.StockChangedPayload;
import com.example.product.domain.exception.ProductNotFoundException;
import com.example.product.domain.exception.VariantNotFoundException;
import com.example.product.domain.model.Inventory;
import com.example.product.domain.model.Product;
import com.example.product.domain.repository.InventoryRepository;
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
public class AdjustStockService {

    private record StockSnapshot(int previousStock, int currentStock) {}

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final EventPublishingHelper eventPublishingHelper;
    private final ProductMetricPort productMetrics;
    private final ReservationRetryService reservationRetryService;

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "product-list", allEntries = true),
            @CacheEvict(value = "product-detail", key = "T(com.example.product.domain.tenant.TenantContext).currentTenant() + ':' + #command.productId()")
    })
    public AdjustStockResult adjust(AdjustStockCommand command) {
        validateQuantity(command);
        Product product = findProductAndValidateVariant(command.productId(), command.variantId());
        StockSnapshot snapshot = adjustInventoryStock(command.variantId(), command.quantity());
        recordStockMetrics(command, snapshot.currentStock(), product);
        publishStockChangedEvent(command, command.variantId(), snapshot.previousStock(), snapshot.currentStock());
        // A positive adjustment may satisfy waiting backordered reservations (TASK-BE-428, AC-4).
        if (command.quantity() > 0) {
            reservationRetryService.onStockIncreased(command.variantId());
        }
        return new AdjustStockResult(command.variantId(), snapshot.currentStock());
    }

    private void validateQuantity(AdjustStockCommand command) {
        if (command.quantity() == 0) {
            throw new IllegalArgumentException("Stock adjustment quantity must not be zero");
        }
    }

    private Product findProductAndValidateVariant(UUID productId, UUID variantId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        boolean variantBelongsToProduct = product.getVariants().stream()
                .anyMatch(v -> v.getId().equals(variantId));
        if (!variantBelongsToProduct) {
            throw new VariantNotFoundException(variantId);
        }
        return product;
    }

    private StockSnapshot adjustInventoryStock(UUID variantId, int quantity) {
        Inventory inventory = inventoryRepository.findByVariantId(variantId)
                .orElseThrow(() -> new VariantNotFoundException(variantId));
        int previousStock = inventory.currentStock().value();
        inventory.adjustStock(quantity);
        int currentStock = inventory.currentStock().value();
        inventoryRepository.save(inventory);
        return new StockSnapshot(previousStock, currentStock);
    }

    private void recordStockMetrics(AdjustStockCommand command, int currentStock, Product product) {
        StockAdjustmentType adjustType = StockAdjustmentType.of(command.quantity(), command.reason());
        productMetrics.incrementStockAdjusted(adjustType);

        boolean statusChanged = product.adjustStatusByStock(currentStock);
        if (statusChanged) {
            if (currentStock == 0) {
                productMetrics.incrementOutOfStock();
            }
            productRepository.save(product);
        }
    }

    private void publishStockChangedEvent(AdjustStockCommand command, UUID variantId,
                                          int previousStock, int currentStock) {
        eventPublishingHelper.publishSafely(
                ProductEvent.stockChanged(new StockChangedPayload(
                        command.productId().toString(),
                        variantId.toString(),
                        previousStock,
                        currentStock,
                        command.quantity(),
                        command.reason(),
                        null // orderId: 수동 재고 조정에는 해당 없음
                )),
                "variant", variantId);
    }
}
