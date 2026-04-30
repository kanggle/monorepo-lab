package com.example.auth.domain.session;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the deprecated auth-service shim (TASK-BE-028c-fix) forwards
 * to the canonical {@code com.example.common.id.UuidV7} implementation and
 * preserves v7 structural invariants. When the shim is eventually removed,
 * this test is expected to be deleted alongside it.
 */
@SuppressWarnings("deprecation")
class UuidV7ShimTest {

    @Test
    void randomUuidIsVersion7() {
        UUID u = UuidV7.randomUuid();
        assertThat(u.version()).isEqualTo(7);
        assertThat(u.variant()).isEqualTo(2);
    }

    @Test
    void randomStringIsParseableV7() {
        UUID u = UUID.fromString(UuidV7.randomString());
        assertThat(u.version()).isEqualTo(7);
    }

    @Test
    void timestampMsForwardMatchesCanonicalExtraction() {
        UUID u = UuidV7.randomUuid();
        long shimTs = UuidV7.timestampMs(u);
        long canonicalTs = com.example.common.id.UuidV7.timestampMs(u);
        assertThat(shimTs).isEqualTo(canonicalTs);
        assertThat(shimTs).isCloseTo(System.currentTimeMillis(), org.assertj.core.data.Offset.offset(5_000L));
    }
}
