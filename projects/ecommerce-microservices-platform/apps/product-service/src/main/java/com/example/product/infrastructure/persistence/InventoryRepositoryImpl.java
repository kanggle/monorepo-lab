package com.example.product.infrastructure.persistence;

import com.example.product.domain.exception.VariantNotFoundException;
import com.example.product.infrastructure.persistence.entity.ProductVariantJpaEntity;
import com.example.product.domain.model.Inventory;
import com.example.product.domain.model.StockQuantity;
import com.example.product.domain.repository.InventoryRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
class InventoryRepositoryImpl implements InventoryRepository {

    private final ProductVariantJpaRepository jpaRepository;

    InventoryRepositoryImpl(ProductVariantJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public Inventory save(Inventory inventory) {
        ProductVariantJpaEntity entity = jpaRepository.findById(inventory.getVariantId())
                .orElseThrow(() -> new VariantNotFoundException(inventory.getVariantId()));
        entity.updateStock(inventory.currentStock().value());
        jpaRepository.save(entity);
        return inventory;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Inventory> findByVariantId(UUID variantId) {
        return jpaRepository.findById(variantId)
                .map(entity -> Inventory.create(entity.getId(), new StockQuantity(entity.getStock())));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VariantRef> findVariantBySku(String sku) {
        if (sku == null || sku.isBlank()) {
            return Optional.empty();
        }
        return jpaRepository.findBySku(sku)
                .map(entity -> new VariantRef(
                        entity.getId(), entity.getProduct().getId(), entity.getStock()));
    }
}
