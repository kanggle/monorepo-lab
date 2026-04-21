package com.example.auth.infrastructure.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisOAuthStateStore 단위 테스트")
class RedisOAuthStateStoreUnitTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisOAuthStateStore createStore() {
        return new RedisOAuthStateStore(redisTemplate, "test");
    }

    @Test
    @DisplayName("save는 callbackUrl을 TTL과 함께 Redis에 저장한다")
    void save_storesCallbackUrlWithTtl() {
        RedisOAuthStateStore store = createStore();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        String state = "abc123";
        String callbackUrl = "https://example.com/callback";
        Duration ttl = Duration.ofMinutes(10);

        store.save(state, callbackUrl, ttl);

        then(valueOperations).should().set("test:oauth:state:" + state, callbackUrl, ttl);
    }

    @Test
    @DisplayName("getAndDelete는 키가 존재하면 값을 반환하고 삭제한다")
    void getAndDelete_keyExists_returnsValueAndDeletes() {
        RedisOAuthStateStore store = createStore();
        String state = "abc123";
        String callbackUrl = "https://example.com/callback";

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.getAndDelete("test:oauth:state:" + state)).willReturn(callbackUrl);

        Optional<String> result = store.getAndDelete(state);

        assertThat(result).isPresent().contains(callbackUrl);
        then(valueOperations).should().getAndDelete("test:oauth:state:" + state);
    }

    @Test
    @DisplayName("getAndDelete는 키가 없으면 빈 Optional을 반환한다")
    void getAndDelete_keyMissing_returnsEmpty() {
        RedisOAuthStateStore store = createStore();
        String state = "nonexistent";

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.getAndDelete("test:oauth:state:" + state)).willReturn(null);

        Optional<String> result = store.getAndDelete(state);

        assertThat(result).isEmpty();
    }
}
