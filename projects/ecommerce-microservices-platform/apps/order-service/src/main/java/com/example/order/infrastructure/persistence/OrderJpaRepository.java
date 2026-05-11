package com.example.order.infrastructure.persistence;

import com.example.order.domain.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, String> {

    Page<OrderJpaEntity> findByUserId(String userId, Pageable pageable);

    Page<OrderJpaEntity> findByUserIdAndStatus(String userId, OrderStatus status, Pageable pageable);

    List<OrderJpaEntity> findByUserIdAndStatusIn(String userId, Collection<OrderStatus> statuses);

    Page<OrderJpaEntity> findByStatus(OrderStatus status, Pageable pageable);

    @Query(value = "SELECT o FROM OrderJpaEntity o LEFT JOIN FETCH o.items WHERE o.status = :status",
           countQuery = "SELECT COUNT(o) FROM OrderJpaEntity o WHERE o.status = :status")
    Page<OrderJpaEntity> findByStatusWithItems(@Param("status") OrderStatus status, Pageable pageable);

    @Query(value = "SELECT o FROM OrderJpaEntity o LEFT JOIN FETCH o.items",
           countQuery = "SELECT COUNT(o) FROM OrderJpaEntity o")
    Page<OrderJpaEntity> findAllWithItems(Pageable pageable);

    @Query("SELECT COUNT(o) > 0 FROM OrderJpaEntity o JOIN o.items i " +
           "WHERE o.userId = :userId AND i.productId = :productId AND o.status = :status")
    boolean existsByUserIdAndProductIdAndStatus(
            @Param("userId") String userId,
            @Param("productId") String productId,
            @Param("status") OrderStatus status);

    @Query("SELECT o FROM OrderJpaEntity o " +
           "WHERE o.status = :status AND o.paymentId IS NULL AND o.createdAt < :placedBefore " +
           "ORDER BY o.createdAt ASC")
    List<OrderJpaEntity> findStuckPaymentPending(
            @Param("status") OrderStatus status,
            @Param("placedBefore") Instant placedBefore,
            Pageable pageable);
}
