package com.example.web.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Family A canonicalizer ({@code inbound} / {@code outbound}): delegates to
 * {@link BodyHashUtil#computeHash(byte[], ObjectMapper)}, which parses into
 * {@code Object} and re-serialises with a module-free, key-sorting
 * {@code CANONICAL_MAPPER} (the TASK-BE-342-fixed implementation).
 *
 * <p>The {@link ObjectMapper} is retained for source-compatibility with the
 * pre-extraction call sites but is ignored by {@link BodyHashUtil}.
 */
public final class JsonValueBodyCanonicalizer implements BodyCanonicalizer {

    private final ObjectMapper objectMapper;

    public JsonValueBodyCanonicalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String hash(byte[] body) {
        return BodyHashUtil.computeHash(body, objectMapper);
    }
}
