package com.example.scmplatform.procurement.infrastructure.persistence.jpa;

import com.example.scmplatform.procurement.domain.po.PurchaseOrder;
import com.example.scmplatform.procurement.domain.po.repository.SubmittedPoRow;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderJpaRepository extends JpaRepository<PurchaseOrder, String> {

    Optional<PurchaseOrder> findByIdAndTenantId(String id, String tenantId);

    Optional<PurchaseOrder> findByPoNumberAndTenantId(String poNumber, String tenantId);

    Optional<PurchaseOrder> findBySourceSuggestionIdAndTenantId(String sourceSuggestionId, String tenantId);

    /**
     * {@code GET /po} search. {@code submittedFrom} / {@code submittedTo} (TASK-MONO-810) are
     * a half-open {@code [from, to)} on {@code submitted_at}; a NULL {@code submitted_at}
     * (DRAFT) fails both comparisons, so it is excluded whenever either bound is given.
     * {@code excludeStatus} drops one current status (the metrics card link sends
     * {@code CANCELED}, so {@code totalElements} equals {@code poCount}).
     *
     * <p>{@code CAST(:p AS string) IS NULL} on the nullable temporal guards — a bare
     * {@code :submittedFrom IS NULL} binds an untyped null PostgreSQL cannot type (42P18) on
     * an unfiltered call → 500. Same fix as wms admin-service {@code ShipmentSummaryRepository}
     * (TASK-BE-331/332); the {@code >=} / {@code <} comparison keeps the temporal typing.
     */
    @Query("""
            SELECT p FROM PurchaseOrder p
            WHERE p.tenantId = :tenantId
              AND (:status IS NULL OR p.status = :status)
              AND (:excludeStatus IS NULL OR p.status <> :excludeStatus)
              AND (:supplierId IS NULL OR p.supplierId = :supplierId)
              AND (CAST(:submittedFrom AS string) IS NULL OR p.submittedAt >= :submittedFrom)
              AND (CAST(:submittedTo AS string) IS NULL OR p.submittedAt < :submittedTo)
            ORDER BY p.createdAt DESC
            """)
    Page<PurchaseOrder> search(@Param("tenantId") String tenantId,
                               @Param("status") PoStatus status,
                               @Param("excludeStatus") PoStatus excludeStatus,
                               @Param("supplierId") String supplierId,
                               @Param("submittedFrom") Instant submittedFrom,
                               @Param("submittedTo") Instant submittedTo,
                               Pageable pageable);

    /** Metrics (TASK-MONO-810): every PO submitted in {@code [from, to)}, any current status. */
    @Query("""
            SELECT new com.example.scmplatform.procurement.domain.po.repository.SubmittedPoRow(
                       p.submittedAt, p.status, p.totalAmount.amount, p.totalAmount.currency)
            FROM PurchaseOrder p
            WHERE p.tenantId = :tenantId
              AND p.submittedAt >= :from
              AND p.submittedAt < :to
            """)
    List<SubmittedPoRow> findSubmittedIn(@Param("tenantId") String tenantId,
                                         @Param("from") Instant from,
                                         @Param("to") Instant to);
}
