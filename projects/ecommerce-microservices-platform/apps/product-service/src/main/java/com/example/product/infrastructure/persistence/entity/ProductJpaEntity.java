package com.example.product.infrastructure.persistence.entity;

import com.example.product.domain.model.Price;
import com.example.product.domain.model.Product;
import com.example.product.domain.model.ProductStatus;
import com.example.product.domain.model.ProductVariant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductJpaEntity implements Persistable<UUID> {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

    /**
     * Owning seller within the tenant (ADR-MONO-030 Step 3 §3.2 — inner axis).
     * Stamped once at insert from the resolved domain attribute; immutable
     * afterward (ownership key {@code (tenant_id, seller_id)}).
     */
    @Column(name = "seller_id", nullable = false, updatable = false, length = 64)
    private String sellerId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private long price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    @Column(name = "category_id", columnDefinition = "uuid")
    private UUID categoryId;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ProductVariantJpaEntity> variants = new ArrayList<>();

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = false;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public static ProductJpaEntity from(Product product, String tenantId) {
        ProductJpaEntity entity = new ProductJpaEntity();
        entity.id = product.getId();
        entity.tenantId = tenantId;
        entity.sellerId = product.getSellerId();
        entity.name = product.getName();
        entity.description = product.getDescription();
        entity.price = product.getPrice().value();
        entity.status = product.getStatus();
        entity.categoryId = product.getCategoryId();
        entity.thumbnailUrl = product.getThumbnailUrl();
        entity.createdAt = product.getCreatedAt();
        entity.updatedAt = product.getUpdatedAt();
        entity.isNew = true;
        entity.variants = product.getVariants().stream()
                .map(v -> ProductVariantJpaEntity.from(v, entity))
                .collect(Collectors.toCollection(ArrayList::new));
        return entity;
    }

    public Product toDomain() {
        List<ProductVariant> domainVariants = variants.stream()
                .map(ProductVariantJpaEntity::toDomain)
                .toList();
        return Product.reconstitute(
                id, name, description, new Price(price),
                status, categoryId, thumbnailUrl, sellerId, createdAt, updatedAt, domainVariants
        );
    }

    public void update(Product product) {
        this.name = product.getName();
        this.description = product.getDescription();
        this.price = product.getPrice().value();
        this.status = product.getStatus();
        this.categoryId = product.getCategoryId();
        this.thumbnailUrl = product.getThumbnailUrl();
        this.updatedAt = product.getUpdatedAt();
        syncVariants(product.getVariants());
    }

    private void syncVariants(List<ProductVariant> domainVariants) {
        Set<UUID> domainIds = domainVariants.stream()
                .map(ProductVariant::getId)
                .collect(Collectors.toSet());

        variants.removeIf(e -> !domainIds.contains(e.getId()));

        Map<UUID, ProductVariantJpaEntity> remainingById = variants.stream()
                .collect(Collectors.toMap(ProductVariantJpaEntity::getId, v -> v));

        for (ProductVariant dv : domainVariants) {
            ProductVariantJpaEntity ev = remainingById.get(dv.getId());
            if (ev == null) {
                variants.add(ProductVariantJpaEntity.from(dv, this));
            } else {
                ev.update(dv);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductJpaEntity e)) return false;
        return id != null && id.equals(e.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
