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
    @DisplayName("isKnown — 기기 지문 존재 → true 반환")
    void isKnown_memberExists_returnsTrue() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.isMember("security:device:seen:acc-1", "fp-abc")).thenReturn(true);

        assertThat(store.isKnown("acc-1", "fp-abc")).isTrue();
    }

    @Test
    @DisplayName("isKnown — 기기 지문 없음 → false 반환")
    void isKnown_memberAbsent_returnsFalse() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.isMember("security:device:seen:acc-2", "fp-new")).thenReturn(false);

        assertThat(store.isKnown("acc-2", "fp-new")).isFalse();
    }

    @Test
    @DisplayName("isKnown — Redis 오류 → fail-open (true 반환, false-positive 방지)")
    void isKnown_redisException_returnsTrueFailOpen() {
        when(redisTemplate.opsForSet()).thenThrow(new RuntimeException("Redis down"));

        assertThat(store.isKnown("acc-err", "fp-err")).isTrue();
    }

    @Test
    @DisplayName("remember — 정상: SADD 후 expire 호출")
    void remember_normal_addsAndSetsExpiry() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        store.remember("acc-4", "fp-xyz");

        verify(setOps).add("security:device:seen:acc-4", "fp-xyz");
        verify(redisTemplate).expire("security:device:seen:acc-4", Duration.ofDays(90));
    }

    @Test
    @DisplayName("remember — Redis 오류 → 예외 흡수")
    void remember_redisException_exceptionSwallowed() {
        when(redisTemplate.opsForSet()).thenThrow(new RuntimeException("Redis down"));

        store.remember("acc-err", "fp-err");
    }
}
