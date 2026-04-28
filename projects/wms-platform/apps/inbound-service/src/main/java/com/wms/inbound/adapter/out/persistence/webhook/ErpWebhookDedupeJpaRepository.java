package com.wms.inbound.adapter.out.persistence.webhook;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ErpWebhookDedupeJpaRepository extends JpaRepository<ErpWebhookDedupeJpaEntity, String> {
}
