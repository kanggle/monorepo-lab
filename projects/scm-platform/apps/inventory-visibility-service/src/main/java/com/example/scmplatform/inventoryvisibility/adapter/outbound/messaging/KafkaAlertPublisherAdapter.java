package com.example.scmplatform.inventoryvisibility.adapter.outbound.messaging;

import com.example.scmplatform.inventoryvisibility.application.port.outbound.AlertPublisherPort;
import com.example.scmplatform.inventoryvisibility.domain.node.NodeId;
import com.example.scmplatform.inventoryvisibility.domain.staleness.StalenessStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes SNAPSHOT_STALE / NODE_UNREACHABLE alerts to {@code scm.inventory.alert.v1}.
 * <p>
 * Best-effort: no outbox. If Kafka is down, the alert is lost.
 * Acceptable because the next staleness detection batch (5 minutes) re-detects
 * and re-publishes (Failure Scenario H in TASK-SCM-BE-003).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaAlertPublisherAdapter implements AlertPublisherPort {

    static final String ALERT_TOPIC = "scm.inventory.alert.v1";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publishStalenessAlert(NodeId nodeId, String tenantId,
                                       StalenessStatus status, Instant detectedAt) {
        String alertType = status == StalenessStatus.UNREACHABLE
                ? "NODE_UNREACHABLE" : "SNAPSHOT_STALE";

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", "inventory.alert." + alertType.toLowerCase());
        envelope.put("source", "scm-platform-inventory-visibility-service");
        envelope.put("occurredAt", detectedAt.toString());
        envelope.put("schemaVersion", 1);
        envelope.put("partitionKey", nodeId.toString());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nodeId", nodeId.toString());
        payload.put("tenantId", tenantId);
        payload.put("alertType", alertType);
        payload.put("stalenessStatus", status.name());
        payload.put("detectedAt", detectedAt.toString());
        envelope.put("payload", payload);

        try {
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(ALERT_TOPIC, nodeId.toString(), json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish staleness alert for node={} status={}: {}",
                                    nodeId, status, ex.getMessage());
                        } else {
                            log.debug("Published staleness alert: node={} status={} topic={}",
                                    nodeId, status, ALERT_TOPIC);
                        }
                    });
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize alert event for node={}", nodeId, e);
        }
    }
}
