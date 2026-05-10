package com.wms.notification.application.service;

import com.example.common.id.UuidV7;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.notification.application.port.inbound.ProcessInboundEventUseCase;
import com.wms.notification.application.port.outbound.AlertDedupePort;
import com.wms.notification.application.port.outbound.DeliveryRepository;
import com.wms.notification.application.port.outbound.OutboxPort;
import com.wms.notification.application.port.outbound.RoutingRuleRepository;
import com.wms.notification.domain.alert.AlertEnvelope;
import com.wms.notification.domain.delivery.DedupeOutcome;
import com.wms.notification.domain.delivery.NotificationDelivery;
import com.wms.notification.domain.error.RoutingAmbiguousException;
import com.wms.notification.domain.routing.ChannelTarget;
import com.wms.notification.domain.routing.RoutingRule;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Routing decision + dedupe row + delivery row(s) + outbox row in one TX
 * (architecture.md § Routing &amp; Delivery Pipeline).
 *
 * <h2>Transactional shape</h2>
 *
 * <p>Either every write commits or none does. If the dedupe insert wins
 * the race the caller can re-deliver; if it loses (DUPLICATE), this method
 * exits cleanly without any side effect — replay is safe.
 *
 * <h2>Channel dispatch</h2>
 *
 * <p>Channel calls happen post-commit via {@code DeliveryExecutor} — this
 * service does NOT touch the channel adapter. That guarantees a vendor
 * timeout cannot poison the ingest path (architecture.md § Why post-commit
 * delivery).
 */
@Service
public class AlertRoutingService implements ProcessInboundEventUseCase {

    private static final Logger log = LoggerFactory.getLogger(AlertRoutingService.class);

    private final RoutingRuleRepository routingRules;
    private final AlertDedupePort dedupe;
    private final DeliveryRepository deliveries;
    private final OutboxPort outbox;
    private final PostCommitDeliveryDispatcher postCommitDispatcher;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private final Counter classifiedQueued;
    private final Counter classifiedFiltered;
    private final Counter classifiedNoRule;
    private final Counter classifiedAmbiguous;
    private final Counter dedupeDuplicate;

