package com.wms.outbound.adapter.out.persistence.repository;

import com.wms.outbound.adapter.out.persistence.entity.OrderLineEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderLineRepository extends JpaRepository<OrderLineEntity, UUID> {

    List<OrderLineEntity> findByOrderIdOrderByLineNumberAsc(UUID orderId);

    long countByOrderId(UUID orderId);

    void deleteByOrderId(UUID orderId);
}
