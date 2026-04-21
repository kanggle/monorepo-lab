package com.example.product.infrastructure.persistence.entity;

import com.example.product.domain.model.ProductImage;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImageJpaEntity implements Persistable<UUID> {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "product_id", nullable = false, columnDefinition = "uuid")
    private UUID productId;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

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

    public static ProductImageJpaEntity from(ProductImage image) {
        ProductImageJpaEntity entity = new ProductImageJpaEntity();
        entity.id = image.getId();
        entity.productId = image.getProductId();
        entity.objectKey = image.getObjectKey();
        entity.sortOrder = image.getSortOrder();
        entity.isPrimary = image.isPrimary();
        entity.uploadedAt = image.getUploadedAt();
        entity.isNew = true;
        return entity;
    }

    public ProductImage toDomain() {
        return ProductImage.reconstitute(id, productId, objectKey, sortOrder, isPrimary, uploadedAt);
    }

    public void update(ProductImage image) {
        this.sortOrder = image.getSortOrder();
        this.isPrimary = image.isPrimary();
    }
}
