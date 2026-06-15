package com.example.web.idempotency;

/**
 * Strategy for turning a raw request body into a stable, canonical hash used as
 * the idempotency body fingerprint (ADR-MONO-038 I3).
 *
 * <p>Two implementations ship in this module, matching the two canonicalization
 * families found across the WMS services:
 * <ul>
 *   <li>{@link JsonValueBodyCanonicalizer} — Family A ({@code inbound} /
 *       {@code outbound}): parse-to-{@code Object} + module-free sorted
 *       re-serialise (delegates to {@link BodyHashUtil}).</li>
 *   <li>{@link JsonTreeBodyCanonicalizer} — Family B ({@code master} /
 *       {@code admin}): recursive {@code JsonNode} tree-sort.</li>
 * </ul>
 *
 * <p>Both MUST be content-sensitive (different bodies → different hash) and
 * order-insensitive (key reordering → same hash), and both fall back to a
 * raw-byte hash for non-JSON bodies.
 */
@FunctionalInterface
public interface BodyCanonicalizer {

    /**
     * Returns the canonical SHA-256 hex hash of {@code body}. An empty or null
     * body hashes to {@code sha256("")}.
     */
    String hash(byte[] body);
}
