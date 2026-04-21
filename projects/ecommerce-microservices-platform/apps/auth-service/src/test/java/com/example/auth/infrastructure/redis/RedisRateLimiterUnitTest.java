package com.example.auth.infrastructure.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisRateLimiter 단위 테스트")
class RedisRateLimiterUnitTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private RedisRateLimiter createLimiter() {
        return new RedisRateLimiter(redisTemplate, "test");
    }

    @Test
    @DisplayName("isRateLimited는 현재 카운트가 maxRequests 이하이면 false를 반환한다")
    @SuppressWarnings("unchecked")
    void isRateLimited_underLimit_returnsFalse() {
        RedisRateLimiter limiter = createLimiter();
        String clientKey = "user:login:127.0.0.1";
        int maxRequests = 5;
        long windowSeconds = 60L;
        String expectedKey = "test:ratelimit:" + clientKey;

        given(redisTemplate.execute(any(RedisScript.class), eq(List.of(expectedKey)), eq("60")))
            .willReturn(3L);

        boolean result = limiter.isRateLimited(clientKey, maxRequests, windowSeconds);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isRateLimited는 현재 카운트가 maxRequests를 초과하면 true를 반환한다")
    @SuppressWarnings("unchecked")
    void isRateLimited_overLimit_returnsTrue() {
        RedisRateLimiter limiter = createLimiter();
        String clientKey = "user:login:127.0.0.1";
        int maxRequests = 5;
        long windowSeconds = 60L;
        String expectedKey = "test:ratelimit:" + clientKey;

        given(redisTemplate.execute(any(RedisScript.class), eq(List.of(expectedKey)), eq("60")))
            .willReturn(6L);

        boolean result = limiter.isRateLimited(clientKey, maxRequests, windowSeconds);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isRateLimited는 Redis 오류 발생 시 fail-open으로 false를 반환한다")
    @SuppressWarnings("unchecked")
    void isRateLimited_redisError_returnsFalse() {
        RedisRateLimiter limiter = createLimiter();
        String clientKey = "user:login:127.0.0.1";

        given(redisTemplate.execute(any(RedisScript.class), any(List.class), any(String.class)))
            .willThrow(new QueryTimeoutException("Redis timeout"));

        boolean result = limiter.isRateLimited(clientKey, 5, 60L);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isRateLimited는 스크립트 결과가 null이면 fail-open으로 false를 반환한다")
    @SuppressWarnings("unchecked")
    void isRateLimited_scriptReturnsNull_returnsFalse() {
        RedisRateLimiter limiter = createLimiter();
        String clientKey = "user:login:127.0.0.1";

        given(redisTemplate.execute(any(RedisScript.class), any(List.class), any(String.class)))
            .willReturn(null);

        boolean result = limiter.isRateLimited(clientKey, 5, 60L);

        assertThat(result).isFalse();
    }
}