    public AlertRoutingService(RoutingRuleRepository routingRules,
                               AlertDedupePort dedupe,
                               DeliveryRepository deliveries,
                               OutboxPort outbox,
                               PostCommitDeliveryDispatcher postCommitDispatcher,
                               ObjectMapper objectMapper,
                               Clock clock,
                               MeterRegistry meters) {
        this.routingRules = routingRules;
        this.dedupe = dedupe;
        this.deliveries = deliveries;
        this.outbox = outbox;
        this.postCommitDispatcher = postCommitDispatcher;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.classifiedQueued = meters.counter("notification.routing.classified", "outcome", "QUEUED");
        this.classifiedFiltered = meters.counter("notification.routing.classified", "outcome", "FILTERED");
        this.classifiedNoRule = meters.counter("notification.routing.classified", "outcome", "NO_RULE");
        this.classifiedAmbiguous = meters.counter("notification.routing.classified", "outcome", "AMBIGUOUS");
        this.dedupeDuplicate = meters.counter("notification.routing.dedupe", "outcome", "DUPLICATE");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Outcome process(AlertEnvelope envelope) {
        // Phase 1: dedupe fast-exit
        if (dedupe.exists(envelope.eventId())) {
            dedupeDuplicate.increment();
            log.debug("eventId={} already processed; skipping", envelope.eventId());
            return Outcome.DUPLICATE;
        }

        // Phase 2: resolve routing rule
        RoutingRule rule = findUniqueRule(envelope);
        if (rule == null) {
            return Outcome.NO_RULE;
        }

        // Phase 3: matcher predicate evaluation
        if (!rule.matches(envelope)) {
            // Best-effort dedupe write; DUPLICATE is a no-op for observability.
            dedupe.recordIfAbsent(envelope.eventId(), envelope.sourceTopic(), DedupeOutcome.FILTERED);
            classifiedFiltered.increment();
            log.debug("eventType={} matched rule but matcher predicate filtered eventId={}",
                    envelope.eventType(), envelope.eventId());
            return Outcome.FILTERED;
        }

        // Phase 4: record QUEUED dedupe
        if (!recordQueuedDedupe(envelope)) {
            return Outcome.DUPLICATE;
        }

        // Phase 5: enqueue deliveries for matched rule
        enqueueDeliveriesForRule(rule, envelope);
        classifiedQueued.increment();
        return Outcome.QUEUED;
    }

    /**
     * Phase 2 — resolves exactly one enabled rule for the event type.
     * Returns {@code null} when no rule exists (NO_RULE path).
     * Throws {@link RoutingAmbiguousException} when multiple rules collide
     * (bad manual DB edit; sends to DLT).
     */
    private RoutingRule findUniqueRule(AlertEnvelope envelope) {
        List<RoutingRule> matchingType = routingRules.findEnabledByEventType(envelope.eventType());
        if (matchingType.isEmpty()) {
            // Best-effort dedupe write; DUPLICATE is a no-op for observability.
            dedupe.recordIfAbsent(envelope.eventId(), envelope.sourceTopic(), DedupeOutcome.NO_RULE);
            classifiedNoRule.increment();
            return null;
        }
        if (matchingType.size() > 1) {
            classifiedAmbiguous.increment();
            // Storage UNIQUE partial index normally prevents this; surfaces
            // only on bad manual DB edit. Record the dedupe row with
            // outcome=ERROR so the diagnostic is visible in the cleanup
            // sweeper, then re-throw so the consumer's DLT path fires.
            dedupe.recordIfAbsent(envelope.eventId(), envelope.sourceTopic(), DedupeOutcome.ERROR);
            throw new RoutingAmbiguousException(envelope.eventType(),
                    matchingType.stream().map(RoutingRule::id).toList());
        }
        return matchingType.get(0);
    }

    /**
     * Phase 4 — inserts the QUEUED dedupe row.
     * Returns {@code false} when a concurrent insert already committed the row
     * (DUPLICATE race); the caller exits with {@link Outcome#DUPLICATE}.
     */
    private boolean recordQueuedDedupe(AlertEnvelope envelope) {
        AlertDedupePort.Result dedupeResult =
                dedupe.recordIfAbsent(envelope.eventId(), envelope.sourceTopic(), DedupeOutcome.QUEUED);
        if (dedupeResult == AlertDedupePort.Result.DUPLICATE) {
            dedupeDuplicate.increment();
            return false;
        }
        return true;
    }

    /**
     * Phase 5 — creates one {@link NotificationDelivery} per channel target,
     * persists each, appends the outbox row, and schedules post-commit dispatch.
     */
    private void enqueueDeliveriesForRule(RoutingRule rule, AlertEnvelope envelope) {
        Instant now = clock.instant();
        String payloadSnapshot = serialise(envelope);

        for (ChannelTarget target : rule.channelTargets()) {
            String idempotencyKey = IdempotencyKeys.forDelivery(
                    envelope.eventId(), target.channelId(), target.channelId());
            NotificationDelivery delivery = NotificationDelivery.createPending(
                    UuidV7.randomUuid(),
                    envelope.eventId(),
                    envelope.sourceTopic(),
                    target.channelId(),
                    target.channelId(),
                    idempotencyKey,
                    payloadSnapshot,
                    now);
            deliveries.save(delivery);
            outbox.writeDeliveryScheduled(delivery);
            // Post-commit dispatch — vendor 5xx cannot poison the ingest TX.
            postCommitDispatcher.scheduleAfterCommit(delivery);
        }
    }

    private String serialise(AlertEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise envelope eventId=" + envelope.eventId(), e);
        }
    }
}
