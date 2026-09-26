package com.example.security.service.infrastructure.redis;

import com.example.testsupport.integration.DockerAvailableCondition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-608: real-Redis regression test for {@link RedisTokenReuseCounter}'s atomic
 * {@code INCR}+{@code EXPIRE}.
 *
 * <p>Deliberately does <b>not</b> boot the full security-service Spring context (no
 * {@code @SpringBootTest}) — this class only needs a live Redis, not Kafka/MySQL/the
 * detection pipeline, so it wires a bare {@link StringRedisTemplate} directly against the
 * Testcontainers Redis and constructs {@link RedisTokenReuseCounter} by hand. Skipped
 * automatically when Docker is unavailable ({@link DockerAvailableCondition}, same guard as
 * every other Testcontainers IT in this repo).</p>
 */
@Tag("integration")
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@DisplayName("RedisTokenReuseCounter 통합 테스트 (실제 Redis — TASK-BE-608)")
class RedisTokenReuseCounterIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static LettuceConnectionFactory connectionFactory;
    static StringRedisTemplate redisTemplate;
    static RedisTokenReuseCounter counter;

    @BeforeAll
    static void setUp() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                redis.getHost(), redis.getMappedPort(6379));
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        counter = new RedisTokenReuseCounter(redisTemplate);
    }

    @AfterAll
    static void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    @DisplayName("첫 증가 → 값 1, TTL 이 이미 걸려 있다(같은 스크립트 호출 안에서 — 별도 EXPIRE 호출을 기다릴 필요 없음)")
    void firstIncrement_valueOneAndTtlAlreadySet() {
        String tenantId = "tenant-" + UUID.randomUUID();
        String accountId = "acc-" + UUID.randomUUID();

        long value = counter.incrementAndGet(tenantId, accountId);

        assertThat(value).isEqualTo(1L);
        // If EXPIRE had not run (the old two-round-trip shape's failure mode), this key would
        // have TTL -1 (no expiry — permanent). Checking it immediately, in the same test
        // "tick" as the increment, is exactly the window the old shape could lose.
        assertThat(counter.peek(tenantId, accountId)).isEqualTo(1L);
        assertThat(ttlSecondsOf(tenantId, accountId)).isBetween(1L, 3600L);
    }

    @Test
    @DisplayName("여러 번 빠르게 증가해도 매번 TTL 이 걸려 있다 — 원자적 스크립트라 부분 실패 창이 없다")
    void manyRapidIncrements_ttlNeverMissing() {
        String tenantId = "tenant-" + UUID.randomUUID();
        String accountId = "acc-" + UUID.randomUUID();

        for (int i = 1; i <= 20; i++) {
            long value = counter.incrementAndGet(tenantId, accountId);
            assertThat(value).isEqualTo(i);
            // Every single call, not just the first — a permanent (TTL == -1) key at any
            // point is exactly the TASK-BE-608 defect this test guards against.
            assertThat(ttlSecondsOf(tenantId, accountId))
                    .as("iteration %d must have a live TTL, never permanent (-1)", i)
                    .isBetween(1L, 3600L);
        }
    }

    @Test
    @DisplayName("TTL 은 첫 증가에만 걸리고 이후 증가로 재연장되지 않는다 (고정 창 — BE-606 의미 그대로)")
    void ttlIsSetOnlyOnFirstIncrement_notExtendedBySubsequentOnes() throws InterruptedException {
        String tenantId = "tenant-" + UUID.randomUUID();
        String accountId = "acc-" + UUID.randomUUID();

        counter.incrementAndGet(tenantId, accountId);
        long ttlAfterFirst = ttlSecondsOf(tenantId, accountId);

        Thread.sleep(1100); // let at least one second elapse so a re-armed TTL would be observable

        long secondValue = counter.incrementAndGet(tenantId, accountId);
        long ttlAfterSecond = ttlSecondsOf(tenantId, accountId);

        assertThat(secondValue).isEqualTo(2L);
        // If EXPIRE re-ran on the second increment (a sliding window), ttlAfterSecond would be
        // back up near 3600. It must instead have kept counting down from the first call.
        assertThat(ttlAfterSecond).isLessThan(ttlAfterFirst);
    }

    @Test
    @DisplayName("다른 (tenantId, accountId) 는 독립된 키 — 서로 영향 없음 (실제 Redis)")
    void differentTenantAccountPairs_areIndependentKeys() {
        String tenantA = "tenant-" + UUID.randomUUID();
        String tenantB = "tenant-" + UUID.randomUUID();
        String accountId = "acc-" + UUID.randomUUID();

        counter.incrementAndGet(tenantA, accountId);
        counter.incrementAndGet(tenantA, accountId);
        long countB = counter.incrementAndGet(tenantB, accountId);

        assertThat(counter.peek(tenantA, accountId)).isEqualTo(2L);
        assertThat(countB).isEqualTo(1L);
    }

    private static long ttlSecondsOf(String tenantId, String accountId) {
        Long ttl = redisTemplate.getExpire("reuse:" + tenantId + ":" + accountId, TimeUnit.SECONDS);
        return ttl == null ? -1L : ttl;
    }
}
