package com.example.account.infrastructure.redis;

import com.example.account.domain.repository.EmailVerificationTokenStore;
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
import static org.mockito.ArgumentMatchers.anyString;
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
    @DisplayName("save — 정상: 키/값(tenant|account)/TTL 로 set 호출")
    void save_normal_setsKeyWithTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        Duration ttl = Duration.ofHours(24);

        store.save("token-abc", "ecommerce", "acc-1", ttl);

        // TASK-BE-507: the tenant rides on the value so the token-authenticated verify path
        // can scope its account lookup.
        verify(valueOps).set("email-verify:token-abc", "ecommerce|acc-1", ttl);
    }

    @Test
    @DisplayName("save — Redis 오류 → 예외 전파 (fail-closed)")
    void save_redisException_propagates() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doThrow(new QueryTimeoutException("Redis timeout"))
                .when(valueOps).set(eq("email-verify:token-err"), anyString(), any(Duration.class));

        assertThatThrownBy(() -> store.save("token-err", "fan-platform", "acc-x", Duration.ofHours(24)))
                .isInstanceOf(QueryTimeoutException.class);
    }

    // ── findSubject ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findSubject — 키 존재 → (tenant, accountId) 반환")
    void findSubject_keyExists_returnsTenantAndAccountId() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("email-verify:token-1")).thenReturn("ecommerce|acc-1");

        assertThat(store.findSubject("token-1"))
                .contains(new EmailVerificationTokenStore.Subject("ecommerce", "acc-1"));
    }

    @Test
    @DisplayName("TASK-BE-507: 구분자 없는 값 = BE-507 이전에 발급된 토큰 → fan-platform (TTL 24h 안의 인-플라이트 토큰이 안 깨진다)")
    void findSubject_legacyValueWithoutTenant_fallsBackToFanPlatform() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("email-verify:token-legacy")).thenReturn("acc-legacy");

        assertThat(store.findSubject("token-legacy"))
                .contains(new EmailVerificationTokenStore.Subject("fan-platform", "acc-legacy"));
    }

    @Test
    @DisplayName("findSubject — 키 없음 → Optional.empty()")
    void findSubject_keyAbsent_returnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("email-verify:token-none")).thenReturn(null);

        assertThat(store.findSubject("token-none")).isEmpty();
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
