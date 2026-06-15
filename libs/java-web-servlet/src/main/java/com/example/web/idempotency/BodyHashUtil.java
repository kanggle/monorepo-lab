package com.example.web.idempotency;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Shared utilities for computing a canonical SHA-256 request-body hash, used by
 * the servlet-stack REST Idempotency-Key filters.
 *
 * <p>Key-order normalisation: JSON is re-serialised with both
 * {@link MapperFeature#SORT_PROPERTIES_ALPHABETICALLY} (for POJO fields) and
 * {@link SerializationFeature#ORDER_MAP_ENTRIES_BY_KEYS} (for Map entries,
 * which is what Jackson produces when parsing into {@code Object.class}) so
 * that {@code {"b":1,"a":2}} and {@code {"a":2,"b":1}} produce the same hash.
 * This prevents spurious {@code DUPLICATE_REQUEST (409)} responses when two
 * clients submit semantically identical bodies with different key ordering or
 * whitespace.
 *
 * <p>This is the single shared implementation. It was previously copied
 * per-service; the copies silently diverged (see {@link #CANONICAL_MAPPER}),
 * which is exactly the failure mode hosting it once in {@code libs/} prevents.
 * The class is project-agnostic — pure Jackson + SHA-256, no domain content —
 * so it carries no shared-library-policy concern.
 */
public final class BodyHashUtil {

    private BodyHashUtil() {
    }

    /**
     * Canonicalising mapper — sorted keys, and CRUCIALLY a plain vanilla
     * {@link JsonMapper} with <strong>no auto-registered modules</strong>.
     *
     * <p>The body round-trip MUST parse and serialise with the <em>same</em>
     * module set. A caller-supplied application {@link ObjectMapper} can have
     * {@code jackson-module-scala} on the classpath (pulled transitively), so
     * {@code readValue(json, Object.class)} returns a
     * {@code scala.collection.immutable.Map} — which a module-less serialiser
     * then renders by its Java-bean getters as {@code {"empty":...,
     * "traversableAgain":...}}, a CONTENT-INDEPENDENT string. That would make
     * every body hash identical → idempotency body-conflict detection (409)
     * silently breaks (different bodies under the same key treated as replays).
     * Using one vanilla mapper for both read and write yields a
     * {@code java.util.LinkedHashMap} that serialises faithfully (TASK-BE-342).
     */
    private static final ObjectMapper CANONICAL_MAPPER = JsonMapper.builder()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .build();

    /**
     * Returns the SHA-256 hex digest of the given bytes, interpreted as a
     * canonical (sorted-keys) JSON string when the content is valid JSON.
     * Falls back to hashing the raw bytes when the content is not valid JSON
     * (e.g. multipart or plain-text bodies).
     *
     * @param bodyBytes raw request body bytes (may be empty)
     * @param mapper    retained for API compatibility but IGNORED — the
     *                  round-trip uses the module-free {@link #CANONICAL_MAPPER}
     *                  (a caller-supplied mapper with jackson-module-scala was
     *                  the TASK-BE-342 bug; see {@link #CANONICAL_MAPPER})
     * @return lowercase hex SHA-256 digest
     */
    public static String computeHash(byte[] bodyBytes, ObjectMapper mapper) {
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
     * the same canonical string. Parses AND serialises with the same vanilla
     * {@link #CANONICAL_MAPPER}; the {@code mapper} parameter is ignored
     * (retained for API compatibility) — using it for parsing while a
     * module-free mapper serialises is exactly the TASK-BE-342 correctness bug.
     */
    @SuppressWarnings("unused")
    public static String normalizedJson(byte[] jsonBytes, ObjectMapper mapper) throws Exception {
        Object parsed = CANONICAL_MAPPER.readValue(jsonBytes, Object.class);
        return CANONICAL_MAPPER.writeValueAsString(parsed);
    }

    /**
     * Returns the lowercase hexadecimal SHA-256 digest of {@code input}.
     */
    public static String sha256hex(byte[] input) {
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
