package com.wms.admin.infra.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.web.idempotency.StoredResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link InMemoryIdempotencyStore}.
 *
 * <p>The concurrency test forks two threads that both call
 * {@link InMemoryIdempotencyStore#tryAcquireLock(String, Duration)} on the
 * same key. Exactly one must observe a successful acquire — verifying the
 * {@link java.util.concurrent.ConcurrentHashMap#compute} guarantee
 * (TASK-BE-564 — closes the get-then-put race present before this fix).
 */
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

    @Test
    @DisplayName("two concurrent threads call tryAcquireLock — exactly one wins (TASK-BE-564)")
    void concurrentTryAcquireLockYieldsExactlyOneWinner() throws Exception {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
        String key = "POST:admin:11111111-1111-1111-1111-111111111111";
        Duration ttl = Duration.ofSeconds(30);

        int rounds = 200;
        AtomicInteger collisionsObserved = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < rounds; i++) {
                String roundKey = key + ":" + i;
                CountDownLatch start = new CountDownLatch(1);
                Future<Boolean> a = pool.submit(() -> {
                    start.await();
                    return store.tryAcquireLock(roundKey, ttl);
                });
                Future<Boolean> b = pool.submit(() -> {
                    start.await();
                    return store.tryAcquireLock(roundKey, ttl);
                });
                start.countDown();
                int wins = (a.get() ? 1 : 0) + (b.get() ? 1 : 0);
                if (wins != 1) {
                    collisionsObserved.incrementAndGet();
                }
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(collisionsObserved.get())
                .as("exactly one thread per round must win the lock")
                .isZero();
    }

    private static Clock tickingClock(AtomicReference<Instant> now) {
        return new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
    }
}
