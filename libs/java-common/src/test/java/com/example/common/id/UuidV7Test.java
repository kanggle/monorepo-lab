package com.example.common.id;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies UUID v7 structural fields per RFC 9562. */
class UuidV7Test {

    @Test
    void versionNibbleIsSeven() {
        UUID u = UuidV7.randomUuid();
        assertThat(u.version()).isEqualTo(7);
    }

    @Test
    void variantIsRfc4122() {
        UUID u = UuidV7.randomUuid();
        // Variant bits "10" → java UUID.variant() returns 2
        assertThat(u.variant()).isEqualTo(2);
    }

    @Test
    void timestampMsIsCloseToNow() {
        long before = System.currentTimeMillis();
        UUID u = UuidV7.randomUuid();
        long after = System.currentTimeMillis();
        long ts = UuidV7.timestampMs(u);
        assertThat(ts).isBetween(before, after);
    }

    @Test
    void generatedValuesAreUnique() {
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            seen.add(UuidV7.randomUuid());
        }
        assertThat(seen).hasSize(1000);
    }

    @Test
    void stringFormIsParseable() {
        UUID u = UUID.fromString(UuidV7.randomString());
        assertThat(u.version()).isEqualTo(7);
    }
}
