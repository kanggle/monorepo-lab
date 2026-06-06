package com.example.admin.infrastructure.persistence.rbac;

import com.example.testsupport.integration.DockerAvailableCondition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Acceptance coverage for TASK-BE-028c §Caching:
 *
 * <ul>
 *   <li>2nd call hits Redis, no origin (DB) invocation</li>
 *   <li>TTL expiry → origin invoked exactly once on re-lookup</li>
 *   <li>Explicit {@code invalidate(operatorId)} → origin invoked on next lookup</li>
 *   <li>Redis outage → graceful degrade to origin</li>
 * </ul>
 *
 * <p>Boots an isolated Redis Testcontainer; does not require the full Spring
 * context. The {@link PermissionEvaluatorImpl} origin is a Mockito mock,
 * letting us count DB hits precisely.
 */
@ExtendWith(DockerAvailableCondition.class)
class PermissionEvaluatorCacheTest {

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static LettuceConnectionFactory connectionFactory;
    static StringRedisTemplate redisTemplate;

    private static final String OPERATOR = "00000000-0000-7000-8000-0000000000aa";

    private PermissionEvaluatorImpl origin;
    private CachingPermissionEvaluator caching;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) connectionFactory.destroy();
        REDIS.stop();
    }

    @BeforeEach
    void setUp() {
        // Flush Redis between tests so TTL / key presence are deterministic.
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        origin = mock(PermissionEvaluatorImpl.class);
        when(origin.loadPermissions(OPERATOR))
                .thenReturn(Set.of("account.lock", "account.unlock", "audit.read"));

        // TTL 2s for fast-but-observable expiry assertions.
        caching = new CachingPermissionEvaluator(
                origin, redisTemplate, new ObjectMapper(), 2L, "admin:operator:perm:");
    }

    @Test
    @DisplayName("2회차 hasPermission은 Redis에서 해결되어 DB(origin) 호출이 추가되지 않는다")
    void secondCallIsCacheHitNoDbQuery() {
        assertThat(caching.hasPermission(OPERATOR, "account.lock")).isTrue();
        assertThat(caching.hasPermission(OPERATOR, "account.unlock")).isTrue();
        assertThat(caching.hasPermission(OPERATOR, "audit.read")).isTrue();

        verify(origin, times(1)).loadPermissions(OPERATOR);
    }

    @Test
    @DisplayName("10초(테스트에서는 2초) TTL 경과 후 재조회 시 origin을 1회 재호출한다")
    void reloadsAfterTtlExpiry() {
        caching.hasPermission(OPERATOR, "account.lock");
        // Poll until Redis TTL (2s) expires and the next lookup triggers origin again.
        await().atMost(Duration.ofSeconds(5))
               .pollInterval(Duration.ofMillis(200))
               .untilAsserted(() -> {
                   caching.hasPermission(OPERATOR, "account.lock");
                   verify(origin, times(2)).loadPermissions(OPERATOR);
               });
    }

    @Test
    @DisplayName("invalidate(operatorId) 호출 직후 재조회 시 origin을 재호출한다")
    void invalidateForcesReload() {
        caching.hasPermission(OPERATOR, "account.lock");
        verify(origin, times(1)).loadPermissions(OPERATOR);

        caching.invalidate(OPERATOR);

        caching.hasPermission(OPERATOR, "account.lock");
        verify(origin, times(2)).loadPermissions(OPERATOR);
    }

    @Test
    @DisplayName("Redis 연결 실패 시 DB 경로로 graceful degrade")
    void redisOutageGracefullyDegrades() {
        StringRedisTemplate brokenRedis = mock(StringRedisTemplate.class);
        when(brokenRedis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));
        PermissionEvaluatorImpl localOrigin = mock(PermissionEvaluatorImpl.class);
        AtomicInteger hits = new AtomicInteger();
        doAnswer(inv -> {
            hits.incrementAndGet();
            return Set.of("audit.read");
        }).when(localOrigin).loadPermissions(anyString());

        CachingPermissionEvaluator degraded = new CachingPermissionEvaluator(
                localOrigin, brokenRedis, new ObjectMapper(), 10L, "admin:operator:perm:");

        assertThat(degraded.hasPermission(OPERATOR, "audit.read")).isTrue();
        assertThat(degraded.hasPermission(OPERATOR, "audit.read")).isTrue();

        // Each call falls through to DB when Redis is unreachable.
        assertThat(hits.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("Lettuce RedisCommandTimeoutException 주입 시에도 origin 경로로 degrade (028c-fix)")
    void lettuceCommandTimeoutDegradesToOrigin() {
        StringRedisTemplate brokenRedis = mock(StringRedisTemplate.class);
        when(brokenRedis.opsForValue())
                .thenThrow(new io.lettuce.core.RedisCommandTimeoutException("timeout"));
        PermissionEvaluatorImpl localOrigin = mock(PermissionEvaluatorImpl.class);
        when(localOrigin.loadPermissions(anyString())).thenReturn(Set.of("audit.read"));

        CachingPermissionEvaluator degraded = new CachingPermissionEvaluator(
                localOrigin, brokenRedis, new ObjectMapper(), 10L, "admin:operator:perm:");

        assertThat(degraded.hasPermission(OPERATOR, "audit.read")).isTrue();
        verify(localOrigin, times(1)).loadPermissions(OPERATOR);
    }

    @Test
    @DisplayName("Lettuce RedisException(최상위) 주입 시에도 evaluator 밖으로 예외가 새지 않는다 (028c-fix)")
    void lettuceRedisExceptionDoesNotPropagate() {
        StringRedisTemplate brokenRedis = mock(StringRedisTemplate.class);
        when(brokenRedis.opsForValue())
                .thenThrow(new io.lettuce.core.RedisException("boom"));
        PermissionEvaluatorImpl localOrigin = mock(PermissionEvaluatorImpl.class);
        when(localOrigin.loadPermissions(anyString())).thenReturn(Set.of("audit.read"));

        CachingPermissionEvaluator degraded = new CachingPermissionEvaluator(
                localOrigin, brokenRedis, new ObjectMapper(), 10L, "admin:operator:perm:");

        // Must not throw; must return DB-backed result.
        assertThat(degraded.hasPermission(OPERATOR, "audit.read")).isTrue();
        verify(localOrigin, times(1)).loadPermissions(OPERATOR);
    }
}
