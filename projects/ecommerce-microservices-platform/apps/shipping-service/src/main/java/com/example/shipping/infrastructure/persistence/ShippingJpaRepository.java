package com.example.shipping.infrastructure.persistence;

import com.example.shipping.domain.model.ShippingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ShippingJpaRepository extends JpaRepository<ShippingJpaEntity, String> {

    Optional<ShippingJpaEntity> findByOrderId(String orderId);

    boolean existsByOrderId(String orderId);

    Page<ShippingJpaEntity> findByStatus(ShippingStatus status, Pageable pageable);

    /**
     * In-flight shipments (status ∈ {@code statuses}) that carry a non-blank tracking number
     * AND carrier, oldest-updated first. Paged by the caller to a bounded batch (TASK-BE-360).
     */
    @Query("""
            SELECT s FROM ShippingJpaEntity s
            WHERE s.status IN :statuses
              AND s.trackingNumber IS NOT NULL AND s.trackingNumber <> ''
              AND s.carrier IS NOT NULL AND s.carrier <> ''
            ORDER BY s.updatedAt ASC
            """)
    List<ShippingJpaEntity> findInFlightWithTracking(@Param("statuses") Collection<ShippingStatus> statuses,
                                                     Pageable pageable);
}
