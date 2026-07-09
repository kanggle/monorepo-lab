package com.wms.inventory.adapter.in.messaging.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.inventory.application.port.out.EventDedupePort;
import com.wms.inventory.application.port.out.LowStockThresholdWriterPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@code wms.admin.settings.v1} ({@code admin.settings.changed},
 * published by admin-service) and applies the low-stock <em>default</em>
 * threshold live — TASK-BE-459 (Option B). Operators can change
 * {@code inventory.low_stock.default_threshold_qty} in the admin console and it
 * reaches low-stock detection without an inventory-service redeploy.
 *
 * <p><b>Option B (Kafka + config-default bootstrap)</b>: this consumer only
 * pushes <em>changes</em>; the initial value at startup is the
 * {@code inventory.alert.low-stock.default-threshold} config default (see
 * {@code AlertConfig}). Restart-durability of an operator-set value is deferred
 * — that needs a startup HTTP read, and WMS has no service-to-service HTTP auth
 * mechanism yet (documented-but-unbuilt {@code wms-internal-services-client}).
 *
 * <p>Only the low-stock threshold key (GLOBAL scope) is applied; every other
 * setting key on the topic ({@code inventory.reservation.ttl_hours},
 * {@code inbound.*}, {@code outbound.*}) is ignored (net-zero) — the seam is
 * left open for future keys. Mirrors the master-ref consumer shape:
 * envelope parse → {@link EventDedupePort#process} (T8) → apply; the shared
 * {@code KafkaConsumerConfig} error handler owns retry + DLT.
 */
@Component
@Profile("!standalone")
public class AdminSettingsConsumer {

    private static final Logger log = LoggerFactory.getLogger(AdminSettingsConsumer.class);

    /** The GLOBAL setting key this consumer reacts to (admin V99 seed). */
    static final String LOW_STOCK_THRESHOLD_KEY = "inventory.low_stock.default_threshold_qty";
    private static final String GLOBAL_SCOPE = "GLOBAL";

    private final SettingsEventParser parser;
    private final LowStockThresholdWriterPort thresholdWriter;
    private final EventDedupePort dedupe;

    public AdminSettingsConsumer(SettingsEventParser parser,
                                 LowStockThresholdWriterPort thresholdWriter,
                                 EventDedupePort dedupe) {
        this.parser = parser;
        this.thresholdWriter = thresholdWriter;
        this.dedupe = dedupe;
    }

    @KafkaListener(
            topics = "${inventory.kafka.topics.admin-settings:wms.admin.settings.v1}",
            groupId = "${spring.kafka.consumer.group-id:inventory-service}"
    )
    @Transactional
    public void handle(@Payload String rawJson,
                       @Header(name = "kafka_receivedMessageKey", required = false) String key) {
        SettingsEventEnvelope envelope = parser.parse(rawJson);
        String settingKey = envelope.payload().path("key").asText(null);

        // Filter before dedupe: only the low-stock threshold key is relevant. Other
        // setting keys share this topic — ignore them without a dedupe row (a re-delivery
        // simply re-skips; the apply is a no-op either way).
        if (!LOW_STOCK_THRESHOLD_KEY.equals(settingKey)) {
            log.debug("admin.settings.changed for '{}' — not the low-stock threshold key; ignoring", settingKey);
            return;
        }

        MDC.put("eventId", envelope.eventId().toString());
        MDC.put("consumer", "admin-settings");
        try {
            EventDedupePort.Outcome outcome = dedupe.process(
                    envelope.eventId(),
                    envelope.eventType(),
                    () -> applyThresholdChange(envelope));
            if (outcome == EventDedupePort.Outcome.IGNORED_DUPLICATE) {
                log.debug("admin.settings.changed event {} already applied; skipping", envelope.eventId());
            }
        } finally {
            MDC.remove("eventId");
            MDC.remove("consumer");
        }
    }

    private void applyThresholdChange(SettingsEventEnvelope envelope) {
        JsonNode payload = envelope.payload();

        // v1 handles the GLOBAL default only — the seed key is GLOBAL and the
        // in-memory holder exposes a single default. WAREHOUSE-scoped overrides
        // have no seeded setting yet (out of scope).
        String scope = payload.path("scope").asText(null);
        if (!GLOBAL_SCOPE.equals(scope)) {
            log.debug("low-stock threshold setting scope '{}' is not GLOBAL; ignoring (v1 GLOBAL default only)", scope);
            return;
        }

        JsonNode valueNode = payload.get("valueJson");
        if (valueNode == null || valueNode.isNull() || !valueNode.canConvertToInt()) {
            // Non-retryable — malformed contract payload. Routes to the DLT.
            throw new IllegalArgumentException(
                    "low-stock threshold valueJson is not an integer: " + valueNode);
        }

        int threshold = valueNode.intValue();
        thresholdWriter.updateDefaultThreshold(threshold);
        log.info("low-stock default threshold updated to {} from admin.settings.changed", threshold);
    }
}
