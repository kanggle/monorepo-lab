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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RedisTokenBlacklist}.
 *
 * <p>TASK-BE-229: key pattern is now {@code refresh:blacklist:{tenantId}:{jti}}.
 * The legacy single-arg overloads delegate to "fan-platform".
 * {@code isBlacklisted(tenantId, jti)} also checks the legacy key
 * {@code refresh:blacklist:{jti}} for backward compat.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RedisTokenBlacklist 단위 테스트")
class RedisTokenBlacklistUnitTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    private RedisTokenBlacklist blacklist;

    @BeforeEach
    void setUp() {
        blacklist = new RedisTokenBlacklist(redisTemplate);
    }

    // ── blacklist (tenant-aware) ───────────────────────────────────────────────

    @Test
    @DisplayName("blacklist(tenantId, jti, ttl) — 정상: 테넌트 키/값/TTL 로 set 호출")
    void blacklist_tenantAware_setsKeyWithTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        blacklist.blacklist("fan-platform", "jti-abc", 3600L);

        verify(valueOps).set(eq("refresh:blacklist:fan-platform:jti-abc"),
                eq("1"), eq(Duration.ofSeconds(3600L)));
    }

    @Test
    @DisplayName("blacklist(tenantId, jti, ttl) — 다른 테넌트: wms 키 사용")
    void blacklist_tenantAware_wms_setsKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        blacklist.blacklist("wms", "jti-wms", 1800L);

        verify(valueOps).set(eq("refresh:blacklist:wms:jti-wms"),
                eq("1"), eq(Duration.ofSeconds(1800L)));
    }

    @Test
    @DisplayName("blacklist(jti, ttl) — 레거시: fan-platform 키 사용")
    void blacklist_legacy_usesFanPlatformKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        blacklist.blacklist("jti-abc", 3600L);

        verify(valueOps).set(eq("refresh:blacklist:fan-platform:jti-abc"),
                eq("1"), eq(Duration.ofSeconds(3600L)));
    }

    @Test
    @DisplayName("blacklist — Redis 오류 → 예외 흡수 (fail-open)")
    void blacklist_redisException_swallowed() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doThrow(new QueryTimeoutException("Redis timeout"))
                .when(valueOps).set(any(), any(), any(Duration.class));

        blacklist.blacklist("fan-platform", "jti-err", 3600L);
    }

    // ── isBlacklisted (tenant-aware) ───────────────────────────────────────────

    @Test
    @DisplayName("isBlacklisted(tenantId, jti) — 테넌트 키 존재 → true")
    void isBlacklisted_tenantAware_keyExists_returnsTrue() {
        when(redisTemplate.hasKey("refresh:blacklist:fan-platform:jti-1")).thenReturn(true);

        assertThat(blacklist.isBlacklisted("fan-platform", "jti-1")).isTrue();
    }

    @Test
    @DisplayName("isBlacklisted(tenantId, jti) — 레거시 키만 존재 → true (fallback)")
    void isBlacklisted_tenantAware_legacyKeyExists_returnsTrue() {
        when(redisTemplate.hasKey("refresh:blacklist:fan-platform:jti-2")).thenReturn(false);
        when(redisTemplate.hasKey("refresh:blacklist:jti-2")).thenReturn(true);

        assertThat(blacklist.isBlacklisted("fan-platform", "jti-2")).isTrue();
    }

    @Test
    @DisplayName("isBlacklisted(tenantId, jti) — 둘 다 없음 → false")
    void isBlacklisted_tenantAware_keyAbsent_returnsFalse() {
        when(redisTemplate.hasKey("refresh:blacklist:fan-platform:jti-3")).thenReturn(false);
        when(redisTemplate.hasKey("refresh:blacklist:jti-3")).thenReturn(false);

        assertThat(blacklist.isBlacklisted("fan-platform", "jti-3")).isFalse();
    }

    // ── isBlacklisted (legacy single-arg) ─────────────────────────────────────

    @Test
    @DisplayName("isBlacklisted(jti) — 레거시: fan-platform 테넌트 키로 위임")
    void isBlacklisted_legacy_usesFanPlatformDelegate() {
        when(redisTemplate.hasKey("refresh:blacklist:fan-platform:jti-leg")).thenReturn(true);

        assertThat(blacklist.isBlacklisted("jti-leg")).isTrue();
    }

    @Test
    @DisplayName("isBlacklisted — Redis 오류 → fail-closed (true 반환)")
    void isBlacklisted_redisException_failClosed() {
        when(redisTemplate.hasKey(any())).thenThrow(new QueryTimeoutException("Redis timeout"));

        assertThat(blacklist.isBlacklisted("fan-platform", "jti-err")).isTrue();
    }
}
