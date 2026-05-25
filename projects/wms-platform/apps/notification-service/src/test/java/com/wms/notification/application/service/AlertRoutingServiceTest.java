package com.wms.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wms.notification.application.port.in.ProcessInboundEventUseCase;
import com.wms.notification.application.service.fakes.InMemoryAlertDedupePort;
import com.wms.notification.application.service.fakes.InMemoryDeliveryRepository;
import com.wms.notification.application.service.fakes.InMemoryRoutingRuleRepository;
import com.wms.notification.application.service.fakes.RecordingOutboxPort;
import com.wms.notification.domain.alert.AlertEnvelope;
import com.wms.notification.domain.alert.AlertSeverity;
import com.wms.notification.domain.delivery.DedupeOutcome;
import com.wms.notification.domain.delivery.DeliveryStatus;
import com.wms.notification.domain.error.RoutingAmbiguousException;
import com.wms.notification.domain.routing.ChannelTarget;
import com.wms.notification.domain.routing.ChannelType;
import com.wms.notification.domain.routing.RoutingMatcher;
import com.wms.notification.domain.routing.RoutingRule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * AlertRoutingService unit tests covering the 6 source-topic flows + dedupe
 * + ambiguous + no-rule + filtered cases. The {@link PostCommitDeliveryDispatcher}
 * is mocked to a no-op so we exercise only the routing/dedupe/persistence
 * choreography.
 */
class AlertRoutingServiceTest {

    private static final Instant NOW = Instant.parse("2025-01-01T00:00:00Z");

    private InMemoryRoutingRuleRepository routingRules;
    private InMemoryAlertDedupePort dedupe;
    private InMemoryDeliveryRepository deliveries;
    private RecordingOutboxPort outbox;
    private AlertRoutingService service;

    @BeforeEach
    void setUp() {
        routingRules = new InMemoryRoutingRuleRepository();
        dedupe = new InMemoryAlertDedupePort();
        deliveries = new InMemoryDeliveryRepository();
        outbox = new RecordingOutboxPort();
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Clock fixed = Clock.fixed(NOW, ZoneId.of("UTC"));
        service = new AlertRoutingService(
                routingRules, dedupe, deliveries, outbox,
                mock(PostCommitDeliveryDispatcher.class),
                mapper, fixed,
                new SimpleMeterRegistry());
    }

    @Test
    void inventoryAlertHappyPath_writesDedupeAndDeliveryAndOutbox() {
        seed("inventory.low-stock-detected", new RoutingMatcher.AlwaysMatch(), "wms-alerts");
        AlertEnvelope env = envelope("inventory.low-stock-detected",
                "wms.inventory.alert.v1", Map.of("warehouseId", "WH-1"));

        ProcessInboundEventUseCase.Outcome outcome = service.process(env);

        assertThat(outcome).isEqualTo(ProcessInboundEventUseCase.Outcome.QUEUED);
        assertThat(dedupe.rows).containsEntry(env.eventId(), DedupeOutcome.QUEUED);
        assertThat(deliveries.byId).hasSize(1);
        assertThat(deliveries.byId.values().iterator().next().status())
                .isEqualTo(DeliveryStatus.PENDING);
        assertThat(outbox.rows).hasSize(1);
        assertThat(outbox.rows.get(0).eventType()).isEqualTo("notification.delivery.scheduled");
    }

    @Test
    void inventoryAdjustedDeltaUnderThreshold_filtersWithoutDelivery() {
        seed("inventory.adjusted",
                new RoutingMatcher.PayloadPredicateMatch("$.payload.delta", RoutingMatcher.Op.ABS_GTE, 100),
                "wms-alerts");
        AlertEnvelope env = envelope("inventory.adjusted",
                "wms.inventory.adjusted.v1", Map.of("delta", 50));

        ProcessInboundEventUseCase.Outcome outcome = service.process(env);

        assertThat(outcome).isEqualTo(ProcessInboundEventUseCase.Outcome.FILTERED);
        assertThat(dedupe.rows).containsEntry(env.eventId(), DedupeOutcome.FILTERED);
        assertThat(deliveries.byId).isEmpty();
        assertThat(outbox.rows).isEmpty();
    }

    @Test
    void inventoryAdjustedDeltaOverThreshold_queues() {
        seed("inventory.adjusted",
                new RoutingMatcher.PayloadPredicateMatch("$.payload.delta", RoutingMatcher.Op.ABS_GTE, 100),
                "wms-alerts");
        AlertEnvelope env = envelope("inventory.adjusted",
                "wms.inventory.adjusted.v1", Map.of("delta", 250));

        assertThat(service.process(env))
                .isEqualTo(ProcessInboundEventUseCase.Outcome.QUEUED);
        assertThat(deliveries.byId).hasSize(1);
    }

    @Test
    void inboundInspectionWithDiscrepancy_queues() {
        seed("inbound.inspection.completed",
                new RoutingMatcher.PayloadPredicateMatch(
                        "$.payload.discrepancyCount", RoutingMatcher.Op.GT, 0),
                "wms-alerts");
        AlertEnvelope env = envelope("inbound.inspection.completed",
                "wms.inbound.inspection.completed.v1", Map.of("discrepancyCount", 3));

        assertThat(service.process(env))
                .isEqualTo(ProcessInboundEventUseCase.Outcome.QUEUED);
    }

