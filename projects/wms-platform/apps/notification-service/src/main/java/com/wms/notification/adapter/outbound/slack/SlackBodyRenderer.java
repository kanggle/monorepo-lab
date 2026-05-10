package com.wms.notification.adapter.outbound.slack;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders a Slack-compatible {@code {"text":"..."}} JSON body from an
 * {@code AlertEnvelope} payload snapshot.
 *
 * <p>Best-effort plaintext rendering. Robust to malformed payload — falls
 * back to a literal stamp so the message at least reaches ops, even if the
 * routing service tampered with the snapshot.
 *
 * <p>Stateless utility — all methods are static. v2 template engine will
 * replace this with a {@code @Component} that loads channel-specific
 * Mustache/Handlebars templates.
 */
class SlackBodyRenderer {

    private static final Logger log = LoggerFactory.getLogger(SlackBodyRenderer.class);

    private SlackBodyRenderer() {
        // static utility
    }

    /**
     * Produces the Slack webhook request body for the given payload snapshot
     * and channel alias.
     */
    static String render(ObjectMapper objectMapper, String payloadJson, String channelAlias) {
        try {
            JsonNode root = objectMapper.readTree(payloadJson);
            String eventType = root.path("eventType").asText("unknown-event");
            String aggregateId = root.path("aggregateId").asText("");
            String text = "[" + channelAlias + "] " + eventType
                    + (aggregateId.isEmpty() ? "" : " @ " + aggregateId);
            return objectMapper.writeValueAsString(Map.of("text", text));
        } catch (JsonProcessingException e) {
            log.warn("Failed to render Slack body for alias={}; falling back to stamp", channelAlias);
            try {
                return objectMapper.writeValueAsString(Map.of(
                        "text", "[" + channelAlias + "] notification (payload render failed)"));
            } catch (JsonProcessingException impossible) {
                throw new IllegalStateException("Jackson failed to serialise a Map<String,String>", impossible);
            }
        }
    }
}
