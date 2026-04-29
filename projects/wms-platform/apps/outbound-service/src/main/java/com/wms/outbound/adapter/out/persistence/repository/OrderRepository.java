package com.wms.outbound.adapter.out.persistence.repository;

import com.wms.outbound.adapter.out.persistence.entity.OrderEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    Optional<OrderEntity> findByOrderNo(String orderNo);

    boolean existsByOrderNo(String orderNo);

    @Query("""
            SELECT o FROM OrderEntity o
            WHERE (:status IS NULL OR o.status = :status)
              AND (:warehouseId IS NULL OR o.warehouseId = :warehouseId)
              AND (:customerPartnerId IS NULL OR o.customerPartnerId = :customerPartnerId)
              AND (:source IS NULL OR o.source = :source)
              AND (:orderNo IS NULL OR o.orderNo = :orderNo)
              AND (:requiredShipAfter IS NULL OR o.requestedShipDate >= :requiredShipAfter)
              AND (:requiredShipBefore IS NULL OR o.requestedShipDate <= :requiredShipBefore)
              AND (:createdAfter IS NULL OR o.createdAt >= :createdAfter)
              AND (:createdBefore IS NULL OR o.createdAt <= :createdBefore)
            ORDER BY o.updatedAt DESC
            """)
    List<OrderEntity> findFiltered(@Param("status") String status,
                                   @Param("warehouseId") UUID warehouseId,
                                   @Param("customerPartnerId") UUID customerPartnerId,
                                   @Param("source") String source,
                                   @Param("orderNo") String orderNo,
                                   @Param("requiredShipAfter") LocalDate requiredShipAfter,
                                   @Param("requiredShipBefore") LocalDate requiredShipBefore,
                                   @Param("createdAfter") Instant createdAfter,
                                   @Param("createdBefore") Instant createdBefore,
                                   Pageable pageable);

    @Query("""
            SELECT COUNT(o) FROM OrderEntity o
            WHERE (:status IS NULL OR o.status = :status)
              AND (:warehouseId IS NULL OR o.warehouseId = :warehouseId)
              AND (:customerPartnerId IS NULL OR o.customerPartnerId = :customerPartnerId)
              AND (:source IS NULL OR o.source = :source)
              AND (:orderNo IS NULL OR o.orderNo = :orderNo)
              AND (:requiredShipAfter IS NULL OR o.requestedShipDate >= :requiredShipAfter)
              AND (:requiredShipBefore IS NULL OR o.requestedShipDate <= :requiredShipBefore)
              AND (:createdAfter IS NULL OR o.createdAt >= :createdAfter)
              AND (:createdBefore IS NULL OR o.createdAt <= :createdBefore)
            """)
    long countFiltered(@Param("status") String status,
                       @Param("warehouseId") UUID warehouseId,
                       @Param("customerPartnerId") UUID customerPartnerId,
                       @Param("source") String source,
                       @Param("orderNo") String orderNo,
                       @Param("requiredShipAfter") LocalDate requiredShipAfter,
                       @Param("requiredShipBefore") LocalDate requiredShipBefore,
                       @Param("createdAfter") Instant createdAfter,
                       @Param("createdBefore") Instant createdBefore);
}
