package com.wms.outbound.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.redis.testcontainers.RedisContainer;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared infrastructure for outbound-service {@code @SpringBootTest} integration
 * tests (TASK-BE-049 onward). Boots Postgres + Kafka + Redis + a per-class
 * WireMock server, then wires the WireMock URL into {@code outbound.tms.base-url}
 * so the real {@link com.wms.outbound.adapter.out.tms.TmsClientAdapter} talks to
 * the fake TMS instance.
 *
 * <p>Tagged {@code integration} so it runs only via the {@code integrationTest}
 * Gradle task. Mirrors the inventory / inbound bases — same Postgres / Kafka /
 * Redis topology + the additional TMS-dedicated WireMock.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration")
@ContextConfiguration(initializers = OutboundServiceIntegrationBase.Initializer.class)
@ExtendWith(org.testcontainers.junit.jupiter.TestcontainersExtension.class)
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
public abstract class OutboundServiceIntegrationBase {

    protected static final Network NETWORK = Network.newNetwork();

    @SuppressWarnings("resource")
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withNetwork(NETWORK)
                    .withNetworkAliases("postgres")
                    .withDatabaseName("outbound_it")
                    .withUsername("outbound_it")
                    .withPassword("outbound_it");

    @SuppressWarnings("resource")
    protected static final ConfluentKafkaContainer KAFKA =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))
                    .withNetwork(NETWORK)
                    .withNetworkAliases("kafka")
                    .withStartupTimeout(Duration.ofMinutes(2));

    @SuppressWarnings("resource")
    protected static final RedisContainer REDIS =
            new RedisContainer(DockerImageName.parse("redis:7-alpine"))
                    .withNetwork(NETWORK)
                    .withNetworkAliases("redis");

    /**
     * Class-level WireMock — the TMS sandbox endpoint. Started before the
     * Spring context so the {@link Initializer} can inject its URL.
     */
    protected static final WireMockServer WIREMOCK =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    /**
     * Every topic an outbound-service {@code @KafkaListener} subscribes to, with the
     * default names the ITs run under (no property overrides in the test profile).
     *
     * <p>Pinned against the live listener registry by
     * {@code ListenerTopicsPrecreatedIT} — add a {@code @KafkaListener} without adding
     * its topic here and that test fails. A hand-kept list with nothing checking it is
     * how this would quietly go stale.
     */
    protected static final List<String> LISTENER_TOPICS = List.of(
            "ecommerce.fulfillment.requested.v1",
            "ecommerce.shipping.manual-confirm-requested.v1",
            "wms.inventory.confirmed.v1",
            "wms.inventory.released.v1",
            "wms.inventory.reserved.v1",
            "wms.inventory.reserve.failed.v1",
            "wms.master.warehouse.v1",
            "wms.master.zone.v1",
            "wms.master.location.v1",
            "wms.master.sku.v1",
            "wms.master.partner.v1",
            "wms.master.lot.v1");

    static {
        WIREMOCK.start();
        POSTGRES.start();
        KAFKA.start();
        REDIS.start();
        precreateListenerTopics();
    }

    /**
     * Create every listener topic <b>before the Spring context starts</b> (TASK-BE-504).
     *
     * <p>All 12 {@code @KafkaListener} consumers share one group ({@code outbound-service}).
     * Left to themselves they subscribe to topics that do not exist yet, Kafka auto-creates
     * each one lazily, and every appearance changes the group's subscription metadata — so
     * the group rebalances repeatedly while the suite starts up. On a quiet host that
     * settles in well under a second and nobody notices. On a contended CI runner it does
     * not always settle before {@code ContainerTestUtils.waitForAssignment} gives up, and
     * the test dies with the message the Gradle console does not print:
     *
     * <pre>java.lang.IllegalStateException: Expected 1 but got 0 partitions</pre>
     *
     * <p>Creating the whole topic set up front collapses that startup cascade into a single
     * rebalance: the subscription metadata is already final when the first consumer joins.
     *
     * <p><b>What this is not.</b> It is not a longer timeout — it removes a source of
     * rebalance churn rather than waiting out the symptom. But it is also <b>not a proven
     * fix</b>: the CI failure could not be reproduced locally (the full outbound suite is
     * green on a quiet host, 26/26), so a green CI run after this change is not evidence
     * that this was the cause. The failure signature stays on watch — see TASK-BE-504.
     */
    private static void precreateListenerTopics() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        try (AdminClient admin = AdminClient.create(props)) {
            admin.createTopics(LISTENER_TOPICS.stream()
                            .map(t -> new NewTopic(t, 1, (short) 1))
                            .toList())
                    .all()
                    .get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (!(e.getCause() instanceof TopicExistsException)) {
                throw new IllegalStateException("Failed to pre-create listener topics", e);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted pre-creating listener topics", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to pre-create listener topics", e);
        }
    }

    @AfterAll
    static void resetWireMock() {
        WIREMOCK.resetAll();
    }

    /**
     * Start the one {@code @KafkaListener} container that subscribes to {@code topic}, and
     * block until it holds its partition (TASK-MONO-376).
     *
     * <p>Listener containers do not auto-start in this profile — see the {@code
     * spring.kafka.listener.auto-startup=false} note in {@link Initializer}. A test that
     * needs a listener starts exactly that one, so the consumer group has a single member
     * with a single subscription and settles in one rebalance. Starting all twelve, as the
     * default did, makes each member's join revoke every other member's assignment, and the
     * container a test is waiting on loses its partition as fast as it gains it.
     *
     * <p>Idempotent: starting an already-running container is a no-op, so a test may call
     * this per-method without tracking state.
     */
    protected static void startAndAwaitListener(KafkaListenerEndpointRegistry registry, String topic) {
        MessageListenerContainer container = registry.getListenerContainers().stream()
                .filter(c -> {
                    String[] topics = c.getContainerProperties().getTopics();
                    return topics != null && Arrays.asList(topics).contains(topic);
                })
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No @KafkaListener container subscribed to topic " + topic));

        if (!container.isRunning()) {
            container.start();
        }
        ContainerTestUtils.waitForAssignment(container, 1);
    }

    public static class Initializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            TestPropertyValues.of(
                    "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                    "spring.datasource.username=" + POSTGRES.getUsername(),
                    "spring.datasource.password=" + POSTGRES.getPassword(),
                    "spring.jpa.hibernate.ddl-auto=validate",
                    "spring.flyway.enabled=true",
                    "spring.flyway.locations=classpath:db/migration",
                    "spring.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers(),
                    // Deterministic publish→@KafkaListener consumption for the
                    // cross-project fulfillment IT: read from the beginning, and
                    // refresh metadata fast so a topic created in @BeforeEach is
                    // discovered well within the test's await (default
                    // metadata.max.age is 5 min). Complements the test's explicit
                    // topic pre-creation + waitForAssignment.
                    "spring.kafka.consumer.auto-offset-reset=earliest",
                    "spring.kafka.consumer.properties.metadata.max.age.ms=2000",
                    // TASK-MONO-376: do not auto-start the listener containers.
                    //
                    // All 12 @KafkaListener methods inherit one group-id
                    // (`outbound-service`, application.yml) and each subscribes to a
                    // DIFFERENT topic. Twelve members of one consumer group with twelve
                    // disjoint subscriptions is a rebalance engine: every member that
                    // joins revokes the assignments of all the others, so the group
                    // churns while it converges — and a test awaiting one specific
                    // container watches its partition get taken away again and again.
                    //
                    // Measured, not assumed. Same suite, same code:
                    //   quiet host (4 CPU) : 34 join attempts, 12 successful joins  -> converges, passes
                    //   CI runner          : 45 join attempts,  1 successful join   -> never converges, times out
                    //     (FulfillmentRequestedConsumerIT / InventoryReserveFailedConsumerIT,
                    //      "Expected 1 but got 0 partitions" — three separate PRs whose diffs
                    //      touched no wms code at all: MONO-354, MONO-362, MONO-371.)
                    //
                    // TASK-BE-504 removed the *topic* churn (twelve topics being lazily
                    // auto-created one at a time). It could not remove the *member* churn,
                    // which is what this is, and it said so — it kept the failure on watch
                    // rather than claiming a green run as proof. This is that follow-up.
                    //
                    // With auto-start off, the group has zero members until a test starts
                    // the one container it needs: one member, one subscription, one
                    // rebalance, immediate assignment. Nothing is masked — the churn is
                    // gone, not waited out. Only three ITs use a listener at all
                    // (Fulfillment, InventoryReserveFailed, ListenerTopicsPrecreated); the
                    // other eight never even produce to Kafka, and were paying for a
                    // twelve-member rebalance storm they had no use for.
                    //
                    // Production is untouched: there every listener starts, as it should.
                    //
                    // Falsifiable, and falsified on purpose: flipping this one value to
                    // `true` brings the churn straight back — 1 join attempt becomes 22 on
                    // the same host, same suite. That is the revert-brings-it-back evidence
                    // TASK-MONO-376 AC-1 demands; without it this would be a correlation.
                    "spring.kafka.listener.auto-startup=false",
                    "spring.data.redis.host=" + REDIS.getHost(),
                    "spring.data.redis.port=" + REDIS.getFirstMappedPort(),
                    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:0/.well-known/jwks.json",
                    "wms.oauth2.allowed-issuers=http://localhost:8081,iam",
                    "wms.oauth2.required-tenant-id=wms",
                    // TASK-BE-049: point the TMS adapter at WireMock.
                    "outbound.tms.base-url=" + WIREMOCK.baseUrl() + "/tms",
                    "outbound.tms.api-key=test-api-key",
                    "outbound.tms.connect-timeout-ms=2000",
                    "outbound.tms.read-timeout-ms=2000",
                    "outbound.tms.max-connections=10",
                    // Fast retry for tests — production keeps 1s/2s/4s.
                    "resilience4j.retry.instances.tms-client.waitDuration=50ms",
                    "resilience4j.retry.instances.tms-client.exponentialBackoffMultiplier=2",
                    "resilience4j.retry.instances.tms-client.randomizedWaitFactor=0",
                    // Circuit-breaker tuning for tests.
                    //
                    // The default Resilience4j aspect order makes @Retry wrap
                    // @CircuitBreaker, so EACH retry attempt passes through the
                    // breaker. (The @Retry fallbackMethod — bound on the OUTER
                    // aspect in TmsClientAdapter — only fires after all 3 retry
                    // attempts exhaust; a fallback on the inner @CircuitBreaker
                    // would convert the first TmsTransientException to the
                    // non-retryable ExternalServiceUnavailableException and the
                    // burst would stop at 1 call.)
                    //
                    // If minimumNumberOfCalls is too low the breaker OPENS
                    // partway through a single 3-attempt retry burst — the
                    // remaining attempt(s) then short-circuit with
                    // CallNotPermittedException (a retry ignoreException), so the
                    // burst stops early and WireMock sees < 3 calls. Scenarios 2
                    // (timeout) and 3 (5xx) assert exactly 3 HTTP calls, so the
                    // breaker MUST stay closed across a single burst (3 failures).
                    //
                    // minimumNumberOfCalls=4 (> the 3-attempt burst) keeps the
                    // breaker closed during one notify(), while scenario 5's
                    // 4-iteration loop still drives ≥4 failures and opens it on
                    // the 2nd notify(). slidingWindowSize=10 holds enough samples
                    // that the 100%-failure rate is evaluated as soon as the
                    // minimum is reached.
                    "resilience4j.circuitbreaker.instances.tms-client.minimumNumberOfCalls=4",
                    "resilience4j.circuitbreaker.instances.tms-client.slidingWindowSize=10",
                    "resilience4j.circuitbreaker.instances.tms-client.waitDurationInOpenState=2s"
            ).applyTo(context.getEnvironment());
        }
    }
}
