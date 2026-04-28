package com.wms.inbound.adapter.in.webhook.erp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TimestampWindowVerifier}. Window is fixed to 300s for
 * the tests; spec says default 300s, prod ceiling 600s.
 */
class TimestampWindowVerifierTest {

    private static final Instant NOW = Instant.parse("2026-04-28T12:00:00Z");

    private TimestampWindowVerifier newVerifier() {
        return new TimestampWindowVerifier(Clock.fixed(NOW, ZoneOffset.UTC), 300L);
    }

    @Test
    void inWindowAcceptsExactNow() {
        assertThat(newVerifier().isWithinWindow(NOW.toString())).isTrue();
    }

    @Test
    void inWindowAcceptsBoundaryPast() {
        Instant boundary = NOW.minusSeconds(300);
        assertThat(newVerifier().isWithinWindow(boundary.toString())).isTrue();
    }

    @Test
    void inWindowAcceptsBoundaryFuture() {
        Instant boundary = NOW.plusSeconds(300);
        assertThat(newVerifier().isWithinWindow(boundary.toString())).isTrue();
    }

    @Test
    void rejectsSixMinutesPast() {
        Instant stale = NOW.minusSeconds(360);
        assertThat(newVerifier().isWithinWindow(stale.toString())).isFalse();
    }

    @Test
    void rejectsSixMinutesFuture() {
        Instant future = NOW.plusSeconds(360);
        assertThat(newVerifier().isWithinWindow(future.toString())).isFalse();
    }

    @Test
    void rejectsNullHeader() {
        assertThat(newVerifier().isWithinWindow(null)).isFalse();
    }

    @Test
    void rejectsBlankHeader() {
        assertThat(newVerifier().isWithinWindow("")).isFalse();
        assertThat(newVerifier().isWithinWindow("   ")).isFalse();
    }

    @Test
    void rejectsUnparseableHeader() {
        assertThat(newVerifier().isWithinWindow("yesterday")).isFalse();
        assertThat(newVerifier().isWithinWindow("2026-04-28")).isFalse();
        assertThat(newVerifier().isWithinWindow("garbage-12345")).isFalse();
    }
}
