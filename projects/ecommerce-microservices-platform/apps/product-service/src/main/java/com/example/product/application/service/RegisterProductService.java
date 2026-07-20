package com.example.product.application.service;

import com.example.product.application.command.RegisterProductCommand;
import com.example.product.domain.event.ProductCreatedPayload;
import com.example.product.domain.event.ProductEvent;
import com.example.product.domain.exception.IdempotencyKeyConflictException;
import com.example.product.domain.exception.IdempotencyKeyRequiredException;
import com.example.product.domain.model.Price;
import com.example.product.domain.model.Product;
import com.example.product.domain.model.ProductCreateRequest;
import com.example.product.domain.model.ProductVariant;
import com.example.product.domain.model.Seller;
import com.example.product.domain.model.StockQuantity;
import com.example.product.domain.exception.InvalidCategoryException;
import com.example.product.domain.repository.CategoryRepository;
import com.example.product.domain.repository.ProductCreateRequestRepository;
import com.example.product.domain.repository.ProductRepository;
import com.example.product.domain.repository.SellerRepository;
import com.example.product.domain.tenant.TenantContext;
import com.example.product.application.port.ProductMetricPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SellerRepository sellerRepository;
    private final SellerOwnershipResolver sellerOwnershipResolver;
    private final EventPublishingHelper eventPublishingHelper;
    private final ProductMetricPort productMetrics;
    /** Idempotency store for this admin write path (TASK-BE-536). */
    private final ProductCreateRequestRepository productCreateRequestRepository;
    private final Clock clock;

    /**
     * Registers a new product. {@code Idempotency-Key} is <b>required</b>
     * (TASK-BE-536): a replayed registration must not create a second product with
     * a second stock ledger. A name-uniqueness natural key is deliberately NOT used
     * — two genuinely different products can legitimately share a name (F1) — so
     * only a client key can separate a retry from an intentional same-named product.
     *
     * <ul>
     *   <li><b>Absent / blank key → {@link IdempotencyKeyRequiredException}</b> (400).</li>
     *   <li><b>Same key, same name → replay.</b> Returns the ALREADY-created
     *       product's id without creating a second product or re-publishing
     *       {@code ProductCreated}.</li>
     *   <li><b>Same key, different name → {@link IdempotencyKeyConflictException}</b>
     *       (409).</li>
     *   <li><b>Different key → proceeds.</b> A genuine second registration (AC-2),
     *       even with the same name.</li>
     * </ul>
     *
     * <p><b>Concurrency.</b> The arbiter is {@code UNIQUE (tenant_id,
     * idempotency_key)}: the {@link Product} aggregate is built (and its id
     * assigned) BEFORE the claim insert, so the claim row can record the resulting
     * {@code productId} without a second round-trip, but the claim insert itself
     * happens BEFORE {@link ProductRepository#save}, so a concurrent duplicate that
     * also missed the replay lookup loses the insert race and never persists a
     * second product.
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "product-list", allEntries = true),
            @CacheEvict(value = "product-detail", allEntries = true)
    })
    public UUID register(RegisterProductCommand command) {
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw new IdempotencyKeyRequiredException(
                    "Idempotency-Key 헤더는 상품 등록 요청에 필수입니다");
        }

        if (command.categoryId() != null) {
            categoryRepository.findById(command.categoryId())
                    .orElseThrow(() -> new InvalidCategoryException(command.categoryId()));
        }

        String tenantId = TenantContext.currentTenant();
        Optional<ProductCreateRequest> replayed =
                productCreateRequestRepository.find(tenantId, command.idempotencyKey());
        if (replayed.isPresent()) {
            if (!replayed.get().matchesName(command.name())) {
                throw new IdempotencyKeyConflictException(
                        "동일한 Idempotency-Key 가 다른 상품명으로 재사용되었습니다: 최초="
                                + replayed.get().getName() + ", 요청=" + command.name());
            }
            // Replay: the registration was already performed under this key. Do NOT
            // create a second product, do NOT re-publish ProductCreated.
            log.info("Idempotent product-create replay: tenantId={}, productId={}",
                    tenantId, replayed.get().getProductId());
            return replayed.get().getProductId();
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

        // Claim the key BEFORE the product is persisted. A concurrent duplicate
        // that also missed the lookup above loses this insert and never persists a
        // second product row.
        try {
            productCreateRequestRepository.insert(ProductCreateRequest.of(
                    tenantId, command.idempotencyKey(), command.name(), product.getId(), clock.instant()));
        } catch (DataIntegrityViolationException e) {
            throw new IdempotencyKeyConflictException(
                    "동일한 Idempotency-Key 의 상품 등록 요청이 이미 처리 중이거나 처리되었습니다: tenantId="
                            + tenantId, e);
        }

        productRepository.save(product);
        productMetrics.incrementProductCreated();

        eventPublishingHelper.publishSafely(
                ProductEvent.created(ProductCreatedPayload.from(product)),
                "product", product.getId());

        return product.getId();
    }
}
