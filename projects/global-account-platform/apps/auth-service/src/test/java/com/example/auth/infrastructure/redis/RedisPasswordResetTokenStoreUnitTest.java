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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisPasswordResetTokenStore 단위 테스트")
class RedisPasswordResetTokenStoreUnitTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    private RedisPasswordResetTokenStore store;

    @BeforeEach
    void setUp() {
        store = new RedisPasswordResetTokenStore(redisTemplate);
    }

    @Test
    @DisplayName("save — 정상: 키/값/TTL 로 set 호출")
    void save_normal_setsKeyWithTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        Duration ttl = Duration.ofHours(1);

        store.save("token-abc", "acc-1", ttl);

        verify(valueOps).set("pwd-reset:token-abc", "acc-1", ttl);
    }

    @Test
    @DisplayName("save — Redis 오류 → 예외 전파 (fail-closed)")
    void save_redisException_propagates() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doThrow(new QueryTimeoutException("Redis timeout"))
                .when(valueOps).set(eq("pwd-reset:token-err"), eq("acc-x"), eq(Duration.ofHours(1)));

        assertThatThrownBy(() -> store.save("token-err", "acc-x", Duration.ofHours(1)))
                .isInstanceOf(QueryTimeoutException.class);
    }

    @Test
    @DisplayName("findAccountId — 키 존재 → accountId 반환")
    void findAccountId_keyExists_returnsAccountId() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("pwd-reset:token-1")).thenReturn("acc-1");

        Optional<String> result = store.findAccountId("token-1");

        assertThat(result).contains("acc-1");
    }

    @Test
    @DisplayName("findAccountId — 키 없음 → Optional.empty()")
    void findAccountId_keyAbsent_returnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("pwd-reset:token-none")).thenReturn(null);

        assertThat(store.findAccountId("token-none")).isEmpty();
    }

    @Test
    @DisplayName("delete — 정상: 키 삭제 호출")
    void delete_normal_deletesKey() {
        store.delete("token-del");

        verify(redisTemplate).delete("pwd-reset:token-del");
    }
}
