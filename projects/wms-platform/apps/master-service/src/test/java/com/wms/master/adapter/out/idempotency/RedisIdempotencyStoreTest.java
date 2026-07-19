package com.wms.master.adapter.out.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.web.idempotency.StoredResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Isolated unit test for {@link RedisIdempotencyStore} (TASK-BE-527, AC-2).
 *
 * <p>Mirrors the {@code RedisIdempotencyStoreTest} pattern already present in
 * {@code outbound-service} / {@code inventory-service} / {@code inbound-service}:
 * a mocked {@link StringRedisTemplate} rather than a Redis Testcontainer,
 * because the properties under test — key-prefix shape, TTL propagation, lock
 * semantics, and the malformed-entry fallback — are pinned by verifying the
 * exact calls issued to the template, not by exercising real Redis wire
 * behavior (which the 3 full-stack {@code *IntegrationTest} classes already
 * do transitively against a real Redis container via {@code
 * MasterServiceIntegrationBase}). A dedicated container per unit-test class
 * would be a disproportionate footprint for what is fundamentally a "does
 * this adapter call the template correctly" test.
 *
 * <p>The entry/lock prefixes ({@code master:idem:} / {@code master:idem:lock:})
 * are declared {@code private static final} on {@link RedisIdempotencyStore}
 * (no test-visibility change permitted under TASK-BE-527's src/main freeze),
 * so this test pins them as literal constants matching the adapter's own
 * class-level Javadoc rather than referencing the private fields directly.
 */
@ExtendWith(MockitoExtension.class)
class RedisIdempotencyStoreTest {

    private static final String ENTRY_PREFIX = "master:idem:";
    private static final String LOCK_PREFIX = "master:idem:lock:";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private RedisIdempotencyStore store;

    private static final String SUFFIX = "POST:/api/v1/master/partners:test-key";
    private static final String EXPECTED_ENTRY_KEY = ENTRY_PREFIX + SUFFIX;
    private static final String EXPECTED_LOCK_KEY = LOCK_PREFIX + SUFFIX;

    @BeforeEach
    void setUp() {
        store = new RedisIdempotencyStore(redisTemplate, objectMapper);
    }

    @Test
    @DisplayName("lookup hits the entry key under the master:idem: prefix and round-trips the stored response")
    void lookupReadsCanonicalEntryKey() throws Exception {
        StoredResponse response = new StoredResponse(
                "hash-1", 201, "{\"id\":\"x\"}", "application/json",
                Instant.parse("2026-04-28T12:00:00Z"));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(EXPECTED_ENTRY_KEY)).thenReturn(objectMapper.writeValueAsString(response));

        Optional<StoredResponse> result = store.lookup(SUFFIX);

        assertThat(result).contains(response);
        verify(valueOps).get(EXPECTED_ENTRY_KEY);
    }

    @Test
    @DisplayName("lookup miss returns empty without exception")
    void lookupMissReturnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(EXPECTED_ENTRY_KEY)).thenReturn(null);

        assertThat(store.lookup(SUFFIX)).isEmpty();
    }

    @Test
    @DisplayName("lookup with malformed JSON falls back to cache miss (serialization-failure path)")
    void lookupWithCorruptEntryReturnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(EXPECTED_ENTRY_KEY)).thenReturn("not-json{");

        assertThat(store.lookup(SUFFIX)).isEmpty();
    }

    @Test
    @DisplayName("put serializes the response and writes it under the entry key with the given ttl")
    void putWritesCanonicalEntryKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        StoredResponse response = new StoredResponse(
                "hash-1", 201, "{\"id\":\"x\"}", "application/json",
                Instant.parse("2026-04-28T12:00:00Z"));

        store.put(SUFFIX, response, Duration.ofSeconds(86_400));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

        assertThat(keyCaptor.getValue()).isEqualTo(EXPECTED_ENTRY_KEY);
        assertThat(valueCaptor.getValue()).contains("\"requestHash\":\"hash-1\"");
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofSeconds(86_400));
    }

    @Test
    @DisplayName("tryAcquireLock writes the lock key under master:idem:lock: and returns true on success")
    void tryAcquireLockUsesLockPrefix() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(EXPECTED_LOCK_KEY), anyString(), any(Duration.class)))
                .thenReturn(true);

        boolean acquired = store.tryAcquireLock(SUFFIX, Duration.ofSeconds(30));

        assertThat(acquired).isTrue();
        verify(valueOps).setIfAbsent(eq(EXPECTED_LOCK_KEY), eq("1"), eq(Duration.ofSeconds(30)));
    }

    @Test
    @DisplayName("tryAcquireLock returns false when the lock key already exists (contended)")
    void tryAcquireLockContended() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(EXPECTED_LOCK_KEY), anyString(), any(Duration.class)))
                .thenReturn(false);

        assertThat(store.tryAcquireLock(SUFFIX, Duration.ofSeconds(30))).isFalse();
    }

    @Test
    @DisplayName("releaseLock deletes the canonical lock key (does not touch the entry key)")
    void releaseLockDeletesLockKey() {
        store.releaseLock(SUFFIX);

        verify(redisTemplate).delete(EXPECTED_LOCK_KEY);
    }

    @Test
    @DisplayName("entry and lock keys never collide for the same storageKey")
    void entryAndLockKeysAreDistinct() {
        assertThat(EXPECTED_ENTRY_KEY).isNotEqualTo(EXPECTED_LOCK_KEY);
        assertThat(EXPECTED_LOCK_KEY).startsWith(ENTRY_PREFIX);
    }
}
