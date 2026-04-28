package com.wms.inventory.adapter.out.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inventory.application.port.out.StoredResponse;
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
 * Pins the literal Redis key shape produced by {@link RedisIdempotencyStore}
 * to {@code idempotency.md} §1.3. The adapter prepends the canonical
 * {@code inventory:idempotency:} prefix; callers pass the suffix
 * {@code {method}:{path_hash}:{idempotency_key}}.
 *
 * <p>Uses a mocked {@link StringRedisTemplate} rather than Testcontainers
 * because the assertion under test is the key string, not Redis behaviour.
 */
@ExtendWith(MockitoExtension.class)
class RedisIdempotencyStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private RedisIdempotencyStore store;

    private static final String SUFFIX = "POST:abc123:11111111-1111-1111-1111-111111111111";
    private static final String EXPECTED_ENTRY_KEY = "inventory:idempotency:" + SUFFIX;
    private static final String EXPECTED_LOCK_KEY = "inventory:idempotency:lock:" + SUFFIX;

    @BeforeEach
    void setUp() {
        store = new RedisIdempotencyStore(redisTemplate, objectMapper);
    }

    @Test
    @DisplayName("entry prefix matches idempotency.md §1.3 canonical shape")
    void entryPrefixMatchesSpec() {
        assertThat(RedisIdempotencyStore.ENTRY_PREFIX).isEqualTo("inventory:idempotency:");
    }

    @Test
    @DisplayName("lookup hits the entry key under the canonical prefix")
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
    @DisplayName("lookup with malformed JSON falls back to cache miss")
    void lookupWithCorruptEntryReturnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(EXPECTED_ENTRY_KEY)).thenReturn("not-json{");

        assertThat(store.lookup(SUFFIX)).isEmpty();
    }

    @Test
    @DisplayName("put writes entry under canonical prefix with ttl")
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
    @DisplayName("tryAcquireLock writes lock key under inventory:idempotency:lock:")
    void tryAcquireLockUsesLockPrefix() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(EXPECTED_LOCK_KEY), anyString(), any(Duration.class)))
                .thenReturn(true);

        boolean acquired = store.tryAcquireLock(SUFFIX, Duration.ofSeconds(30));

        assertThat(acquired).isTrue();
        verify(valueOps).setIfAbsent(eq(EXPECTED_LOCK_KEY), eq("1"), eq(Duration.ofSeconds(30)));
    }

    @Test
    @DisplayName("tryAcquireLock returns false when the lock key already exists")
    void tryAcquireLockContended() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(EXPECTED_LOCK_KEY), anyString(), any(Duration.class)))
                .thenReturn(false);

        assertThat(store.tryAcquireLock(SUFFIX, Duration.ofSeconds(30))).isFalse();
    }

    @Test
    @DisplayName("releaseLock deletes the canonical lock key (does not touch entry key)")
    void releaseLockDeletesLockKey() {
        store.releaseLock(SUFFIX);

        verify(redisTemplate).delete(EXPECTED_LOCK_KEY);
    }

    @Test
    @DisplayName("entry and lock prefixes never collide for the same storageKey")
    void entryAndLockKeysAreDistinct() {
        assertThat(EXPECTED_ENTRY_KEY).isNotEqualTo(EXPECTED_LOCK_KEY);
        assertThat(EXPECTED_LOCK_KEY).startsWith(RedisIdempotencyStore.ENTRY_PREFIX);
    }
}
