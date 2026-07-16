package com.example.scmplatform.procurement.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fast-lane unit coverage for {@link IdempotencyHasher} (TASK-BE-445): identical
 * payloads hash identically (so a legitimate retry is a replay, not a false 422),
 * key order is irrelevant, and different payloads diverge (so a genuine key-reuse
 * with a changed body is caught).
 */
class IdempotencyHasherTest {

    private final IdempotencyHasher hasher = new IdempotencyHasher();

    @Test
    @DisplayName("identical payloads hash identically")
    void identicalPayloads_sameHash() {
        Map<String, Object> a = Map.of("poId", "po-1", "reason", "damaged");
        Map<String, Object> b = Map.of("poId", "po-1", "reason", "damaged");
        assertThat(hasher.hash(a)).isEqualTo(hasher.hash(b));
    }

    @Test
    @DisplayName("field order does not change the hash (canonical / sorted keys)")
    void fieldOrderIrrelevant() {
        Map<String, Object> ordered = new LinkedHashMap<>();
        ordered.put("poId", "po-1");
        ordered.put("reason", "damaged");
        Map<String, Object> reversed = new LinkedHashMap<>();
        reversed.put("reason", "damaged");
        reversed.put("poId", "po-1");
        assertThat(hasher.hash(ordered)).isEqualTo(hasher.hash(reversed));
    }

    @Test
    @DisplayName("different payloads hash differently (key reuse with a changed body is caught)")
    void differentPayloads_differentHash() {
        assertThat(hasher.hash(Map.of("poId", "po-1")))
                .isNotEqualTo(hasher.hash(Map.of("poId", "po-2")));
        assertThat(hasher.hash(Map.of("poId", "po-1", "reason", "a")))
                .isNotEqualTo(hasher.hash(Map.of("poId", "po-1", "reason", "b")));
    }

    @Test
    @DisplayName("hash is a 64-char lowercase hex SHA-256")
    void hashShape() {
        String h = hasher.hash(Map.of("poId", "po-1"));
        assertThat(h).hasSize(64).matches("[0-9a-f]{64}");
    }
}
