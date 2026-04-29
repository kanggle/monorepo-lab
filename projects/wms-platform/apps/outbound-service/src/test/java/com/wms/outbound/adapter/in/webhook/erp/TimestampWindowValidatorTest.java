package com.wms.outbound.adapter.in.webhook.erp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TimestampWindowValidatorTest {

    private static final Instant NOW = Instant.parse("2026-04-28T12:00:00Z");

    private TimestampWindowValidator newValidator() {
        return new TimestampWindowValidator(Clock.fixed(NOW, ZoneOffset.UTC), 300L);
    }

    @Test
    void inWindowAcceptsExactNow() {
        assertThat(newValidator().isWithinWindow(NOW.toString())).isTrue();
    }

    @Test
    void inWindowAcceptsBoundaryPast() {
        Instant boundary = NOW.minusSeconds(300);
        assertThat(newValidator().isWithinWindow(boundary.toString())).isTrue();
    }

    @Test
    void inWindowAcceptsBoundaryFuture() {
        Instant boundary = NOW.plusSeconds(300);
        assertThat(newValidator().isWithinWindow(boundary.toString())).isTrue();
    }

    @Test
    void rejectsSixMinutesPast() {
        Instant stale = NOW.minusSeconds(360);
        assertThat(newValidator().isWithinWindow(stale.toString())).isFalse();
    }

    @Test
    void rejectsSixMinutesFuture() {
        Instant future = NOW.plusSeconds(360);
        assertThat(newValidator().isWithinWindow(future.toString())).isFalse();
    }

    @Test
    void rejectsNullHeader() {
        assertThat(newValidator().isWithinWindow(null)).isFalse();
    }

    @Test
    void rejectsBlankHeader() {
        assertThat(newValidator().isWithinWindow("")).isFalse();
        assertThat(newValidator().isWithinWindow("   ")).isFalse();
    }

    @Test
    void rejectsUnparseableHeader() {
        assertThat(newValidator().isWithinWindow("yesterday")).isFalse();
        assertThat(newValidator().isWithinWindow("2026-04-28")).isFalse();
        assertThat(newValidator().isWithinWindow("garbage-12345")).isFalse();
    }
}
