package com.example.security.infrastructure.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisEventDedupStore 단위 테스트")
class RedisEventDedupStoreUnitTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    private RedisEventDedupStore store;

    @BeforeEach
    void setUp() {
        store = new RedisEventDedupStore(redisTemplate);
        ReflectionTestUtils.setField(store, "ttlSeconds", 86400L);
    }

    @Test
    @DisplayName("isDuplicate — Redis에 키 존재 → true 반환")
    void isDuplicate_keyExists_returnsTrue() {
        when(redisTemplate.hasKey("security:event-dedup:evt-1")).thenReturn(true);

        assertThat(store.isDuplicate("evt-1")).isTrue();
    }

    @Test
    @DisplayName("isDuplicate — Redis에 키 없음 → false 반환")
    void isDuplicate_keyAbsent_returnsFalse() {
        when(redisTemplate.hasKey("security:event-dedup:evt-2")).thenReturn(false);

        assertThat(store.isDuplicate("evt-2")).isFalse();
    }

    @Test
    @DisplayName("isDuplicate — hasKey null 반환 → false 반환")
    void isDuplicate_nullResult_returnsFalse() {
        when(redisTemplate.hasKey("security:event-dedup:evt-null")).thenReturn(null);

        assertThat(store.isDuplicate("evt-null")).isFalse();
    }

    @Test
    @DisplayName("isDuplicate — Redis 오류 → fail-open (false 반환)")
    void isDuplicate_redisException_returnsFalse() {
        when(redisTemplate.hasKey(any())).thenThrow(new RuntimeException("Redis down"));

        assertThat(store.isDuplicate("evt-err")).isFalse();
    }

    @Test
    @DisplayName("markProcessed — 정상: set 호출 with TTL")
    void markProcessed_normal_callsSetWithTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        store.markProcessed("evt-3");

        verify(valueOps).set(eq("security:event-dedup:evt-3"), eq("1"), eq(Duration.ofSeconds(86400L)));
    }

    @Test
    @DisplayName("markProcessed — Redis 오류 → 예외 흡수")
    void markProcessed_redisException_exceptionSwallowed() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));

        store.markProcessed("evt-err");

        verify(valueOps, never()).set(any(), any(), any(Duration.class));
    }
}
