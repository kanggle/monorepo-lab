package com.example.settlement.application.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * The {@code settlement.period.closed.v1} producer event (settlement-events.md).
 * snake_case ecommerce envelope (matches order/payment-service) with {@code tenant_id}
 * on the <b>envelope</b> (and echoed in the payload). Emitted once per successful
 * close via the transactional outbox.
 *
 * <p>{@code payouts[]} contains one entry per seller with a <b>positive</b>
 * {@code payable_net_minor} (net-zero sellers are skipped, decision 7) — so
 * {@code seller_count == payouts.size()}. Money fields are minor units (KRW). The
 * payload reflects the <b>PENDING</b> payout rows at close time (no payout status /
 * reference here — execution is TASK-BE-416).
 */
public record SettlementPeriodClosedEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") String occurredAt,
        String source,
        @JsonProperty("tenant_id") String tenantId,
        Payload payload
) {

    public static final String EVENT_TYPE = "settlement.period.closed.v1";
    public static final String SOURCE = "settlement-service";

    public static SettlementPeriodClosedEvent of(String eventId, String tenantId,
                                                 Instant occurredAt, Payload payload) {
        return new SettlementPeriodClosedEvent(
                eventId, EVENT_TYPE, occurredAt.toString(), SOURCE, tenantId, payload);
    }

    public record Payload(
            @JsonProperty("period_id") String periodId,
            @JsonProperty("tenant_id") String tenantId,
            @JsonProperty("period_from") String periodFrom,
            @JsonProperty("period_to") String periodTo,
            @JsonProperty("closed_at") String closedAt,
            @JsonProperty("seller_count") int sellerCount,
            List<PayoutLine> payouts
    ) {
    }

    public record PayoutLine(
            @JsonProperty("seller_id") String sellerId,
            @JsonProperty("payable_net_minor") long payableNetMinor,
            @JsonProperty("commission_minor") long commissionMinor,
            @JsonProperty("accrual_count") int accrualCount
    ) {
    }
}
