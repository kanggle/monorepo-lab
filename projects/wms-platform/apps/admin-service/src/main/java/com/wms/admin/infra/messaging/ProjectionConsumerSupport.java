package com.wms.admin.infra.messaging;

import com.wms.admin.application.projection.ProjectionEnvelope;
import com.wms.admin.application.projection.ProjectionEnvelopeParser;
import com.wms.admin.infra.observability.ProjectionMetrics;
import java.util.function.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;

/**
 * Static dispatch utility for the 4 admin {@code *ProjectionConsumer}s
 * (Inventory / Inbound / Master / Outbound). Eliminates the byte-identical
 * 16-line {@code onMessage} body (parse → MDC put → projection-fn → MDC remove
 * → metrics.recordError on failure) duplicated across all four consumers
 * (TASK-BE-300 Cohort C2 closure).
 *
 * <p><b>Spring AOP self-invocation 회피</b> — this is intentionally a static
 * utility (no Spring bean, no instance) so that {@code projectionService.project}
 * is called via the caller's injected proxy reference (method reference
 * {@code projectionService::project}), preserving the {@code @Transactional}
 * proxy that the projection service relies on. An {@code AbstractProjectionService}
 * superclass would have invoked {@code project} via {@code this.project(...)}
 * inside the bean, bypassing the proxy — see
 * {@code .claude/skills/backend/refactoring/SKILL.md} for the cautionary
 * precedent.
 */
public final class ProjectionConsumerSupport {

    private ProjectionConsumerSupport() {
        // utility class — no instances
    }

    /**
     * Dispatches a Kafka {@code ConsumerRecord} through the standard projection
     * pipeline: parse → MDC enrichment → projection invocation → MDC cleanup,
     * with error-path metrics recording.
     *
     * @param record       the inbound Kafka record (raw envelope string)
     * @param parser       parses {@code record.value()} into a {@link ProjectionEnvelope}
     * @param metrics      receives error notifications via {@link ProjectionMetrics#recordError(String)}
     * @param projectionFn the projection invocation, typically
     *                     {@code projectionService::project} (method reference
     *                     resolves through the Spring proxy, preserving
     *                     {@code @Transactional} semantics)
     */
    public static void dispatch(ConsumerRecord<String, String> record,
                                ProjectionEnvelopeParser parser,
                                ProjectionMetrics metrics,
                                Consumer<ProjectionEnvelope> projectionFn) {
        String topic = record.topic();
        try {
            ProjectionEnvelope envelope = parser.parse(record.value(), topic);
            MDC.put("eventId", envelope.eventId().toString());
            MDC.put("sourceTopic", topic);
            try {
                projectionFn.accept(envelope);
            } finally {
                MDC.remove("eventId");
                MDC.remove("sourceTopic");
            }
        } catch (RuntimeException ex) {
            metrics.recordError(topic);
            throw ex;
        }
    }
}
