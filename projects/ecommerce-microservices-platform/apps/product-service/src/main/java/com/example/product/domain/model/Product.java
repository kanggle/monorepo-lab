package com.example.product.domain.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Product {

    private UUID id;
    private String name;
    private String description;
    private Price price;
    private ProductStatus status;
    private UUID categoryId;
    private String thumbnailUrl;
    private Instant createdAt;
    private Instant updatedAt;
    private List<ProductVariant> variants = new ArrayList<>();
    private transient boolean isNew;

    public static Product create(String name, String description, Price price, UUID categoryId,
                                 List<ProductVariant> variants) {
        validateName(name);
        if (price == null) {
            throw new IllegalArgumentException("Price must not be null");
        }
        if (variants == null || variants.isEmpty()) {
            throw new IllegalArgumentException("Product must have at least one variant");
        }

        Product product = new Product();
        product.id = UUID.randomUUID();
        product.isNew = true;
        product.name = name.trim();
        product.description = description;
        product.price = price;
        product.status = ProductStatus.ON_SALE;
        product.categoryId = categoryId;
        Instant now = Instant.now();
        product.createdAt = now;
        product.updatedAt = now;

        for (ProductVariant variant : variants) {
            if (variant == null) {
                throw new IllegalArgumentException("Variant must not be null");
            }
            variant.assignProduct(product.id);
            product.variants.add(variant);
        }

        return product;
    }

    public static Product reconstitute(UUID id, String name, String description, Price price,
                                       ProductStatus status, UUID categoryId,
                                       Instant createdAt, Instant updatedAt,
                                       List<ProductVariant> variants) {
        return reconstitute(id, name, description, price, status, categoryId, null,
                createdAt, updatedAt, variants);
    }

    public static Product reconstitute(UUID id, String name, String description, Price price,
                                       ProductStatus status, UUID categoryId, String thumbnailUrl,
                                       Instant createdAt, Instant updatedAt,
                                       List<ProductVariant> variants) {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Product name must not be blank");
        if (price == null) throw new IllegalArgumentException("Price must not be null");
        if (status == null) throw new IllegalArgumentException("Status must not be null");
        if (variants == null) throw new IllegalArgumentException("Variants must not be null");
        Product product = new Product();
        product.id = id;
        product.name = name;
        product.description = description;
        product.price = price;
        product.status = status;
        product.categoryId = categoryId;
        product.thumbnailUrl = thumbnailUrl;
        product.createdAt = createdAt;
        product.updatedAt = updatedAt;
        product.variants = new ArrayList<>(variants);
        return product;
    }

    public void addVariant(ProductVariant variant) {
        if (variant == null) {
            throw new IllegalArgumentException("Variant must not be null");
        }
        variant.assignProduct(this.id);
        this.variants.add(variant);
        this.updatedAt = Instant.now();
    }

    public ProductVariant updateVariant(UUID variantId, String optionName, Price additionalPrice) {
        ProductVariant variant = variants.stream()
                .filter(v -> v.getId().equals(variantId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Variant not found: " + variantId));
        variant.updateOption(optionName, additionalPrice);
        this.updatedAt = Instant.now();
        return variant;
    }

    public void removeVariant(UUID variantId) {
        if (variants.size() <= 1) {
            throw new IllegalStateException("Product must have at least one variant");
        }
        boolean removed = variants.removeIf(v -> v.getId().equals(variantId));
        if (!removed) {
            throw new IllegalArgumentException("Variant not found: " + variantId);
        }
        this.updatedAt = Instant.now();
    }

    public void updateName(String name) {
        validateName(name);
        this.name = name.trim();
        this.updatedAt = Instant.now();
    }

    public void updateDescription(String description) {
        this.description = (description != null && description.isBlank()) ? null : description;
        this.updatedAt = Instant.now();
    }

    public void updatePrice(Price price) {
        if (price == null) {
            throw new IllegalArgumentException("Price must not be null");
        }
        this.price = price;
        this.updatedAt = Instant.now();
    }

    public void updateThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
        this.updatedAt = Instant.now();
    }

    public void changeStatus(ProductStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Status must not be null");
        }
        this.status = status;
        this.updatedAt = Instant.now();
    }

    /**
     * 재고 수량에 따라 상품 상태를 전환한다.
     * 재고가 0이면 SOLD_OUT, 재고가 0 초과이면 SOLD_OUT 상태에서 ON_SALE로 복구한다.
     * 상태가 실제로 변경된 경우 true를 반환한다.
     *
     * @param currentStock 현재 재고 수량
     * @return 상태가 변경된 경우 true, 변경 없으면 false
     */
    public boolean adjustStatusByStock(int currentStock) {
        if (currentStock == 0 && this.status != ProductStatus.SOLD_OUT) {
            this.status = ProductStatus.SOLD_OUT;
            this.updatedAt = Instant.now();
            return true;
        }
        if (currentStock > 0 && this.status == ProductStatus.SOLD_OUT) {
            this.status = ProductStatus.ON_SALE;
            this.updatedAt = Instant.now();
            return true;
        }
        return false;
    }

    public List<ProductVariant> getVariants() {
        return Collections.unmodifiableList(variants);
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Product name must not be blank");
        }
        if (name.trim().length() > 255) {
            throw new IllegalArgumentException("Product name must not exceed 255 characters");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Product p)) return false;
        return id != null && id.equals(p.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
