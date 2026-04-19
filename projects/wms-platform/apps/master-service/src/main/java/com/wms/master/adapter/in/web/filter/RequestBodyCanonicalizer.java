package com.wms.master.adapter.in.web.filter;

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
 * Canonicalizes a JSON request body for stable hashing.
 * <p>
 * Per {@code specs/services/master-service/idempotency.md}: sort object fields
 * alphabetically at every level, omit whitespace, preserve array order, preserve
 * primitive values verbatim. Empty body canonicalizes to empty string.
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
