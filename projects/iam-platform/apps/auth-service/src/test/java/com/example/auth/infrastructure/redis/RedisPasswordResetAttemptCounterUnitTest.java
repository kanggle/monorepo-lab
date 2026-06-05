package com.example.auth.infrastructure.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisPasswordResetAttemptCounter 단위 테스트")
class RedisPasswordResetAttemptCounterUnitTest {

    private static final String EMAIL_HASH = "abcdef0123";
    private static final String EXPECTED_KEY = "pwd-reset-rate:" + EMAIL_HASH;
    private static final long WINDOW_SECONDS = 900L;
    private static final int MAX_ATTEMPTS = 3;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private RedisPasswordResetAttemptCounter counter;

    @BeforeEach
    void setUp() {
        counter = new RedisPasswordResetAttemptCounter(redisTemplate, WINDOW_SECONDS, MAX_ATTEMPTS);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
    }

    @Test
    @DisplayName("첫 호출 시 INCR 후 EXPIRE 가 윈도우 길이로 설정된다")
    void firstCall_setsExpire() {
        given(valueOps.increment(EXPECTED_KEY)).willReturn(1L);

        boolean acquired = counter.tryAcquire(EMAIL_HASH);

        assertThat(acquired).isTrue();
        verify(redisTemplate).expire(eq(EXPECTED_KEY), eq(Duration.ofSeconds(WINDOW_SECONDS)));
    }

    @Test
    @DisplayName("후속 호출은 EXPIRE 를 다시 설정하지 않는다 (sliding 아닌 fixed window)")
    void subsequentCall_doesNotResetExpire() {
        given(valueOps.increment(EXPECTED_KEY)).willReturn(2L);

        counter.tryAcquire(EMAIL_HASH);

        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("max 이하 호출은 true 반환")
    void countWithinThreshold_returnsTrue() {
        given(valueOps.increment(EXPECTED_KEY)).willReturn((long) MAX_ATTEMPTS);

        assertThat(counter.tryAcquire(EMAIL_HASH)).isTrue();
    }

    @Test
    @DisplayName("max 초과 호출은 false 반환")
    void countExceedingThreshold_returnsFalse() {
        given(valueOps.increment(EXPECTED_KEY)).willReturn((long) (MAX_ATTEMPTS + 1));

        assertThat(counter.tryAcquire(EMAIL_HASH)).isFalse();
    }

    @Test
    @DisplayName("INCR 가 null 반환 시 fail-open 으로 true")
    void incrementReturnsNull_failsOpen() {
        given(valueOps.increment(EXPECTED_KEY)).willReturn(null);

        assertThat(counter.tryAcquire(EMAIL_HASH)).isTrue();
    }

    @Test
    @DisplayName("Redis 예외 발생 시 fail-open 으로 true 반환 (LoginAttemptCounter 패턴 일관)")
    void redisException_failsOpen() {
        given(valueOps.increment(EXPECTED_KEY)).willThrow(new RedisConnectionFailureException("redis down"));

        assertThat(counter.tryAcquire(EMAIL_HASH)).isTrue();
    }

    @Test
    @DisplayName("키 prefix 가 pwd-reset-rate: 로 시작한다")
    void keyPrefixIsPwdResetRate() {
        given(valueOps.increment(anyString())).willReturn(1L);

        counter.tryAcquire("hash-1");
        counter.tryAcquire("hash-2");

        verify(valueOps, times(1)).increment("pwd-reset-rate:hash-1");
        verify(valueOps, times(1)).increment("pwd-reset-rate:hash-2");
    }
}
