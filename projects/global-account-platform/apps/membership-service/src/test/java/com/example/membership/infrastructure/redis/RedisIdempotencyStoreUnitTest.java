package com.example.membership.infrastructure.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisIdempotencyStore — fail-open on Redis outage")
class RedisIdempotencyStoreUnitTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private RedisIdempotencyStore store;

    @BeforeEach
    void setUp() {
        store = new RedisIdempotencyStore(redis, 86400);
    }

    @Test
    @DisplayName("get — Redis 연결 실패 시 cache miss 로 degrade (Optional.empty)")
    void get_whenRedisDown_returnsEmpty() {
        given(redis.opsForValue()).willReturn(valueOps);
        given(valueOps.get(anyString()))
                .willThrow(new RedisConnectionFailureException("cannot connect"));

        Optional<String> result = store.get("some-key");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("putIfAbsent — Redis 연결 실패 시 true (fail-open, DB 유일성이 최종 가드)")
    void putIfAbsent_whenRedisDown_returnsTrue() {
        given(redis.opsForValue()).willReturn(valueOps);
        willThrow(new RedisConnectionFailureException("cannot connect"))
                .given(valueOps).setIfAbsent(anyString(), any(), any(Duration.class));

        boolean result = store.putIfAbsent("some-key", "sub-123");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("get — Redis hit 정상 경로 회귀 검증")
    void get_whenKeyExists_returnsValue() {
        given(redis.opsForValue()).willReturn(valueOps);
        given(valueOps.get("membership:idem:some-key")).willReturn("sub-42");

        Optional<String> result = store.get("some-key");

        assertThat(result).contains("sub-42");
    }

    @Test
    @DisplayName("putIfAbsent — Redis write 성공 시 true 반환 (실제 저장)")
    void putIfAbsent_whenSetSucceeds_returnsTrue() {
        given(redis.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).willReturn(true);

        boolean result = store.putIfAbsent("some-key", "sub-42");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("putIfAbsent — key 이미 존재 시 false 반환 (기존 동작 유지)")
    void putIfAbsent_whenKeyExists_returnsFalse() {
        given(redis.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).willReturn(false);

        boolean result = store.putIfAbsent("some-key", "sub-42");

        assertThat(result).isFalse();
    }
}
