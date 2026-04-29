package com.wms.inbound.adapter.in.web.filter;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Package-private utilities for computing a canonical SHA-256 body hash.
 *
 * <p>Key-order normalisation: JSON is re-serialised with both
 * {@link MapperFeature#SORT_PROPERTIES_ALPHABETICALLY} (for POJO fields) and
 * {@link SerializationFeature#ORDER_MAP_ENTRIES_BY_KEYS} (for Map entries,
 * which is what Jackson produces when parsing into {@code Object.class}) so
 * that {@code {"b":1,"a":2}} and {@code {"a":2,"b":1}} produce the same hash.
 * This is required by {@code idempotency.md} §1.4 to prevent spurious
 * {@code DUPLICATE_REQUEST (409)} responses when two clients submit
 * semantically identical bodies with different key ordering.
 */
final class BodyHashUtil {

    private BodyHashUtil() {
    }

    /**
     * Returns the SHA-256 hex digest of the given bytes, interpreted as a
     * canonical (sorted-keys) JSON string when the content is valid JSON.
     * Falls back to hashing the raw bytes when the content is not valid JSON
     * (e.g. multipart or plain-text bodies).
     *
     * @param bodyBytes raw request body bytes (may be empty)
     * @param mapper    Jackson {@link ObjectMapper} used for JSON round-trip
     * @return lowercase hex SHA-256 digest
     */
    static String computeHash(byte[] bodyBytes, ObjectMapper mapper) {
        if (bodyBytes == null || bodyBytes.length == 0) {
            return sha256hex(new byte[0]);
        }
        try {
            String normalised = normalizedJson(bodyBytes, mapper);
            return sha256hex(normalised.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // Non-JSON body — hash the raw bytes as-is.
            return sha256hex(bodyBytes);
        }
    }

    /**
     * Re-serialises {@code jsonBytes} with alphabetically-sorted keys so that
     * semantically equivalent JSON objects with different key orders produce
     * the same canonical string.
     */
    static String normalizedJson(byte[] jsonBytes, ObjectMapper mapper) throws Exception {
        // SORT_PROPERTIES_ALPHABETICALLY handles POJO fields;
        // ORDER_MAP_ENTRIES_BY_KEYS handles Map entries (the concrete type
        // Jackson produces when deserialising into Object.class).
        ObjectMapper sortingMapper = JsonMapper.builder()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .build();
        Object parsed = mapper.readValue(jsonBytes, Object.class);
        return sortingMapper.writeValueAsString(parsed);
    }

    /**
     * Returns the lowercase hexadecimal SHA-256 digest of {@code input}.
     */
    static String sha256hex(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
