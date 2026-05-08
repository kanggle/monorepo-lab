package com.wms.admin.infra.idempotency;

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
 * Canonicalises a JSON body for stable hashing — sort object fields
 * alphabetically at every level, omit whitespace, preserve array order.
 * Empty body canonicalises to empty string. Per
 * {@code idempotency.md § 1.6}.
 */
public class RequestBodyCanonicalizer {

    private final ObjectMapper objectMapper;

    public RequestBodyCanonicalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String canonicalize(byte[] body) {
        if (body == null || body.length == 0) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.isMissingNode() || root.isNull()) {
                return "";
            }
            return objectMapper.writeValueAsString(sort(root));
        } catch (JsonProcessingException e) {
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
