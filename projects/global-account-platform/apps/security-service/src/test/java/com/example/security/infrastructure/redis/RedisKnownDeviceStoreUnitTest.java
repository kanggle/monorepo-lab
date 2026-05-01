package com.example.security.infrastructure.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-248 Phase 2a: Redis key format changed to
 * {@code security:device:known:{tenantId}:{accountId}} for per-tenant isolation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RedisKnownDeviceStore 단위 테스트")
class RedisKnownDeviceStoreUnitTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock SetOperations<String, String> setOps;

    private RedisKnownDeviceStore store;

    @BeforeEach
    void setUp() {
        store = new RedisKnownDeviceStore(redisTemplate);
    }

    @Test
    @DisplayName("isKnown — 기기 지문 존재 → true 반환 (tenant-aware key)")
    void isKnown_memberExists_returnsTrue() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.isMember("security:device:known:tenant-a:acc-1", "fp-abc")).thenReturn(true);

        assertThat(store.isKnown("tenant-a", "acc-1", "fp-abc")).isTrue();
    }

    @Test
    @DisplayName("isKnown — 기기 지문 없음 → false 반환 (tenant-aware key)")
    void isKnown_memberAbsent_returnsFalse() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.isMember("security:device:known:tenant-a:acc-2", "fp-new")).thenReturn(false);

        assertThat(store.isKnown("tenant-a", "acc-2", "fp-new")).isFalse();
    }

    @Test
    @DisplayName("isKnown — Redis 오류 → fail-open (true 반환, false-positive 방지)")
    void isKnown_redisException_returnsTrueFailOpen() {
        when(redisTemplate.opsForSet()).thenThrow(new RuntimeException("Redis down"));

        assertThat(store.isKnown("tenant-a", "acc-err", "fp-err")).isTrue();
    }

    @Test
    @DisplayName("remember — 정상: SADD 후 expire 호출 (tenant-aware key)")
    void remember_normal_addsAndSetsExpiry() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        store.remember("tenant-a", "acc-4", "fp-xyz");

        verify(setOps).add("security:device:known:tenant-a:acc-4", "fp-xyz");
        verify(redisTemplate).expire("security:device:known:tenant-a:acc-4", Duration.ofDays(90));
    }

    @Test
    @DisplayName("remember — Redis 오류 → 예외 흡수")
    void remember_redisException_exceptionSwallowed() {
        when(redisTemplate.opsForSet()).thenThrow(new RuntimeException("Redis down"));

        store.remember("tenant-a", "acc-err", "fp-err");
    }

    @Test
    @DisplayName("[cross-tenant] 서로 다른 테넌트의 동일 account/fingerprint는 독립적 key 사용")
    void crossTenantIsolation_separateRedisKeys() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        // tenant-a에 등록된 기기가 tenant-b에는 없음
        when(setOps.isMember("security:device:known:tenant-a:acc-1", "fp-shared")).thenReturn(true);
        when(setOps.isMember("security:device:known:tenant-b:acc-1", "fp-shared")).thenReturn(false);

        assertThat(store.isKnown("tenant-a", "acc-1", "fp-shared")).isTrue();
        assertThat(store.isKnown("tenant-b", "acc-1", "fp-shared")).isFalse();
    }
}
