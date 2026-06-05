package com.example.auth.infrastructure.redis;

import com.example.auth.domain.oauth.OAuthProvider;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisOAuthStateStore 단위 테스트 (TASK-BE-147)")
class RedisOAuthStateStoreUnitTest {

    private static final String STATE = "state-uuid";
    // Reference the adapter's own constant so a prefix change is caught here too.
    private static final String EXPECTED_KEY = RedisOAuthStateStore.KEY_PREFIX + STATE;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private RedisOAuthStateStore store;

    @BeforeEach
    void setUp() {
        store = new RedisOAuthStateStore(redisTemplate);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
    }

    @Test
    @DisplayName("store writes oauth:state:{state} → provider.name() with the spec-pinned TTL")
    void store_writesKeyWithSpecTtl() {
        store.store(STATE, OAuthProvider.GOOGLE);

        verify(valueOps).set(eq(EXPECTED_KEY), eq("GOOGLE"), eq(RedisOAuthStateStore.STATE_TTL));
    }

    @Test
    @DisplayName("store DataAccessException propagates (fail-closed) — authorize 가 실패하면 callback 이 INVALID_STATE 로 reject 되어야 함")
    void store_redisFailurePropagate() {
        doThrow(new QueryTimeoutException("redis timeout"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));

        assertThatThrownBy(() -> store.store(STATE, OAuthProvider.GOOGLE))
                .isInstanceOf(QueryTimeoutException.class);
    }

    @Test
    @DisplayName("consumeAtomic returns mapped provider when GETDEL hits")
    void consumeAtomic_hit() {
        given(valueOps.getAndDelete(EXPECTED_KEY)).willReturn("MICROSOFT");

        Optional<OAuthProvider> result = store.consumeAtomic(STATE);

        assertThat(result).contains(OAuthProvider.MICROSOFT);
    }

    @Test
    @DisplayName("consumeAtomic returns empty when state is unknown / expired / already consumed")
    void consumeAtomic_miss() {
        given(valueOps.getAndDelete(EXPECTED_KEY)).willReturn(null);

        assertThat(store.consumeAtomic(STATE)).isEmpty();
    }

    @Test
    @DisplayName("consumeAtomic returns empty for a malformed stored value (not a known provider)")
    void consumeAtomic_unknownProviderValue() {
        given(valueOps.getAndDelete(EXPECTED_KEY)).willReturn("UNKNOWN_PROVIDER");

        assertThat(store.consumeAtomic(STATE)).isEmpty();
    }

    @Test
    @DisplayName("DataAccessException propagates (fail-closed) — backing-store outage must not be silently absorbed")
    void consumeAtomic_redisFailureFailsClosed() {
        given(valueOps.getAndDelete(EXPECTED_KEY))
                .willThrow(new QueryTimeoutException("redis timeout"));

        assertThatThrownBy(() -> store.consumeAtomic(STATE))
                .isInstanceOf(QueryTimeoutException.class);
    }
}
