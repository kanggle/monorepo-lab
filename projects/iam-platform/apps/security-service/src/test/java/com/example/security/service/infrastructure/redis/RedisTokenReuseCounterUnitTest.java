package com.example.security.service.infrastructure.redis;

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
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-259: per-tenant token-reuse counter — key format is
 * {@code reuse:{tenantId}:{accountId}} with 1-hour TTL.
 *
 * <p>TASK-BE-608: {@code incrementAndGet} no longer calls {@code INCR} and {@code EXPIRE} as
 * two separate round-trips — both run inside one Lua script via
 * {@link StringRedisTemplate#execute(RedisScript, List, Object...)}, so there is no window in
 * which a partial failure (the process/connection dying between the two calls) can leave the
 * key incremented but without a TTL. The tests below assert (a) the counting semantics are
 * unchanged (first increment = 1, TTL argument = 1h in seconds, subsequent increments do not
 * re-derive the TTL — that decision now lives inside the script, not in Java) and (b) that no
 * code path calls the old two-step {@code opsForValue().increment()} + {@code expire()} shape
 * any more — the regression this hardens against is exactly that shape reappearing.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("RedisTokenReuseCounter 단위 테스트 (원자적 INCR+EXPIRE — TASK-BE-608)")
class RedisTokenReuseCounterUnitTest {

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";
    private static final String TTL_SECONDS = String.valueOf(Duration.ofHours(1).toSeconds());

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    private RedisTokenReuseCounter counter;

    @BeforeEach
    void setUp() {
        counter = new RedisTokenReuseCounter(redisTemplate);
    }

    // ── incrementAndGet — atomic Lua script (TASK-BE-608) ──────────────────────

    @Test
    @DisplayName("incrementAndGet — 첫 번째 증가 (script 반환값=1) → 그 값을 그대로 반환")
    void incrementAndGet_firstIncrement_returnsOne() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("reuse:tenant-a:acc-1")), eq(TTL_SECONDS)))
                .thenReturn(1L);

        long result = counter.incrementAndGet(TENANT_A, "acc-1");

        assertThat(result).isEqualTo(1L);
    }

    @Test
    @DisplayName("incrementAndGet — 누적 증가 (script 반환값=5) → 그 값을 그대로 반환")
    void incrementAndGet_subsequentIncrement_returnsFive() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("reuse:tenant-a:acc-2")), eq(TTL_SECONDS)))
                .thenReturn(5L);

        long result = counter.incrementAndGet(TENANT_A, "acc-2");

        assertThat(result).isEqualTo(5L);
    }

    @Test
    @DisplayName("incrementAndGet — execute() null 반환 → 0 반환")
    void incrementAndGet_nullResult_returnsZero() {
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any()))
                .thenReturn(null);

        long result = counter.incrementAndGet(TENANT_A, "acc-3");

        assertThat(result).isEqualTo(0L);
    }

    @Test
    @DisplayName("incrementAndGet — Redis 오류 → fail-open (0 반환)")
    void incrementAndGet_redisException_returnsZero() {
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any()))
                .thenThrow(new RuntimeException("Redis down"));

        long result = counter.incrementAndGet(TENANT_A, "acc-err");

        assertThat(result).isEqualTo(0L);
    }

    @Test
    @DisplayName("incrementAndGet — 만료(1시간=3600s) 인자를 스크립트에 넘긴다")
    void incrementAndGet_passesOneHourTtlArgument() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("reuse:tenant-a:acc-ttl")), eq("3600")))
                .thenReturn(1L);

        long result = counter.incrementAndGet(TENANT_A, "acc-ttl");

        assertThat(result).isEqualTo(1L);
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("reuse:tenant-a:acc-ttl")), eq("3600"));
    }

    @Test
    @DisplayName("[TASK-BE-608 회귀 방어] incrementAndGet 은 별도 INCR/EXPIRE 두 호출로 나뉘지 않는다 " +
            "— 하나의 execute() 호출만 있고, opsForValue()/expire() 는 전혀 쓰이지 않는다")
    void incrementAndGet_neverSplitsIntoTwoRoundTrips() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("reuse:tenant-a:acc-atomic")), eq(TTL_SECONDS)))
                .thenReturn(1L);

        counter.incrementAndGet(TENANT_A, "acc-atomic");

        // The old shape called opsForValue().increment(...) then, conditionally, expire(...) —
        // two separate Redis round-trips with a window between them. If EXPIRE never lands
        // (process/connection dies in that window), the key is permanent. Asserting these are
        // never called (for the increment path) is the regression guard: the fix must stay a
        // single atomic script call, not a "try harder" retry around the same two-call shape.
        verify(redisTemplate, never()).expire(any(), any());
        verify(redisTemplate, never()).opsForValue();
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("reuse:tenant-a:acc-atomic")), eq(TTL_SECONDS));
        verifyNoMoreInteractions(redisTemplate);
    }

    // ── peek — unchanged (plain GET, not part of the atomicity fix) ────────────

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
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("reuse:tenant-a:acc-1")), eq(TTL_SECONDS)))
                .thenReturn(50L);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("reuse:tenant-b:acc-1")), eq(TTL_SECONDS)))
                .thenReturn(1L);

        long countA = counter.incrementAndGet(TENANT_A, "acc-1");
        long countB = counter.incrementAndGet(TENANT_B, "acc-1");

        assertThat(countA).isEqualTo(50L);
        assertThat(countB).isEqualTo(1L);
        // tenantA accumulated 50 reuses; tenantB (same accountId) is independent.
    }
}
