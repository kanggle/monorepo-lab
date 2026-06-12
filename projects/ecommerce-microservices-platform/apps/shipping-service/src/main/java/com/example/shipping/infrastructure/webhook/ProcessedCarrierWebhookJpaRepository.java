package com.example.shipping.infrastructure.webhook;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedCarrierWebhookJpaRepository
        extends JpaRepository<ProcessedCarrierWebhookJpaEntity, String> {
}
