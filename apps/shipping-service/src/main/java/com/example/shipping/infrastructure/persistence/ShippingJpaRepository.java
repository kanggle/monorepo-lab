package com.example.shipping.infrastructure.persistence;

import com.example.shipping.domain.model.ShippingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShippingJpaRepository extends JpaRepository<ShippingJpaEntity, String> {

    Optional<ShippingJpaEntity> findByOrderId(String orderId);

    boolean existsByOrderId(String orderId);

    Page<ShippingJpaEntity> findByStatus(ShippingStatus status, Pageable pageable);
}
