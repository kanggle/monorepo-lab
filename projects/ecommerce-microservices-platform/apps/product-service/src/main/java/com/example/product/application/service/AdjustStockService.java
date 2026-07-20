package com.example.product.application.service;

import com.example.product.application.command.AdjustStockCommand;
import com.example.product.application.dto.AdjustStockResult;
import com.example.product.application.dto.StockAdjustmentType;
import com.example.product.domain.event.ProductEvent;
import com.example.product.domain.event.StockChangedPayload;
import com.example.product.domain.exception.IdempotencyKeyConflictException;
import com.example.product.domain.exception.IdempotencyKeyRequiredException;
import com.example.product.domain.exception.ProductNotFoundException;
import com.example.product.domain.exception.VariantNotFoundException;
import com.example.product.domain.model.Inventory;
import com.example.product.domain.model.Product;
import com.example.product.domain.model.StockAdjustmentRequest;
import com.example.product.domain.repository.InventoryRepository;
import com.example.product.domain.repository.ProductRepository;
import com.example.product.domain.repository.StockAdjustmentRequestRepository;
import com.example.product.application.port.ProductMetricPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdjustStockService {

    private record StockSnapshot(int previousStock, int currentStock) {}

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final EventPublishingHelper eventPublishingHelper;
    private final ProductMetricPort productMetrics;
    private final ReservationRetryService reservationRetryService;
    /** Idempotency store for this admin write path (TASK-BE-536). */
    private final StockAdjustmentRequestRepository stockAdjustmentRequestRepository;
    private final Clock clock;

    /**
     * Adjusts a variant's stock. {@code Idempotency-Key} is <b>required</b>
     * (TASK-BE-536): unlike a natural key, "the same delta requested twice" cannot
     * be rejected outright — a genuine "+10 received twice" warehouse event is real
     * (the task's Edge Cases) and is byte-identical on the wire to a retry of the
     * first request, so only the client can tell them apart.
     *
     * <ul>
     *   <li><b>Absent / blank key → {@link IdempotencyKeyRequiredException}</b> (400).</li>
     *   <li><b>Same key, same quantity → replay.</b> Returns the variant's current
     *       stock without adjusting Inventory again, without re-publishing
     *       {@code StockChanged}, and without re-triggering the backordered-reservation
     *       retry (AC-1 / AC-3 — a guard that blocks the DB write but still publishes
     *       moves the defect downstream instead of fixing it).</li>
     *   <li><b>Same key, different quantity → {@link IdempotencyKeyConflictException}</b>
     *       (409).</li>
     *   <li><b>Different key, same variant → proceeds.</b> A real second adjustment
     *       (AC-2) — the regression a naive "reject any second adjustment" guard
     *       would introduce.</li>
     * </ul>
     *
     * <p><b>Concurrency.</b> The arbiter is {@code UNIQUE (variant_id,
     * idempotency_key)}, not the {@link StockAdjustmentRequestRepository#find}
     * lookup above it: two simultaneous duplicates may both miss the read, but only
     * one {@link StockAdjustmentRequestRepository#insert} can commit. The insert is
     * ordered <b>before</b> the inventory write, so a race loser never reaches the
     * stock mutation. Same shape as {@code PaymentRefundService.refundPayment}
     * (TASK-BE-535).
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "product-list", allEntries = true),
            // allEntries (not a targeted key): the @Cacheable("product-detail")
            // read key carries a seller-scope segment that the write path's
            // SellerScopeContext may not match, so a targeted evict misses
            // (TASK-BE-436). Mirrors RegisterProductService/ProductImageService.
            @CacheEvict(value = "product-detail", allEntries = true)
    })
    public AdjustStockResult adjust(AdjustStockCommand command) {
        validateQuantity(command);
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw new IdempotencyKeyRequiredException(
                    "Idempotency-Key 헤더는 재고 조정 요청에 필수입니다");
        }

        Product product = findProductAndValidateVariant(command.productId(), command.variantId());

        Optional<StockAdjustmentRequest> replayed = stockAdjustmentRequestRepository.find(
                command.variantId(), command.idempotencyKey());
        if (replayed.isPresent()) {
            if (!replayed.get().matchesQuantity(command.quantity())) {
                throw new IdempotencyKeyConflictException(
                        "동일한 Idempotency-Key 가 다른 재고 조정 수량으로 재사용되었습니다: variantId="
                                + command.variantId() + ", 최초=" + replayed.get().getQuantity()
                                + ", 요청=" + command.quantity());
            }
            // Replay: the adjustment was already performed under this key. Do NOT
            // adjust Inventory again, do NOT re-publish StockChanged, do NOT
            // re-trigger the backorder retry. Return the current stock.
            int currentStock = inventoryRepository.findByVariantId(command.variantId())
                    .orElseThrow(() -> new VariantNotFoundException(command.variantId()))
                    .currentStock().value();
            log.info("Idempotent stock-adjustment replay: variantId={}, quantity={}, currentStock={}",
                    command.variantId(), command.quantity(), currentStock);
            return new AdjustStockResult(command.variantId(), currentStock);
        }

        // Claim the key BEFORE any stock moves. A concurrent duplicate that also
        // missed the lookup above loses this insert and never reaches the adjustment.
        try {
            stockAdjustmentRequestRepository.insert(
                    StockAdjustmentRequest.of(command.variantId(), command.idempotencyKey(),
                            command.quantity(), clock.instant()));
        } catch (DataIntegrityViolationException e) {
            throw new IdempotencyKeyConflictException(
                    "동일한 Idempotency-Key 의 재고 조정 요청이 이미 처리 중이거나 처리되었습니다: variantId="
                            + command.variantId(), e);
        }

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
