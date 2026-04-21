package com.example.auth.infrastructure.redis;

import com.example.auth.domain.repository.UserSessionRegistry;
import com.example.auth.domain.service.SessionProperties;
import com.example.auth.infrastructure.util.TokenKeyHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RedisUserSessionRegistry 단위 테스트")
class RedisUserSessionRegistryUnitTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SessionProperties sessionProperties;

    @Mock
    private ZSetOperations<String, String> zSetOps;

    private RedisUserSessionRegistry createRegistry() {
        return new RedisUserSessionRegistry(redisTemplate, sessionProperties, "test");
    }

    @Test
    @DisplayName("registerSession은 Lua 스크립트를 통해 KEYS[1]=sessions:{userId}로 호출한다")
    @SuppressWarnings("unchecked")
    void registerSession_executesLuaScript_withCorrectKey() {
        given(sessionProperties.maxConcurrentSessions()).willReturn(5);
        RedisUserSessionRegistry registry = createRegistry();
        UUID userId = UUID.randomUUID();
        String refreshToken = "my-token";

        registry.registerSession(userId, refreshToken, 604800L);

        ArgumentCaptor<RedisScript<String>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);

        verify(redisTemplate).execute(scriptCaptor.capture(), keysCaptor.capture(),
            any(), any(), any(), any(), any(), any());

        assertThat(keysCaptor.getValue()).containsExactly("test:sessions:" + userId);
        assertThat(scriptCaptor.getValue().getResultType()).isEqualTo(String.class);
    }

    @Test
    @DisplayName("registerSession은 ARGV에 cutoffMillis, maxSessions, nowMillis, newHash, ttl, refreshPrefix를 전달한다")
    @SuppressWarnings("unchecked")
    void registerSession_passesCorrectArgv() {
        given(sessionProperties.maxConcurrentSessions()).willReturn(5);
        RedisUserSessionRegistry registry = createRegistry();
        UUID userId = UUID.randomUUID();
        String refreshToken = "my-token";
        String expectedHash = TokenKeyHasher.sha256Hex(refreshToken);
        long inactivitySeconds = 604800L;

        registry.registerSession(userId, refreshToken, inactivitySeconds);

        ArgumentCaptor<String> arg1 = ArgumentCaptor.forClass(String.class);  // cutoffMillis
        ArgumentCaptor<String> arg2 = ArgumentCaptor.forClass(String.class);  // maxSessions
        ArgumentCaptor<String> arg3 = ArgumentCaptor.forClass(String.class);  // nowMillis
        ArgumentCaptor<String> arg4 = ArgumentCaptor.forClass(String.class);  // newHash
        ArgumentCaptor<String> arg5 = ArgumentCaptor.forClass(String.class);  // ttl
        ArgumentCaptor<String> arg6 = ArgumentCaptor.forClass(String.class);  // refreshPrefix

        verify(redisTemplate).execute(any(RedisScript.class), any(),
            arg1.capture(), arg2.capture(), arg3.capture(), arg4.capture(), arg5.capture(), arg6.capture());

        assertThat(arg2.getValue()).isEqualTo("5");  // maxConcurrentSessions
        assertThat(arg4.getValue()).isEqualTo(expectedHash);
        assertThat(arg5.getValue()).isEqualTo(String.valueOf(inactivitySeconds));
        assertThat(arg6.getValue()).isEqualTo("test:refresh:");
        // cutoffMillis = nowMillis - ttl*1000 (근사 검증)
        long cutoff = Long.parseLong(arg1.getValue());
        long now = Long.parseLong(arg3.getValue());
        assertThat(now - cutoff).isEqualTo(inactivitySeconds * 1000L);
    }

    @Test
    @DisplayName("registerSession은 스크립트 반환값이 null이면 evictedSessionId가 null인 RegistrationResult를 반환한다")
    @SuppressWarnings("unchecked")
    void registerSession_returnsNoEviction_whenScriptReturnsNull() {
        given(sessionProperties.maxConcurrentSessions()).willReturn(5);
        RedisUserSessionRegistry registry = createRegistry();
        Mockito.doReturn(null).when(redisTemplate).execute(any(RedisScript.class), any(List.class), any(), any(), any(), any(), any(), any());

        UserSessionRegistry.RegistrationResult result = registry.registerSession(UUID.randomUUID(), "token", 604800L);

        assertThat(result.evictedSessionId()).isNull();
        assertThat(result.newSessionId()).isEqualTo(TokenKeyHasher.sha256Hex("token"));
    }

    @Test
    @DisplayName("registerSession은 스크립트가 evictedHash를 반환하면 RegistrationResult에 evictedSessionId가 설정된다")
    @SuppressWarnings("unchecked")
    void registerSession_returnsEvictedSessionId_whenEviction() {
        given(sessionProperties.maxConcurrentSessions()).willReturn(5);
        RedisUserSessionRegistry registry = createRegistry();
        String evictedHash = "evicted-hash-value";
        Mockito.doReturn(evictedHash).when(redisTemplate).execute(any(RedisScript.class), any(List.class), any(), any(), any(), any(), any(), any());

        UserSessionRegistry.RegistrationResult result = registry.registerSession(UUID.randomUUID(), "token", 604800L);

        assertThat(result.evictedSessionId()).isEqualTo(evictedHash);
        assertThat(result.newSessionId()).isEqualTo(TokenKeyHasher.sha256Hex("token"));
    }

    @Test
    @DisplayName("rotateSession은 Lua 스크립트를 통해 old hash → new hash로 교체한다")
    @SuppressWarnings("unchecked")
    void rotateSession_executesLuaScript_withHashes() {
        RedisUserSessionRegistry registry = createRegistry();
        UUID userId = UUID.randomUUID();
        String oldToken = "old-token";
        String newToken = "new-token";
        String oldHash = TokenKeyHasher.sha256Hex(oldToken);
        String newHash = TokenKeyHasher.sha256Hex(newToken);

        registry.rotateSession(userId, oldToken, newToken, 604800L);

        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> arg1 = ArgumentCaptor.forClass(String.class);  // oldHash
        ArgumentCaptor<String> arg2 = ArgumentCaptor.forClass(String.class);  // nowMillis
        ArgumentCaptor<String> arg3 = ArgumentCaptor.forClass(String.class);  // newHash
        ArgumentCaptor<String> arg4 = ArgumentCaptor.forClass(String.class);  // ttl

        verify(redisTemplate).execute(scriptCaptor.capture(), keysCaptor.capture(),
            arg1.capture(), arg2.capture(), arg3.capture(), arg4.capture());

        assertThat(keysCaptor.getValue()).containsExactly("test:sessions:" + userId);
        assertThat(arg1.getValue()).isEqualTo(oldHash);
        assertThat(arg3.getValue()).isEqualTo(newHash);
        assertThat(arg4.getValue()).isEqualTo("604800");
    }

    @Test
    @DisplayName("removeSession은 opsForZSet().remove()로 해당 세션 hash를 제거한다")
    void removeSession_callsZSetRemove() {
        RedisUserSessionRegistry registry = createRegistry();
        UUID userId = UUID.randomUUID();
        String refreshToken = "my-token";
        String hash = TokenKeyHasher.sha256Hex(refreshToken);

        given(redisTemplate.opsForZSet()).willReturn(zSetOps);

        registry.removeSession(userId, refreshToken);

        verify(zSetOps).remove(eq("test:sessions:" + userId), eq(hash));
    }
}
