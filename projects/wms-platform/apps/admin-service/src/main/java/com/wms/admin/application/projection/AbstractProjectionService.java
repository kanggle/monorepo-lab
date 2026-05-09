package com.wms.admin.application.projection;

import com.wms.admin.application.repository.AdminEventDedupeRepository;
import com.wms.admin.infra.observability.ProjectionMetrics;
import org.springframework.transaction.annotation.Transactional;

/**
 * Template-method base for the four read-side projection services
 * (master / inbound / inventory / outbound).
 *
 * <p>Centralises the per-event boilerplate that was previously byte-identical
 * across all four classes:
 *
 * <ol>
 *   <li>Insert the eventId into {@code admin_event_dedupe} via
 *       {@link AdminEventDedupeRepository#tryRecord} — duplicate insert →
 *       record metric and short-circuit (Kafka redelivery).</li>
 *   <li>Delegate to the concrete {@link #dispatch} for event-type-specific
 *       projection logic.</li>
 *   <li>If the dispatch reports a stale event ({@code IGNORED_DUPLICATE_LATE}),
 *       update the dedupe row outcome and emit the {@code stale} drop metric.
 *       Otherwise, record projection lag against the source-topic.</li>
 * </ol>
 *
 * <p>Concrete subclasses supply:
 *
 * <ul>
 *   <li>{@link #sourceService()} — short label for the {@code projection.lag}
 *       metric tag (e.g. {@code "master"}, {@code "inventory"}).</li>
 *   <li>{@link #dispatch(ProjectionEnvelope)} — the event-type switch and the
 *       per-aggregate upsert calls.</li>
 * </ul>
 *
 * <p>This class is package-private and not annotated with {@code @Service} on
 * purpose: only the concrete subclasses are Spring-managed beans.
 */
abstract class AbstractProjectionService {

    private final AdminEventDedupeRepository dedupe;
    private final ProjectionMetrics metrics;

    protected AbstractProjectionService(AdminEventDedupeRepository dedupe,
                                        ProjectionMetrics metrics) {
        this.dedupe = dedupe;
        this.metrics = metrics;
    }

    @Transactional
    public final DedupeOutcome project(ProjectionEnvelope envelope) {
        DedupeOutcome outcome = dedupe.tryRecord(envelope.eventId(), envelope.eventType());
        if (outcome == DedupeOutcome.DUPLICATE) {
            metrics.recordDropped("duplicate");
            return outcome;
        }

        DedupeOutcome applied = dispatch(envelope);
        if (applied == DedupeOutcome.IGNORED_DUPLICATE_LATE) {
            dedupe.markStale(envelope.eventId());
            metrics.recordDropped("stale");
        } else {
            metrics.recordLag(sourceService(), envelope.sourceTopic(), envelope.occurredAt());
        }
        return applied;
    }

    /**
     * Short identifier for the source service (e.g. {@code "master"},
     * {@code "inbound"}). Tagged on the {@code projection.lag} metric so
     * dashboards can break down lag per source.
     */
    protected abstract String sourceService();

    /**
     * Event-type-specific projection logic. Implementations switch on
     * {@link ProjectionEnvelope#eventType()} and apply the corresponding
     * read-model upsert(s). Must return:
     *
     * <ul>
     *   <li>{@link DedupeOutcome#APPLIED} when at least one row was upserted
     *       (or the event is intentionally ignored at the read-model layer
     *       but should still mark the dedupe row as processed).</li>
     *   <li>{@link DedupeOutcome#IGNORED_DUPLICATE_LATE} when the LWW guard
     *       rejected every upsert (existing rows newer than the event).</li>
     * </ul>
     */
    protected abstract DedupeOutcome dispatch(ProjectionEnvelope envelope);
}
