package com.example.auth.infrastructure.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisAccessTokenBlocklist 단위 테스트")
class RedisAccessTokenBlocklistTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisAccessTokenBlocklist blocklist;

    @BeforeEach
    void setUp() {
        blocklist = new RedisAccessTokenBlocklist(redisTemplate, "auth");
    }

    @Test
    @DisplayName("block() 후 isBlocked() 는 true를 반환한다")
    void block_then_isBlocked_returnsTrue() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        String token = "test-access-token";
        given(redisTemplate.hasKey(any())).willReturn(true);

        blocklist.block(token, 3600L);

        assertThat(blocklist.isBlocked(token)).isTrue();
    }

    @Test
    @DisplayName("block되지 않은 토큰에 대해 isBlocked() 는 false를 반환한다")
    void isBlocked_returnsFalse_whenNotBlocked() {
        String token = "unknown-token";
        given(redisTemplate.hasKey(any())).willReturn(false);

        assertThat(blocklist.isBlocked(token)).isFalse();
    }

    @Test
    @DisplayName("block() 호출 시 TTL이 설정된다")
    void block_setsTtl() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        String token = "test-token";
        long ttlSeconds = 1800L;

        blocklist.block(token, ttlSeconds);

        verify(valueOperations).set(any(String.class), eq("1"), eq(Duration.ofSeconds(ttlSeconds)));
    }

    @Test
    @DisplayName("hasKey가 null을 반환하면 isBlocked() 는 false를 반환한다")
    void isBlocked_returnsFalse_whenRedisReturnsNull() {
        given(redisTemplate.hasKey(any())).willReturn(null);

        assertThat(blocklist.isBlocked("any-token")).isFalse();
    }

    @Test
    @DisplayName("blockByUserId() 호출 시 userId 키에 TTL이 설정된다")
    void blockByUserId_setsKeyWithTtl() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        UUID userId = UUID.randomUUID();
        long ttlSeconds = 3600L;

        blocklist.blockByUserId(userId, ttlSeconds);

        verify(valueOperations).set(
            eq("auth:blocked-user:" + userId), eq("1"), eq(Duration.ofSeconds(ttlSeconds)));
    }

    @Test
    @DisplayName("차단된 userId에 대해 isUserBlocked() 는 true를 반환한다")
    void isUserBlocked_returnsTrue_whenBlocked() {
        UUID userId = UUID.randomUUID();
        given(redisTemplate.hasKey("auth:blocked-user:" + userId)).willReturn(true);

        assertThat(blocklist.isUserBlocked(userId)).isTrue();
    }

    @Test
    @DisplayName("차단되지 않은 userId에 대해 isUserBlocked() 는 false를 반환한다")
    void isUserBlocked_returnsFalse_whenNotBlocked() {
        UUID userId = UUID.randomUUID();
        given(redisTemplate.hasKey("auth:blocked-user:" + userId)).willReturn(false);

        assertThat(blocklist.isUserBlocked(userId)).isFalse();
    }
}
