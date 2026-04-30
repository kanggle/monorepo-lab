package com.example.admin.infrastructure.security;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisTokenBlacklistAdapter 단위 테스트")
class RedisTokenBlacklistAdapterUnitTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    private RedisTokenBlacklistAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RedisTokenBlacklistAdapter(redisTemplate);
    }

    @Test
    @DisplayName("blacklist — 정상: 키/값/TTL 로 set 호출")
    void blacklist_normal_setsKeyWithTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        Duration ttl = Duration.ofMinutes(30);

        adapter.blacklist("jti-1", ttl);

        verify(valueOps).set("admin:jti:blacklist:jti-1", "1", ttl);
    }

    @Test
    @DisplayName("blacklist — null TTL → 1초 폴백으로 set 호출")
    void blacklist_nullTtl_usesOneSecondFallback() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        adapter.blacklist("jti-null", null);

        verify(valueOps).set(eq("admin:jti:blacklist:jti-null"), eq("1"), eq(Duration.ofSeconds(1)));
    }

    @Test
    @DisplayName("isBlacklisted — 키 존재 → true")
    void isBlacklisted_keyExists_returnsTrue() {
        when(redisTemplate.hasKey("admin:jti:blacklist:jti-2")).thenReturn(true);

        assertThat(adapter.isBlacklisted("jti-2")).isTrue();
    }

    @Test
    @DisplayName("isBlacklisted — 키 없음 → false")
    void isBlacklisted_keyAbsent_returnsFalse() {
        when(redisTemplate.hasKey("admin:jti:blacklist:jti-3")).thenReturn(false);

        assertThat(adapter.isBlacklisted("jti-3")).isFalse();
    }

    @Test
    @DisplayName("isBlacklisted — Redis 오류 → fail-closed (true 반환)")
    void isBlacklisted_redisException_failClosedReturnsTrue() {
        when(redisTemplate.hasKey(any())).thenThrow(new QueryTimeoutException("Redis timeout"));

        assertThat(adapter.isBlacklisted("jti-err")).isTrue();
    }
}
