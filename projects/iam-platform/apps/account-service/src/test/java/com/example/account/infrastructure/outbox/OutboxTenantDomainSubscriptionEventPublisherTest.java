package com.example.account.infrastructure.outbox;

import com.example.account.infrastructure.persistence.AccountOutboxJpaEntity;
import com.example.account.infrastructure.persistence.AccountOutboxJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

/**
 * Unit test for {@link OutboxTenantDomainSubscriptionEventPublisher} (TASK-BE-451).
 * The v1 method self-built a FLAT payload carrying its own top-level {@code eventId}
 * via {@code BaseEventPublisher.saveEvent} (no canonical envelope wrapper). The v2
 * adapter reproduces the EXACT flat payload and reuses its {@code eventId} as the
 * {@code account_outbox} row PK. Writes into the SAME table as account.* events.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class OutboxTenantDomainSubscriptionEventPublisherTest {

    @Mock
    private AccountOutboxJpaRepository outboxRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OutboxTenantDomainSubscriptionEventPublisher publisher() {
        return new OutboxTenantDomainSubscriptionEventPublisher(outboxRepository, objectMapper,
                Clock.fixed(Instant.parse("2026-04-14T10:00:00Z"), ZoneOffset.UTC));
    }

    private AccountOutboxJpaEntity captureRow() {
        ArgumentCaptor<AccountOutboxJpaEntity> captor = ArgumentCaptor.forClass(AccountOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("publishSubscriptionChanged — flat payload; aggregate id = tenantId:domainKey; row PK reuses eventId")
    void publishSubscriptionChanged_flatWire() throws Exception {
        publisher().publishSubscriptionChanged(
                "acme-corp", "ecommerce", "ACTIVE", "SUSPENDED",
                "billing-overdue", "operator", "op-1", Instant.parse("2026-04-14T10:00:00Z"));

        AccountOutboxJpaEntity row = captureRow();
        assertThat(row.getAggregateType()).isEqualTo("TenantDomainSubscription");
        assertThat(row.getAggregateId()).isEqualTo("acme-corp:ecommerce");
        assertThat(row.getPartitionKey()).isEqualTo("acme-corp:ecommerce");
        assertThat(row.getEventType()).isEqualTo("tenant.subscription.changed");

        JsonNode wire = objectMapper.readTree(row.getPayload());
        // FLAT — top-level subscription fields, no envelope wrapper.
        assertThat(wire.has("payload")).isFalse();
        assertThat(wire.has("source")).isFalse();
        assertThat(wire.get("tenantId").asText()).isEqualTo("acme-corp");
        assertThat(wire.get("domainKey").asText()).isEqualTo("ecommerce");
        assertThat(wire.get("previousStatus").asText()).isEqualTo("ACTIVE");
        assertThat(wire.get("currentStatus").asText()).isEqualTo("SUSPENDED");
        assertThat(wire.get("actorType").asText()).isEqualTo("operator");
        assertThat(wire.get("actorId").asText()).isEqualTo("op-1");
        assertThat(wire.get("reason").asText()).isEqualTo("billing-overdue");

        // Row PK == the flat payload's own eventId.
        assertThat(row.getId()).isEqualTo(UUID.fromString(wire.get("eventId").asText()));
    }

    @Test
    @DisplayName("create (previousStatus null, no actorId/reason) — nulls/omissions preserved; actorType defaults to operator")
    void publishSubscriptionChanged_createDefaults() throws Exception {
        publisher().publishSubscriptionChanged(
                "acme-corp", "wms", null, "ACTIVE",
                null, null, null, Instant.parse("2026-04-14T10:00:00Z"));

        JsonNode wire = objectMapper.readTree(captureRow().getPayload());
        assertThat(wire.get("previousStatus").isNull()).isTrue();
        assertThat(wire.get("currentStatus").asText()).isEqualTo("ACTIVE");
        assertThat(wire.get("actorType").asText()).isEqualTo("operator");
        assertThat(wire.has("actorId")).isFalse();
        assertThat(wire.has("reason")).isFalse();
    }

    @Test
    @DisplayName("blank tenantId/domainKey → IllegalArgumentException")
    void blank_throws() {
        assertThatThrownBy(() -> publisher().publishSubscriptionChanged(
                "", "ecommerce", null, "ACTIVE", null, null, null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tenantId and domainKey required");
        assertThatThrownBy(() -> publisher().publishSubscriptionChanged(
                "acme-corp", " ", null, "ACTIVE", null, null, null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tenantId and domainKey required");
    }
}
