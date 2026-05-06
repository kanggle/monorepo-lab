package com.example.scmplatform.procurement.infrastructure.persistence.jpa;

import com.example.scmplatform.procurement.domain.po.PurchaseOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseOrderLineJpaRepository extends JpaRepository<PurchaseOrderLine, String> {

    List<PurchaseOrderLine> findByPoIdOrderByLineNoAsc(String poId);
}
