package com.example.security.infrastructure.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-259: per-tenant token-reuse counter — key format is
 * {@code reuse:{tenantId}:{accountId}} with 1-hour TTL.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("RedisTokenReuseCounter 단위 테스트 (per-tenant key)")
class RedisTokenReuseCounterUnitTest {

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    private RedisTokenReuseCounter counter;

    @BeforeEach
    void setUp() {
        counter = new RedisTokenReuseCounter(redisTemplate);
    }

    // ── incrementAndGet ───────────────────────────────────────────────────────

    @Test
    @DisplayName("incrementAndGet — 첫 번째 증가 (value=1) → expire 1h 호출")
    void incrementAndGet_firstIncrement_setsExpire() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("reuse:tenant-a:acc-1")).thenReturn(1L);

        long result = counter.incrementAndGet(TENANT_A, "acc-1");

        assertThat(result).isEqualTo(1L);
        verify(redisTemplate).expire(eq("reuse:tenant-a:acc-1"), eq(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("incrementAndGet — 누적 증가 (value=5) → expire 미호출")
    void incrementAndGet_subsequentIncrement_doesNotSetExpire() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("reuse:tenant-a:acc-2")).thenReturn(5L);

        long result = counter.incrementAndGet(TENANT_A, "acc-2");

        assertThat(result).isEqualTo(5L);
        verify(redisTemplate, never()).expire(any(), any());
    }

    @Test
    @DisplayName("incrementAndGet — increment null 반환 → 0 반환")
    void incrementAndGet_nullResult_returnsZero() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(any())).thenReturn(null);

        long result = counter.incrementAndGet(TENANT_A, "acc-3");

        assertThat(result).isEqualTo(0L);
        verify(redisTemplate, never()).expire(any(), any());
    }

    @Test
    @DisplayName("incrementAndGet — Redis 오류 → fail-open (0 반환)")
    void incrementAndGet_redisException_returnsZero() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));

        long result = counter.incrementAndGet(TENANT_A, "acc-err");

        assertThat(result).isEqualTo(0L);
    }

    // ── peek ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("peek — 키 존재 → 파싱된 값 반환")
    void peek_keyExists_returnsParsedValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("reuse:tenant-a:acc-4")).thenReturn("7");

        long result = counter.peek(TENANT_A, "acc-4");

        assertThat(result).isEqualTo(7L);
    }

    @Test
    @DisplayName("peek — 키 없음 (null) → 0 반환")
    void peek_keyAbsent_returnsZero() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("reuse:tenant-a:acc-5")).thenReturn(null);

        long result = counter.peek(TENANT_A, "acc-5");

        assertThat(result).isEqualTo(0L);
    }

    @Test
    @DisplayName("peek — Redis 오류 → fail-open (0 반환)")
    void peek_redisException_returnsZero() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));

        long result = counter.peek(TENANT_A, "acc-err");

        assertThat(result).isEqualTo(0L);
    }

    // ── TASK-BE-259: Cross-Tenant Isolation ──────────────────────────────────

    @Test
    @DisplayName("[cross-tenant] tenantA 와 tenantB 의 key 가 서로 다름 (reuse:{tenantId}:{accountId})")
    void crossTenantIsolation_differentKeys() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("reuse:tenant-a:acc-1")).thenReturn(50L);
        when(valueOps.increment("reuse:tenant-b:acc-1")).thenReturn(1L);

        long countA = counter.incrementAndGet(TENANT_A, "acc-1");
        long countB = counter.incrementAndGet(TENANT_B, "acc-1");

        assertThat(countA).isEqualTo(50L);
        assertThat(countB).isEqualTo(1L);
        // tenantA accumulated 50 reuses; tenantB (same accountId) is independent.
    }
}
