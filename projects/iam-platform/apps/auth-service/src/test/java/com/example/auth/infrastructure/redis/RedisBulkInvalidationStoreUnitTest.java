package com.example.auth.infrastructure.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisBulkInvalidationStore 단위 테스트")
class RedisBulkInvalidationStoreUnitTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    private RedisBulkInvalidationStore store;

    @BeforeEach
    void setUp() {
        store = new RedisBulkInvalidationStore(redisTemplate);
    }

    // ── invalidateAll ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("invalidateAll — 정상: epoch-millis 문자열로 set 호출")
    void invalidateAll_normal_setsEpochMillisMarker() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        store.invalidateAll("acc-1", 3600L);

        verify(valueOps).set(eq("refresh:invalidate-all:acc-1"), any(String.class), eq(Duration.ofSeconds(3600L)));
    }

    @Test
    @DisplayName("invalidateAll — Redis 오류 → 예외 흡수 (fail-open)")
    void invalidateAll_redisException_swallowed() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doThrow(new QueryTimeoutException("Redis timeout"))
                .when(valueOps).set(any(), any(), any(Duration.class));

        store.invalidateAll("acc-err", 3600L);
    }

    // ── isInvalidated ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("isInvalidated — 마커 존재 → true")
    void isInvalidated_keyExists_returnsTrue() {
        when(redisTemplate.hasKey("refresh:invalidate-all:acc-2")).thenReturn(true);

        assertThat(store.isInvalidated("acc-2")).isTrue();
    }

    @Test
    @DisplayName("isInvalidated — 마커 없음 → false")
    void isInvalidated_keyAbsent_returnsFalse() {
        when(redisTemplate.hasKey("refresh:invalidate-all:acc-3")).thenReturn(false);

        assertThat(store.isInvalidated("acc-3")).isFalse();
    }

    @Test
    @DisplayName("isInvalidated — Redis 오류 → fail-closed (true 반환)")
    void isInvalidated_redisException_failClosed() {
        when(redisTemplate.hasKey(any())).thenThrow(new QueryTimeoutException("Redis timeout"));

        assertThat(store.isInvalidated("acc-err")).isTrue();
    }

    // ── getInvalidatedAt ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getInvalidatedAt — 유효한 epoch-millis → Instant 반환")
    void getInvalidatedAt_validMarker_returnsInstant() {
        Instant expected = Instant.parse("2026-04-29T10:00:00Z");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("refresh:invalidate-all:acc-4")).thenReturn(String.valueOf(expected.toEpochMilli()));

        Optional<Instant> result = store.getInvalidatedAt("acc-4");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(expected);
    }

    @Test
    @DisplayName("getInvalidatedAt — 키 없음 → Optional.empty()")
    void getInvalidatedAt_keyAbsent_returnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("refresh:invalidate-all:acc-5")).thenReturn(null);

        assertThat(store.getInvalidatedAt("acc-5")).isEmpty();
    }

    @Test
    @DisplayName("getInvalidatedAt — 숫자 아닌 값 → fail-closed (Instant.now() 근사)")
    void getInvalidatedAt_malformedValue_failClosed() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("refresh:invalidate-all:acc-6")).thenReturn("not-a-number");

        Instant before = Instant.now();
        Optional<Instant> result = store.getInvalidatedAt("acc-6");
        Instant after = Instant.now();

        assertThat(result).isPresent();
        assertThat(result.get()).isBetween(before, after);
    }

    @Test
    @DisplayName("getInvalidatedAt — Redis 오류 → fail-closed (Instant.now() 근사)")
    void getInvalidatedAt_redisException_failClosed() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenThrow(new QueryTimeoutException("Redis timeout"));

        Instant before = Instant.now();
        Optional<Instant> result = store.getInvalidatedAt("acc-err");
        Instant after = Instant.now();

        assertThat(result).isPresent();
        assertThat(result.get()).isBetween(before, after);
    }
}
