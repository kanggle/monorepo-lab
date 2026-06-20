package com.example.settlement.application.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract test (AC-6): the {@code settlement.period.closed.v1} envelope + payload
 * serialize to the exact snake_case shape declared in
 * {@code specs/contracts/events/settlement-events.md} — {@code tenant_id} on the
 * envelope (and echoed in the payload), {@code payouts[]} = positive-payable sellers
 * only, {@code seller_count == payouts.length}.
 */
class SettlementPeriodClosedEventSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializes_to_snake_case_envelope_and_payload() throws Exception {
        SettlementPeriodClosedEvent.Payload payload = new SettlementPeriodClosedEvent.Payload(
                "period-1", "tenantA",
                "2026-06-01T00:00:00Z", "2026-07-01T00:00:00Z", "2026-07-01T09:00:00Z",
                2,
                List.of(
                        new SettlementPeriodClosedEvent.PayoutLine("seller-1", 27_000L, 3_000L, 1),
                        new SettlementPeriodClosedEvent.PayoutLine("seller-2", 18_000L, 2_000L, 2)));
        SettlementPeriodClosedEvent event = SettlementPeriodClosedEvent.of(
                "evt-1", "tenantA", java.time.Instant.parse("2026-07-01T09:00:00Z"), payload);

        JsonNode node = mapper.readTree(mapper.writeValueAsString(event));

        // Envelope (snake_case, tenant_id on the envelope).
        assertThat(node.get("event_id").asText()).isEqualTo("evt-1");
        assertThat(node.get("event_type").asText()).isEqualTo("settlement.period.closed.v1");
        assertThat(node.get("occurred_at").asText()).isEqualTo("2026-07-01T09:00:00Z");
        assertThat(node.get("source").asText()).isEqualTo("settlement-service");
        assertThat(node.get("tenant_id").asText()).isEqualTo("tenantA");

        // Payload.
        JsonNode p = node.get("payload");
        assertThat(p.get("period_id").asText()).isEqualTo("period-1");
        assertThat(p.get("tenant_id").asText()).isEqualTo("tenantA");
        assertThat(p.get("period_from").asText()).isEqualTo("2026-06-01T00:00:00Z");
        assertThat(p.get("period_to").asText()).isEqualTo("2026-07-01T00:00:00Z");
        assertThat(p.get("closed_at").asText()).isEqualTo("2026-07-01T09:00:00Z");
        assertThat(p.get("seller_count").asInt()).isEqualTo(2);

        JsonNode payouts = p.get("payouts");
        assertThat(payouts).hasSize(2);
        assertThat(payouts.get(0).get("seller_id").asText()).isEqualTo("seller-1");
        assertThat(payouts.get(0).get("payable_net_minor").asLong()).isEqualTo(27_000L);
        assertThat(payouts.get(0).get("commission_minor").asLong()).isEqualTo(3_000L);
        assertThat(payouts.get(0).get("accrual_count").asInt()).isEqualTo(1);

        // seller_count == payouts.length (decision 7 invariant).
        assertThat(p.get("seller_count").asInt()).isEqualTo(payouts.size());

        // No payout status / reference leaks into this event (execution is BE-416).
        assertThat(payouts.get(0).has("status")).isFalse();
        assertThat(payouts.get(0).has("payout_reference")).isFalse();
    }
}
