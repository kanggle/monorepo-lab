package com.wms.master.adapter.out.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.master.application.port.out.StoredResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class InMemoryIdempotencyStoreTest {

    @Test
    void putThenLookupReturnsEntry() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
        StoredResponse response = new StoredResponse("hash", 201, "{}", "application/json", Instant.now());

        store.put("k", response, Duration.ofMinutes(10));

        assertThat(store.lookup("k")).contains(response);
    }

    @Test
    void lookupReturnsEmptyAfterTtlExpiry() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-04-19T00:00:00Z"));
        Clock clock = Clock.fixed(now.get(), ZoneOffset.UTC);
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(tickingClock(now));

        store.put("k", new StoredResponse("hash", 201, "{}", "application/json", clock.instant()), Duration.ofSeconds(10));
        now.set(now.get().plusSeconds(5));
        assertThat(store.lookup("k")).isPresent();

        now.set(now.get().plusSeconds(10));
        assertThat(store.lookup("k")).isEmpty();
    }

    @Test
    void tryAcquireLockIsExclusive_untilExpiryOrRelease() throws InterruptedException {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();

        assertThat(store.tryAcquireLock("k", Duration.ofMillis(100))).isTrue();
        assertThat(store.tryAcquireLock("k", Duration.ofMillis(100))).isFalse();

        Thread.sleep(150);
        assertThat(store.tryAcquireLock("k", Duration.ofMillis(100))).isTrue();
    }

    @Test
    void releaseLockLetsNextCallerAcquire() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();

        assertThat(store.tryAcquireLock("k", Duration.ofMinutes(1))).isTrue();
        store.releaseLock("k");
        assertThat(store.tryAcquireLock("k", Duration.ofMinutes(1))).isTrue();
    }

    private static Clock tickingClock(AtomicReference<Instant> now) {
        return new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
    }
}
