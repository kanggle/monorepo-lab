package com.example.auth.infrastructure.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisRefreshTokenStore 단위 테스트")
class RedisRefreshTokenStoreUnitTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    private RedisRefreshTokenStore createStore() {
        return new RedisRefreshTokenStore(redisTemplate, "test");
    }

    @Test
    @DisplayName("invalidate는 Lua script를 통해 단일 원자적 연산으로 실행된다")
    @SuppressWarnings("unchecked")
    void invalidate_executesLuaScript() {
        RedisRefreshTokenStore store = createStore();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        store.invalidate("my-token", 300L);

        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);

        verify(redisTemplate).execute(scriptCaptor.capture(), keysCaptor.capture(), eq("300"));

        List<String> keys = keysCaptor.getValue();
        String hash = sha256Hex("my-token");
        assertThat(keys).containsExactly("test:refresh:" + hash, "test:revoked:" + hash);

        String scriptText = scriptCaptor.getValue().getScriptAsString();
        assertThat(scriptText).contains("DEL", "KEYS[1]");
        assertThat(scriptText).contains("SET", "KEYS[2]");
    }

    private static String sha256Hex(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("invalidate는 DEL 결과가 1이면 true를 반환한다")
    @SuppressWarnings("unchecked")
    void invalidate_returnsTrue_whenDeleted() {
        RedisRefreshTokenStore store = createStore();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(redisTemplate.execute(any(RedisScript.class), any(List.class), any())).willReturn(1L);

        boolean result = store.invalidate("my-token", 300L);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("invalidate는 DEL 결과가 0이면 false를 반환한다 (이미 삭제됨)")
    @SuppressWarnings("unchecked")
    void invalidate_returnsFalse_whenNotFound() {
        RedisRefreshTokenStore store = createStore();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(redisTemplate.execute(any(RedisScript.class), any(List.class), any())).willReturn(0L);

        boolean result = store.invalidate("my-token", 300L);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("중복 invalidate 호출은 멱등적으로 동작한다")
    @SuppressWarnings("unchecked")
    void invalidate_idempotent() {
        RedisRefreshTokenStore store = createStore();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        store.invalidate("my-token", 300L);
        store.invalidate("my-token", 300L);

        verify(redisTemplate, Mockito.times(2)).execute(any(RedisScript.class), any(List.class), eq("300"));
    }

    @Test
    @DisplayName("save 시 보조 인덱스(userId → tokenHash Set)가 함께 저장된다")
    void save_addsTokenHashToUserIndex() {
        RedisRefreshTokenStore store = createStore();
        UUID userId = UUID.randomUUID();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(redisTemplate.opsForSet()).willReturn(setOperations);

        store.save("my-token", userId, 604800L);

        String hash = sha256Hex("my-token");
        then(valueOperations).should().set("test:refresh:" + hash, userId.toString(), Duration.ofSeconds(604800L));
        then(setOperations).should().add("test:user-tokens:" + userId, hash);
        then(redisTemplate).should().expire("test:user-tokens:" + userId, Duration.ofSeconds(604800L));
    }

    @Test
    @DisplayName("findAllTokenHashesByUserId는 해당 userId의 모든 토큰 해시를 반환한다")
    void findAllTokenHashesByUserId_returnsTokenHashes() {
        RedisRefreshTokenStore store = createStore();
        UUID userId = UUID.randomUUID();
        String hash1 = sha256Hex("token1");
        String hash2 = sha256Hex("token2");
        given(redisTemplate.opsForSet()).willReturn(setOperations);
        given(setOperations.members("test:user-tokens:" + userId)).willReturn(Set.of(hash1, hash2));

        Set<String> result = store.findAllTokenHashesByUserId(userId);

        assertThat(result).containsExactlyInAnyOrder(hash1, hash2);
    }

    @Test
    @DisplayName("findAllTokenHashesByUserId는 토큰이 없으면 빈 Set을 반환한다")
    void findAllTokenHashesByUserId_returnsEmpty_whenNoTokens() {
        RedisRefreshTokenStore store = createStore();
        UUID userId = UUID.randomUUID();
        given(redisTemplate.opsForSet()).willReturn(setOperations);
        given(setOperations.members("test:user-tokens:" + userId)).willReturn(null);

        Set<String> result = store.findAllTokenHashesByUserId(userId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("invalidate 시 보조 인덱스에서 tokenHash가 제거된다 (SREM)")
    void invalidate_removesTokenHashFromUserIndex() {
        RedisRefreshTokenStore store = createStore();
        String token = "my-token";
        String hash = sha256Hex(token);
        UUID userId = UUID.randomUUID();

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("test:refresh:" + hash)).willReturn(userId.toString());
        given(redisTemplate.opsForSet()).willReturn(setOperations);

        store.invalidate(token, 300L);

        then(setOperations).should().remove("test:user-tokens:" + userId, hash);
    }

    @Test
    @DisplayName("invalidate 시 userId 조회 실패하면 SREM을 생략한다")
    void invalidate_skipsSrem_whenUserIdNotFound() {
        RedisRefreshTokenStore store = createStore();
        String token = "expired-token";
        String hash = sha256Hex(token);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("test:refresh:" + hash)).willReturn(null);

        store.invalidate(token, 300L);

        then(redisTemplate).should(Mockito.never()).opsForSet();
    }

    @Test
    @DisplayName("invalidateAllByUserId는 모든 토큰을 폐기하고 인덱스를 삭제한다")
    @SuppressWarnings("unchecked")
    void invalidateAllByUserId_invalidatesAllTokensAndDeletesIndex() {
        RedisRefreshTokenStore store = createStore();
        UUID userId = UUID.randomUUID();
        String hash1 = sha256Hex("token1");
        given(redisTemplate.opsForSet()).willReturn(setOperations);
        given(setOperations.members("test:user-tokens:" + userId)).willReturn(Set.of(hash1));

        store.invalidateAllByUserId(userId, 604800L);

        verify(redisTemplate).execute(any(RedisScript.class),
            eq(List.of("test:refresh:" + hash1, "test:revoked:" + hash1)), eq("604800"));
        verify(redisTemplate).delete("test:user-tokens:" + userId);
    }
}
