package com.wms.outbound.adapter.out.persistence.repository;

import com.wms.outbound.adapter.out.persistence.entity.ErpOrderWebhookDedupe;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErpOrderWebhookDedupeRepository extends JpaRepository<ErpOrderWebhookDedupe, String> {
}
