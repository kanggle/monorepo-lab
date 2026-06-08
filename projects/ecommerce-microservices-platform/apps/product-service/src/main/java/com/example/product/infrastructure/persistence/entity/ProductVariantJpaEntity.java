package com.example.product.infrastructure.persistence.entity;

import com.example.product.domain.model.Price;
import com.example.product.domain.model.ProductVariant;
import com.example.product.domain.model.StockQuantity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "product_variants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductVariantJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductJpaEntity product;

    @Column(name = "option_name", nullable = false, length = 100)
    private String optionName;

    @Column(nullable = false)
    private int stock;

    @Column(name = "additional_price", nullable = false)
    private long additionalPrice;

    @Column(name = "sku", length = 64)
    private String sku;

    @Version
    private long version;

    public static ProductVariantJpaEntity from(ProductVariant variant, ProductJpaEntity product) {
        ProductVariantJpaEntity entity = new ProductVariantJpaEntity();
        entity.id = variant.getId();
        entity.product = product;
        entity.optionName = variant.getOptionName();
        entity.stock = variant.getStock().value();
        entity.additionalPrice = variant.getAdditionalPrice().value();
        entity.sku = variant.getSku();
        return entity;
    }

    public ProductVariant toDomain() {
        return ProductVariant.reconstitute(
                id,
                product.getId(),
                optionName,
                new StockQuantity(stock),
                new Price(additionalPrice),
                sku
        );
    }

    public void update(ProductVariant variant) {
        this.optionName = variant.getOptionName();
        this.additionalPrice = variant.getAdditionalPrice().value();
        // stock은 Inventory 애그리게이트를 통해서만 변경
    }

    public void updateStock(int newStock) {
        this.stock = newStock;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductVariantJpaEntity e)) return false;
        return id != null && id.equals(e.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
