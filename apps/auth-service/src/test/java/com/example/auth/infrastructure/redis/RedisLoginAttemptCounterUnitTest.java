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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RedisLoginAttemptCounter}.
 *
 * <p>TASK-BE-229: key pattern is now {@code login:fail:{tenantId}:{emailHash}}.
 * The legacy single-arg overloads delegate to "fan-platform", so keys are
 * {@code login:fail:fan-platform:{emailHash}}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RedisLoginAttemptCounter 단위 테스트")
class RedisLoginAttemptCounterUnitTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    private RedisLoginAttemptCounter counter;

    @BeforeEach
    void setUp() {
        counter = new RedisLoginAttemptCounter(redisTemplate);
        ReflectionTestUtils.setField(counter, "failureWindowSeconds", 900L);
    }

    // ── getFailureCount (tenant-aware) ─────────────────────────────────────────

    @Test
    @DisplayName("getFailureCount(tenantId, emailHash) — 키 존재 → 파싱된 카운트 반환")
    void getFailureCount_tenantAware_keyExists_returnsCount() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("login:fail:fan-platform:hash-1")).thenReturn("5");

        assertThat(counter.getFailureCount("fan-platform", "hash-1")).isEqualTo(5);
    }

    @Test
    @DisplayName("getFailureCount(tenantId, emailHash) — 키 없음 → 0 반환")
    void getFailureCount_tenantAware_keyAbsent_returnsZero() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("login:fail:wms:hash-2")).thenReturn(null);

        assertThat(counter.getFailureCount("wms", "hash-2")).isEqualTo(0);
    }

    @Test
    @DisplayName("getFailureCount — Redis 오류 → fail-open (0 반환)")
    void getFailureCount_redisException_failOpen() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenThrow(new QueryTimeoutException("Redis timeout"));

        assertThat(counter.getFailureCount("fan-platform", "hash-err")).isEqualTo(0);
    }

    // ── getFailureCount (legacy single-arg — delegates to fan-platform) ────────

    @Test
    @DisplayName("getFailureCount(emailHash) — 레거시: fan-platform 키 사용")
    void getFailureCount_legacy_usesFanPlatformKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("login:fail:fan-platform:hash-1")).thenReturn("3");

        assertThat(counter.getFailureCount("hash-1")).isEqualTo(3);
    }

    // ── incrementFailureCount ──────────────────────────────────────────────────

    @Test
    @DisplayName("incrementFailureCount(tenantId, emailHash) — 정상: increment 후 expire 호출")
    void incrementFailureCount_tenantAware_incrementsAndSetsExpiry() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        counter.incrementFailureCount("fan-platform", "hash-3");

        verify(valueOps).increment("login:fail:fan-platform:hash-3");
        verify(redisTemplate).expire(eq("login:fail:fan-platform:hash-3"), eq(Duration.ofSeconds(900L)));
    }

    @Test
    @DisplayName("incrementFailureCount(emailHash) — 레거시: fan-platform 키 사용")
    void incrementFailureCount_legacy_usesFanPlatformKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        counter.incrementFailureCount("hash-3");

        verify(valueOps).increment("login:fail:fan-platform:hash-3");
        verify(redisTemplate).expire(eq("login:fail:fan-platform:hash-3"), eq(Duration.ofSeconds(900L)));
    }

    @Test
    @DisplayName("incrementFailureCount — Redis 오류 → 예외 흡수")
    void incrementFailureCount_redisException_swallowed() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doThrow(new QueryTimeoutException("Redis timeout")).when(valueOps).increment(any());

        counter.incrementFailureCount("fan-platform", "hash-err");
    }

    // ── resetFailureCount ──────────────────────────────────────────────────────

    @Test
    @DisplayName("resetFailureCount(tenantId, emailHash) — 정상: 키 삭제 호출")
    void resetFailureCount_tenantAware_deletesKey() {
        counter.resetFailureCount("fan-platform", "hash-4");

        verify(redisTemplate).delete("login:fail:fan-platform:hash-4");
    }

    @Test
    @DisplayName("resetFailureCount(emailHash) — 레거시: fan-platform 키 사용")
    void resetFailureCount_legacy_usesFanPlatformKey() {
        counter.resetFailureCount("hash-4");

        verify(redisTemplate).delete("login:fail:fan-platform:hash-4");
    }

    @Test
    @DisplayName("resetFailureCount — Redis 오류 → 예외 흡수")
    void resetFailureCount_redisException_swallowed() {
        doThrow(new QueryTimeoutException("Redis timeout")).when(redisTemplate).delete(any(String.class));

        counter.resetFailureCount("fan-platform", "hash-err");
    }
}
