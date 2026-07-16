package com.example.scmplatform.procurement.application;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

/**
 * Computes a canonical SHA-256 hash of a request payload for REST idempotency
 * (TASK-BE-445). Uses a key-sorted {@link JsonMapper} so a legitimately
 * re-serialised retry (different field order, whitespace) hashes identically and
 * is NOT mis-flagged as a payload mismatch.
 */
@Component
public class IdempotencyHasher {

    private final JsonMapper canonicalMapper = JsonMapper.builder()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .build();

    /**
     * @param payload the request payload (a request DTO or a small canonical map)
     * @return lowercase-hex SHA-256 of the canonical JSON encoding
     */
    public String hash(Object payload) {
        try {
            byte[] json = canonicalMapper.writeValueAsBytes(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Failed to canonicalise idempotency payload", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
