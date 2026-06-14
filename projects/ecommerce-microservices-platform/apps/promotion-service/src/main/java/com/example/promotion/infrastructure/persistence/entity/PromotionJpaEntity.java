package com.example.promotion.infrastructure.persistence.entity;

import com.example.promotion.domain.promotion.DiscountType;
import com.example.promotion.domain.promotion.Promotion;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "promotions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PromotionJpaEntity {

    @Id
    @Column(name = "promotion_id")
    private String promotionId;

    /**
     * Outer-axis tenant owning this promotion (ADR-MONO-030 Step 4, M1; TASK-BE-368).
     * Stamped once at insert from the request tenant context; immutable afterward, so
     * an operator update preserves the promotion's tenant. Not part of the clean
     * {@code Promotion} domain model — persistence/event layers only.
     */
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false)
    private long discountValue;

    @Column(name = "max_discount_amount", nullable = false)
    private long maxDiscountAmount;

    @Column(name = "max_issuance_count", nullable = false)
    private int maxIssuanceCount;

    @Column(name = "issued_count", nullable = false)
    private int issuedCount;

    @Column(name = "start_date", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant startDate;

    @Column(name = "end_date", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant endDate;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant updatedAt;

    public static PromotionJpaEntity fromDomain(Promotion promotion, String tenantId) {
        PromotionJpaEntity entity = new PromotionJpaEntity();
        entity.promotionId = promotion.getPromotionId();
        entity.tenantId = tenantId;
        entity.name = promotion.getName();
        entity.description = promotion.getDescription();
        entity.discountType = promotion.getDiscountType();
        entity.discountValue = promotion.getDiscountValue();
        entity.maxDiscountAmount = promotion.getMaxDiscountAmount();
        entity.maxIssuanceCount = promotion.getMaxIssuanceCount();
        entity.issuedCount = promotion.getIssuedCount();
        entity.startDate = promotion.getStartDate();
        entity.endDate = promotion.getEndDate();
        entity.createdAt = promotion.getCreatedAt();
        entity.updatedAt = promotion.getUpdatedAt();
        return entity;
    }

    public void updateFrom(Promotion promotion) {
        this.name = promotion.getName();
        this.description = promotion.getDescription();
        this.discountType = promotion.getDiscountType();
        this.discountValue = promotion.getDiscountValue();
        this.maxDiscountAmount = promotion.getMaxDiscountAmount();
        this.maxIssuanceCount = promotion.getMaxIssuanceCount();
        this.issuedCount = promotion.getIssuedCount();
        this.startDate = promotion.getStartDate();
        this.endDate = promotion.getEndDate();
        this.updatedAt = promotion.getUpdatedAt();
    }

    public Promotion toDomain() {
        return Promotion.reconstitute(
                promotionId, name, description,
                discountType, discountValue,
                maxDiscountAmount, maxIssuanceCount,
                issuedCount, startDate, endDate,
                createdAt, updatedAt
        );
    }
}
