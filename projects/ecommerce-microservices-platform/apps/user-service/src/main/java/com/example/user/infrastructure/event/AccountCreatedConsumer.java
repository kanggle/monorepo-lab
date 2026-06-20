package com.example.user.infrastructure.event;

import com.example.user.application.service.AccountCreatedHandler;
import com.example.user.domain.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumes IAM {@code account.created} → creates a minimal ecommerce profile
 * (ADR-MONO-037 P1). Replaces the dead-topic {@code UserSignedUpConsumer}
 * ({@code auth.user.signed-up}, retired with the decommissioned ecommerce
 * auth-service, TASK-BE-132).
 *
 * <p>The wire is FLAT (TASK-BE-422): fields are read from the JSON root, not a nested
 * {@code payload}. Fail-soft (ADR-MONO-037 P5): a missing {@code accountId} is logged
 * and skipped rather than blocking the partition. Idempotency is handled downstream by
 * {@link AccountCreatedHandler} (existsByUserId).
 */
@Slf4j
@Component
@Profile("!standalone")
@RequiredArgsConstructor
public class AccountCreatedConsumer {

    private final AccountCreatedHandler accountCreatedHandler;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "account.created", groupId = "user-service")
    public void onMessage(@Payload String payload) {
        AccountCreatedEvent event = deserialize(payload);
        handle(event);
    }

    private AccountCreatedEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, AccountCreatedEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize account.created event", e);
        }
    }

    void handle(AccountCreatedEvent event) {
        if (event.accountId() == null) {
            log.warn("account.created event missing accountId, skipping.");
            return;
        }

        // IAM carries the tenant at the top level (account-events.md, flat wire);
        // a null tenant → TenantContext resolves the default tenant (M5/D8 net-zero).
        String tenantId = event.tenantId();
        try {
            TenantContext.set(tenantId);
            accountCreatedHandler.handle(event.accountId());
        } finally {
            TenantContext.clear();
        }
    }
}
