package com.example.product.infrastructure.persistence;

import com.example.product.domain.model.ProductStatus;
import com.example.product.infrastructure.persistence.entity.ProductJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, UUID> {

    // Every read filters by tenant_id first (outer isolation axis, M2), then —
    // when a concrete seller scope is bound (OPERATOR seller-scoped read) — narrows
    // to that seller. The seller predicate is net-zero / fail-OPEN (ADR-025 F1):
    // `(:sellerRestricted = false OR p.sellerId = :sellerScope)` collapses to no
    // filter when unrestricted (absent / '*' / CONSUMER plane), returning the full
    // tenant view. The seller filter is ALWAYS nested inside the tenant filter
    // (isolate-then-attribute, AC-6) — it can never reach another tenant's rows.

    @Query("SELECT p FROM ProductJpaEntity p LEFT JOIN FETCH p.variants "
            + "WHERE p.id = :id AND p.tenantId = :tenantId "
            + "AND (:sellerRestricted = false OR p.sellerId = :sellerScope) "
            + "AND p.deletedAt IS NULL")
    Optional<ProductJpaEntity> findWithVariantsById(@Param("id") UUID id,
                                                    @Param("tenantId") String tenantId,
                                                    @Param("sellerRestricted") boolean sellerRestricted,
                                                    @Param("sellerScope") String sellerScope);

    // The :name predicate is case-insensitive partial match (LOWER + LIKE, JPQL has
    // no ILIKE) and null/blank-guarded like categoryId/status — absent OR blank name
    // returns the full (tenant- and seller-scoped) view (a cleared search box sends
    // ?name= which binds to "" — treat it as no filter, not LIKE '' = match-nothing).
    // LOWER(...) LIKE LOWER(CONCAT('%',?,'%')) is portable across Postgres (prod) and
    // H2 PostgreSQL-mode (tests). A leading wildcard cannot use a B-tree index; a
    // pg_trgm GIN index on name is a future follow-up if catalog scan cost matters.
    @Query("SELECT p FROM ProductJpaEntity p WHERE p.tenantId = :tenantId "
            + "AND (:categoryId IS NULL OR p.categoryId = :categoryId) "
            + "AND (:status IS NULL OR p.status = :status) "
            + "AND (:name IS NULL OR :name = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%'))) "
            + "AND (:sellerRestricted = false OR p.sellerId = :sellerScope) "
            + "AND p.deletedAt IS NULL")
    Page<ProductJpaEntity> findByFilters(
            @Param("tenantId") String tenantId,
            @Param("categoryId") UUID categoryId,
            @Param("status") ProductStatus status,
            @Param("name") String name,
            @Param("sellerRestricted") boolean sellerRestricted,
            @Param("sellerScope") String sellerScope,
            Pageable pageable);

    @Query("SELECT COUNT(p) > 0 FROM ProductJpaEntity p WHERE p.id = :id AND p.tenantId = :tenantId AND p.deletedAt IS NULL")
    boolean existsActiveById(@Param("id") UUID id, @Param("tenantId") String tenantId);

    @Modifying
    @Query("UPDATE ProductJpaEntity p SET p.deletedAt = :deletedAt WHERE p.id = :id AND p.tenantId = :tenantId")
    void softDeleteById(@Param("id") UUID id, @Param("tenantId") String tenantId, @Param("deletedAt") Instant deletedAt);

    /** Total non-deleted products for a tenant (all-time). */
    @Query("SELECT COUNT(p) FROM ProductJpaEntity p WHERE p.tenantId = :tenantId AND p.deletedAt IS NULL")
    long countByTenantId(@Param("tenantId") String tenantId);

    /** Non-deleted products created in [from, to) for a tenant. */
    @Query("SELECT COUNT(p) FROM ProductJpaEntity p WHERE p.tenantId = :tenantId "
            + "AND p.createdAt >= :from AND p.createdAt < :to AND p.deletedAt IS NULL")
    long countByTenantIdAndCreatedAtBetween(@Param("tenantId") String tenantId,
                                            @Param("from") Instant from,
                                            @Param("to") Instant to);
}
