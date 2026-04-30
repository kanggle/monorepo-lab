package com.example.account.infrastructure.redis;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisEmailVerificationTokenStore 단위 테스트")
class RedisEmailVerificationTokenStoreUnitTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    private RedisEmailVerificationTokenStore store;

    @BeforeEach
    void setUp() {
        store = new RedisEmailVerificationTokenStore(redisTemplate);
    }

    // ── save ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("save — 정상: 키/값/TTL 로 set 호출")
    void save_normal_setsKeyWithTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        Duration ttl = Duration.ofHours(24);

        store.save("token-abc", "acc-1", ttl);

        verify(valueOps).set("email-verify:token-abc", "acc-1", ttl);
    }

    @Test
    @DisplayName("save — Redis 오류 → 예외 전파 (fail-closed)")
    void save_redisException_propagates() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doThrow(new QueryTimeoutException("Redis timeout"))
                .when(valueOps).set(eq("email-verify:token-err"), eq("acc-x"), any(Duration.class));

        assertThatThrownBy(() -> store.save("token-err", "acc-x", Duration.ofHours(24)))
                .isInstanceOf(QueryTimeoutException.class);
    }

    // ── findAccountId ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("findAccountId — 키 존재 → accountId 반환")
    void findAccountId_keyExists_returnsAccountId() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("email-verify:token-1")).thenReturn("acc-1");

        assertThat(store.findAccountId("token-1")).contains("acc-1");
    }

    @Test
    @DisplayName("findAccountId — 키 없음 → Optional.empty()")
    void findAccountId_keyAbsent_returnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("email-verify:token-none")).thenReturn(null);

        assertThat(store.findAccountId("token-none")).isEmpty();
    }

    // ── delete ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete — 정상: 키 삭제 호출")
    void delete_normal_deletesKey() {
        store.delete("token-del");

        verify(redisTemplate).delete("email-verify:token-del");
    }

    // ── tryAcquireResendSlot ───────────────────────────────────────────────────

    @Test
    @DisplayName("tryAcquireResendSlot — setIfAbsent true → 슬롯 획득 (true)")
    void tryAcquireResendSlot_acquired_returnsTrue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("email-verify:rate:acc-1"), eq("1"), any(Duration.class)))
                .thenReturn(true);

        assertThat(store.tryAcquireResendSlot("acc-1", Duration.ofSeconds(300))).isTrue();
    }

    @Test
    @DisplayName("tryAcquireResendSlot — setIfAbsent false → 슬롯 이미 존재 (false)")
    void tryAcquireResendSlot_alreadyExists_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("email-verify:rate:acc-2"), eq("1"), any(Duration.class)))
                .thenReturn(false);

        assertThat(store.tryAcquireResendSlot("acc-2", Duration.ofSeconds(300))).isFalse();
    }

    @Test
    @DisplayName("tryAcquireResendSlot — setIfAbsent null → false (Boolean.TRUE.equals(null))")
    void tryAcquireResendSlot_nullResult_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("email-verify:rate:acc-3"), eq("1"), any(Duration.class)))
                .thenReturn(null);

        assertThat(store.tryAcquireResendSlot("acc-3", Duration.ofSeconds(300))).isFalse();
    }

    @Test
    @DisplayName("tryAcquireResendSlot — Redis 오류 → fail-open (true 반환)")
    void tryAcquireResendSlot_redisException_failOpen() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(any(), any(), any(Duration.class)))
                .thenThrow(new QueryTimeoutException("Redis timeout"));

        assertThat(store.tryAcquireResendSlot("acc-err", Duration.ofSeconds(300))).isTrue();
    }
}
