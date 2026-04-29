package com.wms.outbound.adapter.out.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link InMemoryIdempotencyStore}.
 *
 * <p>The atomicity test forks two threads that both call
 * {@link InMemoryIdempotencyStore#tryAcquireLock(String, Duration)} on the
 * same key. Exactly one must observe a successful acquire — verifying the
 * {@link java.util.concurrent.ConcurrentHashMap#compute} guarantee.
 */
class InMemoryIdempotencyStoreTest {

    @Test
    @DisplayName("two concurrent threads call tryAcquireLock — exactly one wins")
    void concurrentTryAcquireLockYieldsExactlyOneWinner() throws Exception {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
        String key = "POST:abc:11111111-1111-1111-1111-111111111111";
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

    @Test
    @DisplayName("subsequent acquire on the same key fails until release")
    void subsequentAcquireFailsUntilRelease() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
        String key = "POST:abc:k1";
        Duration ttl = Duration.ofSeconds(30);

        assertThat(store.tryAcquireLock(key, ttl)).isTrue();
        assertThat(store.tryAcquireLock(key, ttl)).isFalse();
        store.releaseLock(key);
        assertThat(store.tryAcquireLock(key, ttl)).isTrue();
    }
}