    @Test
    void inboundInspectionWithoutDiscrepancy_filtered() {
        seed("inbound.inspection.completed",
                new RoutingMatcher.PayloadPredicateMatch(
                        "$.payload.discrepancyCount", RoutingMatcher.Op.GT, 0),
                "wms-alerts");
        AlertEnvelope env = envelope("inbound.inspection.completed",
                "wms.inbound.inspection.completed.v1", Map.of("discrepancyCount", 0));

        assertThat(service.process(env))
                .isEqualTo(ProcessInboundEventUseCase.Outcome.FILTERED);
    }

    @Test
    void inboundAsnCancelledAlwaysQueues() {
        seed("inbound.asn.cancelled", new RoutingMatcher.AlwaysMatch(), "wms-alerts");
        AlertEnvelope env = envelope("inbound.asn.cancelled",
                "wms.inbound.asn.cancelled.v1", Map.of());
        assertThat(service.process(env))
                .isEqualTo(ProcessInboundEventUseCase.Outcome.QUEUED);
    }

    @Test
    void outboundOrderCancelledPostPick_queues() {
        seed("outbound.order.cancelled",
                new RoutingMatcher.PayloadPredicateMatch(
                        "$.payload.priorStatus", RoutingMatcher.Op.IN,
                        List.of("PICKED", "PACKED", "SHIPPED")),
                "wms-alerts");
        AlertEnvelope env = envelope("outbound.order.cancelled",
                "wms.outbound.order.cancelled.v1", Map.of("priorStatus", "PACKED"));
        assertThat(service.process(env))
                .isEqualTo(ProcessInboundEventUseCase.Outcome.QUEUED);
    }

    @Test
    void outboundOrderCancelledPrePick_filtered() {
        seed("outbound.order.cancelled",
                new RoutingMatcher.PayloadPredicateMatch(
                        "$.payload.priorStatus", RoutingMatcher.Op.IN,
                        List.of("PICKED", "PACKED", "SHIPPED")),
                "wms-alerts");
        AlertEnvelope env = envelope("outbound.order.cancelled",
                "wms.outbound.order.cancelled.v1", Map.of("priorStatus", "RECEIVED"));
        assertThat(service.process(env))
                .isEqualTo(ProcessInboundEventUseCase.Outcome.FILTERED);
    }

    @Test
    void outboundShippingConfirmedAlwaysQueuesToShippingChannel() {
        seed("outbound.shipping.confirmed", new RoutingMatcher.AlwaysMatch(), "wms-shipping");
        AlertEnvelope env = envelope("outbound.shipping.confirmed",
                "wms.outbound.shipping.confirmed.v1", Map.of("orderId", "O-1"));
        assertThat(service.process(env))
                .isEqualTo(ProcessInboundEventUseCase.Outcome.QUEUED);
        assertThat(deliveries.byId.values().iterator().next().channelId()).isEqualTo("wms-shipping");
    }

    @Test
    void noEnabledRule_recordsNoRuleAndExits() {
        AlertEnvelope env = envelope("inventory.unknown",
                "wms.inventory.alert.v1", Map.of());
        assertThat(service.process(env))
                .isEqualTo(ProcessInboundEventUseCase.Outcome.NO_RULE);
        assertThat(dedupe.rows).containsEntry(env.eventId(), DedupeOutcome.NO_RULE);
        assertThat(deliveries.byId).isEmpty();
    }

    @Test
    void duplicateEventId_skipsSilently() {
        seed("inventory.low-stock-detected", new RoutingMatcher.AlwaysMatch(), "wms-alerts");
        AlertEnvelope env = envelope("inventory.low-stock-detected",
                "wms.inventory.alert.v1", Map.of());
        // Pre-populate the dedupe table (simulates a prior successful delivery).
        dedupe.rows.put(env.eventId(), DedupeOutcome.QUEUED);

        assertThat(service.process(env))
                .isEqualTo(ProcessInboundEventUseCase.Outcome.DUPLICATE);
        assertThat(deliveries.byId).isEmpty();
        assertThat(outbox.rows).isEmpty();
    }

    @Test
    void ambiguousRules_recordErrorDedupeAndThrow() {
        // Two enabled rules for the same eventType — should never happen
        // under the partial UNIQUE index, but the application service
        // defensively rejects.
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        routingRules.add(new RoutingRule(id1, "x", new RoutingMatcher.AlwaysMatch(),
                List.of(new ChannelTarget(ChannelType.SLACK, "wms-alerts", "t")),
                AlertSeverity.INFO, true, NOW, NOW));
        routingRules.add(new RoutingRule(id2, "x", new RoutingMatcher.AlwaysMatch(),
                List.of(new ChannelTarget(ChannelType.SLACK, "wms-shipping", "t")),
                AlertSeverity.INFO, true, NOW, NOW));

        AlertEnvelope env = envelope("x", "wms.x.v1", Map.of());
        assertThatThrownBy(() -> service.process(env))
                .isInstanceOf(RoutingAmbiguousException.class);
        assertThat(dedupe.rows).containsEntry(env.eventId(), DedupeOutcome.ERROR);
    }

    private void seed(String eventType, RoutingMatcher matcher, String channelAlias) {
        routingRules.add(new RoutingRule(
                UUID.randomUUID(), eventType, matcher,
                List.of(new ChannelTarget(ChannelType.SLACK, channelAlias, "template")),
                AlertSeverity.WARNING, true, NOW, NOW));
    }

    private static AlertEnvelope envelope(String type, String topic, Map<String, Object> payload) {
        return new AlertEnvelope(UUID.randomUUID(), type, topic, "agg", payload, NOW);
    }
}
