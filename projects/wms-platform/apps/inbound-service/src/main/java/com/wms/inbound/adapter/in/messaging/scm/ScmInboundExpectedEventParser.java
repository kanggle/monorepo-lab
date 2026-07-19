package com.wms.inbound.adapter.in.messaging.scm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inbound.application.command.CancelScmInboundExpectationCommand;
import com.wms.inbound.application.command.CreateScmInboundExpectationCommand;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Parses raw scm {@code inbound-expected} / {@code inbound-expected.cancelled} Kafka payloads.
 *
 * <p>Envelope fields ({@code eventId}/{@code eventType}/{@code occurredAt}) are read from the
 * root; business fields from the {@code payload} object (or the root, when a producer emits a
 * flat shape). Structural failures throw {@link IllegalArgumentException} so the shared
 * {@code DefaultErrorHandler} classifies the record as non-retryable → straight to DLT.
 *
 * <p>The authoritative payload schema is owned by scm
 * ({@code scm-procurement-events.md}); this parser reads only the consumed subset per
 * {@code scm-inbound-expected-subscriptions.md}.
 */
@Component
public class ScmInboundExpectedEventParser {

    private final ObjectMapper objectMapper;

    public ScmInboundExpectedEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ScmInboundExpectedEnvelope parse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            UUID eventId = UUID.fromString(requireText(root, "eventId"));
            String eventType = requireText(root, "eventType");
            Instant occurredAt = Instant.parse(requireText(root, "occurredAt"));
            JsonNode body = (root.has("payload") && !root.get("payload").isNull())
                    ? root.get("payload") : root;
            return new ScmInboundExpectedEnvelope(eventId, eventType, occurredAt, body);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("malformed scm inbound-expected JSON", e);
        }
    }

    public CreateScmInboundExpectationCommand toCreateCommand(JsonNode body) {
        JsonNode linesNode = body.get("lines");
        if (linesNode == null || !linesNode.isArray()) {
            throw new IllegalArgumentException("inbound-expected missing lines[]");
        }
        List<CreateScmInboundExpectationCommand.Line> lines = new ArrayList<>();
        for (JsonNode line : linesNode) {
            lines.add(new CreateScmInboundExpectationCommand.Line(
                    text(line, "skuCode"),
                    intValue(line, "expectedQty")));
        }
        return new CreateScmInboundExpectationCommand(
                uuidOrNull(body, "poId"),
                requireText(body, "poNumber"),
                text(body, "supplierId"),
                text(body, "destinationWarehouseId"),
                text(body, "destinationNodeType"),
                dateOrNull(body, "expectedArrivalDate"),
                lines);
    }

    public CancelScmInboundExpectationCommand toCancelCommand(JsonNode body) {
        return new CancelScmInboundExpectationCommand(
                uuidOrNull(body, "poId"),
                requireText(body, "poNumber"),
                text(body, "reason"));
    }

    private static String requireText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("event missing required field: " + field);
        }
        return value.asText();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }

    /**
     * Parses {@code expectedQty}, which scm emits as a decimal <em>string</em>
     * ({@code BigDecimal.toPlainString()}, authoritative per ADR-MONO-050 D9) — e.g.
     * {@code "100"}. Also tolerates a raw JSON number for forward-compatibility. The
     * value must be a non-negative <em>integer</em>: a fractional value ({@code "1.5"}),
     * a negative value, or a non-numeric token is a structural error → {@link
     * IllegalArgumentException} → DLT. (The service-level {@code expectedQty <= 0} guard
     * still applies to the resulting int.)
     */
    private static int intValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("event missing/invalid int field: " + field);
        }
        BigDecimal decimal;
        if (value.isNumber()) {
            decimal = value.decimalValue();
        } else if (value.isTextual() && !value.asText().isBlank()) {
            try {
                decimal = new BigDecimal(value.asText().trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("event missing/invalid int field: " + field, e);
            }
        } else {
            throw new IllegalArgumentException("event missing/invalid int field: " + field);
        }
        int intValue;
        try {
            intValue = decimal.intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("event non-integer int field: " + field, e);
        }
        if (intValue <= 0) {
            throw new IllegalArgumentException("event non-positive int field: " + field);
        }
        return intValue;
    }

    private static UUID uuidOrNull(JsonNode node, String field) {
        String raw = text(node, field);
        return raw == null ? null : UUID.fromString(raw);
    }

    private static LocalDate dateOrNull(JsonNode node, String field) {
        String raw = text(node, field);
        return raw == null ? null : LocalDate.parse(raw);
    }
}
