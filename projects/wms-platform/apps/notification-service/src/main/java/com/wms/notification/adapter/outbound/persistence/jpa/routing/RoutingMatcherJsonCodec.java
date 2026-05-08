package com.wms.notification.adapter.outbound.persistence.jpa.routing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.notification.domain.alert.AlertSeverity;
import com.wms.notification.domain.routing.ChannelTarget;
import com.wms.notification.domain.routing.ChannelType;
import com.wms.notification.domain.routing.RoutingMatcher;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Codec between the domain {@link RoutingMatcher} sealed type and the
 * JSONB column shape declared in {@code domain-model.md} § Value Objects:
 *
 * <pre>
 *   AlwaysMatch              {"type":"ALWAYS"}
 *   PayloadPredicateMatch    {"type":"PAYLOAD_PREDICATE","jsonPath":"...","op":"...","value":...}
 *   SeverityThresholdMatch   {"type":"SEVERITY_THRESHOLD","min":"WARNING"}
 * </pre>
 *
 * <p>And between {@link ChannelTarget} list and the {@code channel_targets_json}
 * array shape.
 *
 * <p>Lives in the adapter layer because it owns Jackson — domain layer is
 * framework-free.
 */
@Component
public class RoutingMatcherJsonCodec {

    private final ObjectMapper objectMapper;

    public RoutingMatcherJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RoutingMatcher decode(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String type = root.path("type").asText();
            return switch (type) {
                case "ALWAYS" -> new RoutingMatcher.AlwaysMatch();
                case "PAYLOAD_PREDICATE" -> new RoutingMatcher.PayloadPredicateMatch(
                        root.path("jsonPath").asText(),
                        RoutingMatcher.Op.valueOf(root.path("op").asText()),
                        readValue(root.path("value")));
                case "SEVERITY_THRESHOLD" -> new RoutingMatcher.SeverityThresholdMatch(
                        AlertSeverity.valueOf(root.path("min").asText()));
                default -> throw new IllegalArgumentException("Unknown matcher type: " + type);
            };
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decode matcher JSON: " + json, e);
        }
    }

    public List<ChannelTarget> decodeChannelTargets(String json) {
        try {
            List<ChannelTargetDto> dtos = objectMapper.readValue(json,
                    new TypeReference<List<ChannelTargetDto>>() {});
            List<ChannelTarget> result = new ArrayList<>(dtos.size());
            for (ChannelTargetDto dto : dtos) {
                result.add(new ChannelTarget(
                        ChannelType.valueOf(dto.channelType),
                        dto.channelId,
                        dto.templateKey));
            }
            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decode channelTargets JSON: " + json, e);
        }
    }

    private Object readValue(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isIntegralNumber()) {
            return node.longValue();
        }
        if (node.isFloatingPointNumber()) {
            return node.doubleValue();
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(elem -> list.add(readValue(elem)));
            return list;
        }
        return node.toString();
    }

    /** Public for tests / future admin v2 writes. */
    @SuppressWarnings("unused")
    private static class ChannelTargetDto {
        public String channelType;
        public String channelId;
        public String templateKey;
    }
}
