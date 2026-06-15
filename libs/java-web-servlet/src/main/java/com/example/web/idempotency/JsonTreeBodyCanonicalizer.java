package com.example.web.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Family B canonicalizer ({@code master} / {@code admin}): sorts JSON object
 * fields alphabetically at every level (preserving array order and primitive
 * values verbatim) via a recursive {@code JsonNode} tree-sort, then SHA-256s
 * the canonical string.
 *
 * <p>This is the lifted {@code master-service} {@code RequestBodyCanonicalizer}
 * + {@code sha256Hex}, adopting master's <strong>lenient fallback</strong>
 * ({@link JsonProcessingException} | {@link RuntimeException} |
 * {@link java.io.IOException} → raw UTF-8 byte hash) as the shared behavior.
 * This superset subsumes {@code admin-service}'s narrower
 * {@code JsonProcessingException}-only catch — a safe behavior addition for
 * admin (a malformed/non-JSON body now hashes to its raw bytes rather than
 * propagating), effectively a bugfix (ADR-MONO-038 I3).
 *
 * <p>Unlike Family A's {@code readValue(Object.class)} round-trip, {@code readTree}
 * always yields a {@code JsonNode} regardless of registered Jackson modules, so
 * this family is immune to the TASK-BE-342 {@code jackson-module-scala} hazard.
 * The canonical string it produces differs from Family A's; unifying the two
 * onto one algorithm is a deferred follow-up (ADR-MONO-038 § 3.3).
 */
public final class JsonTreeBodyCanonicalizer implements BodyCanonicalizer {

    private final ObjectMapper objectMapper;

    public JsonTreeBodyCanonicalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String hash(byte[] body) {
        return BodyHashUtil.sha256hex(canonicalize(body).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns the canonical (sorted-keys, whitespace-free) JSON string for
     * {@code body}; empty/null/blank-JSON → empty string; non-JSON or malformed
     * → the raw bytes as a UTF-8 string.
     */
    String canonicalize(byte[] body) {
        if (body == null || body.length == 0) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.isMissingNode() || root.isNull()) {
                return "";
            }
            return objectMapper.writeValueAsString(sort(root));
        } catch (JsonProcessingException | RuntimeException e) {
            // Non-JSON or malformed body: hash raw bytes as UTF-8 string.
            return new String(body, StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private JsonNode sort(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            Collections.sort(names);
            for (String name : names) {
                sorted.set(name, sort(node.get(name)));
            }
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode sorted = objectMapper.createArrayNode();
            node.forEach(elem -> sorted.add(sort(elem)));
            return sorted;
        }
        return node;
    }
}
