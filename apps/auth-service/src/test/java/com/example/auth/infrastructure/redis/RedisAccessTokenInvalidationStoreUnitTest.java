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
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisAccessTokenInvalidationStore 단위 테스트 (TASK-BE-146)")
class RedisAccessTokenInvalidationStoreUnitTest {

    private static final String ACCOUNT_ID = "acc-1";
    private static final String EXPECTED_KEY = "access:invalidate-before:" + ACCOUNT_ID;
    private static final long ACCESS_TTL_SECONDS = 1_800L;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private RedisAccessTokenInvalidationStore store;

    @BeforeEach
    void setUp() {
        store = new RedisAccessTokenInvalidationStore(redisTemplate);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
    }

    @Test
    @DisplayName("invalidateAccessBefore writes epoch-millis at access:invalidate-before:{accountId} with the supplied TTL")
    void writesMarkerWithGatewayCompatibleKey() {
        Instant at = Instant.parse("2026-04-28T12:34:56Z");

        store.invalidateAccessBefore(ACCOUNT_ID, at, ACCESS_TTL_SECONDS);

        verify(valueOps).set(
                eq(EXPECTED_KEY),
                eq(Long.toString(at.toEpochMilli())),
                eq(Duration.ofSeconds(ACCESS_TTL_SECONDS)));
    }

    @Test
    @DisplayName("DataAccessException 은 흡수되어 호출자에게 전파되지 않는다 (fail-soft)")
    void redisExceptionAbsorbed() {
        Instant at = Instant.parse("2026-04-28T12:34:56Z");
        doThrow(new QueryTimeoutException("redis timeout"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));

        // Must not throw — fail-soft contract.
        store.invalidateAccessBefore(ACCOUNT_ID, at, ACCESS_TTL_SECONDS);
    }
}
