package com.example.platform.notification.consumer;

import com.example.messaging.dedupe.EventDedupePort;
import com.example.messaging.envelope.EventEnvelope;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Small, <b>optional</b> composition helper for the notification consumer leg
 * (D4): it validates the required fields of an inbound {@link EventEnvelope} and
 * runs the handler work exactly-once via the injected {@link EventDedupePort}.
 *
 * <p>This is deliberately not a mandatory base class (ADR-MONO-038 / ADR-MONO-043
 * D4 composition-over-inheritance): a service may inject {@link EventDedupePort}
 * directly and skip this helper. It exists only to remove the repeated
 * "null-check the envelope fields, then {@code dedupe.process(eventId, type, work)}"
 * boilerplate every per-domain consumer otherwise re-derives.
 *
 * <p>Project-agnostic (HARDSTOP-03): no topics, no domain event types, no recipient
 * resolution — the caller supplies the work that interprets the payload.
 */
public final class NotificationConsumerSupport {

    private final EventDedupePort dedupe;

    public NotificationConsumerSupport(EventDedupePort dedupe) {
        this.dedupe = Objects.requireNonNull(dedupe, "dedupe");
    }

    /**
     * Validate the envelope's required dedupe/identity fields and run {@code work}
     * exactly-once for the envelope's {@code eventId}.
     *
     * <p>The envelope is already parsed by {@code EventEnvelopeParser} (which rejects
     * malformed wire input); this method adds the consumer-side precondition that
     * {@code eventId}/{@code eventType} are present before handing them to the dedupe
     * port.
     *
     * @param envelope the parsed inbound envelope
     * @param work     side-effecting handler run on first occurrence of {@code eventId}
     * @return {@link EventDedupePort.Outcome#APPLIED} on first occurrence,
     *         {@link EventDedupePort.Outcome#IGNORED_DUPLICATE} on a replay
     * @throws IllegalArgumentException if the envelope is missing {@code eventId}/{@code eventType}
     */
    public EventDedupePort.Outcome handle(EventEnvelope envelope, Consumer<EventEnvelope> work) {
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(work, "work");
        if (envelope.eventId() == null) {
            throw new IllegalArgumentException("envelope missing eventId");
        }
        if (envelope.eventType() == null || envelope.eventType().isBlank()) {
            throw new IllegalArgumentException("envelope missing eventType");
        }
        return dedupe.process(
                envelope.eventId(),
                envelope.eventType(),
                () -> work.accept(envelope));
    }
}
