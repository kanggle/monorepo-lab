package com.example.testsupport.integration;

import java.time.Duration;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared base class for service integration tests (TASK-BE-076).
 *
 * <h2>Why this class exists</h2>
 *
 * <p>Prior to this change, each integration test class declared its own
 * {@code @Container static MySQLContainer} + {@code @Container static
 * KafkaContainer} and its own {@code @DynamicPropertySource}. Spring's test
 * {@code ContextCache} is keyed by the full context configuration; when a
 * second integration test requested a new cache entry, the Spring context
 * (and therefore the Hikari pool) was rebuilt. The rebuilt pool pointed at
 * the JDBC URL supplied by {@code @DynamicPropertySource}, but the
 * per-class {@code @Container} lifecycle had already stopped the original
 * MySQL container, producing the observed
 * {@code HikariPool-2 Connection is not available ... total=0} +
 * {@code CommunicationsException} failure mode diagnosed from TASK-BE-074
 * CI artifacts.
 *
 * <p>By declaring MySQL and Kafka as {@code static} fields here and
 * starting them exactly once per JVM in a {@code static { }} block, every
 * integration test subclass references the same container instance and the
 * same URL across any number of Spring context rebuilds. The containers
 * outlive every {@code ApplicationContext} in the JVM and are torn down
 * by Testcontainers' JVM shutdown hook (Ryuk) when the test forked JVM
 * exits — we do not need JUnit's {@code @Testcontainers} extension to
 * manage their lifecycle on this class.
 *
 * <h2>Service-specific containers</h2>
 *
 * <p>Additional containers that are not shared across services (Redis,
 * WireMock, Elasticsearch, ...) must be declared on the subclass itself.
 * Subclasses register their extra containers with their own
 * {@code @DynamicPropertySource} method; Spring calls every
 * {@code @DynamicPropertySource} it finds up the class hierarchy, so the
 * MySQL + Kafka properties registered here are merged with the
 * subclass-supplied ones.
 *
 * <h2>Image versions</h2>
 *
 * <p>Pinned to the images already in use across the repository:
 * {@code mysql:8.0} and {@code confluentinc/cp-kafka:7.6.0}. Image bumps
 * should happen here first and propagate automatically to all subclasses.
 *
 * <h2>Docker availability (TASK-BE-101)</h2>
 *
 * <p>The class is annotated with
 * {@link ExtendWith @ExtendWith(DockerAvailableCondition.class)} so JUnit
 * skips every subclass cleanly when the host has no Docker daemon. The
 * {@code static { }} block additionally checks {@link DockerClientFactory}
 * before starting any container — without this guard, the static
 * initializer crashes with {@code IllegalStateException} ->
 * {@code ExceptionInInitializerError} the moment JUnit tries to load a
 * subclass to evaluate any annotation on it. The {@link ExecutionCondition}
 * runs before class init, but defensive null-safe initialization keeps
 * subclasses that might be loaded for other reasons (e.g. reflection in
 * test discovery) from blowing up.
 *
 * @see <a href="file:../../../../../../../../../platform/testing-strategy.md">platform/testing-strategy.md</a>
 */
@ExtendWith(DockerAvailableCondition.class)
public abstract class AbstractIntegrationTest {

    protected static final MySQLContainer<?> MYSQL;
    protected static final KafkaContainer KAFKA;

    static {
        if (isDockerAvailable()) {
            MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("test")
                    .withUsername("test")
                    .withPassword("test")
                    // Services use Flyway migrations that create triggers /
                    // stored functions (e.g. outbox). MySQL 8 rejects those
                    // under binlog unless this flag is set.
                    .withCommand("mysqld", "--log-bin-trust-function-creators=1")
                    .withStartupTimeout(Duration.ofMinutes(3));

            KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
                    // TASK-BE-075: port-listening alone returned before
                    // advertised-listeners propagated. Wait for the broker
                    // startup log line.
                    .waitingFor(Wait.forLogMessage(".*\\[KafkaServer id=\\d+\\] started.*", 1))
                    .withStartupTimeout(Duration.ofMinutes(3));

            MYSQL.start();
            KAFKA.start();
        } else {
            // TASK-BE-101: Docker is not available on this host. Leave the
            // shared containers null and rely on
            // {@link DockerAvailableCondition} to mark every subclass as
            // SKIPPED. The null-safe @DynamicPropertySource below ensures
            // Spring context construction does not NPE if class init still
            // happens for some unrelated reason.
            MYSQL = null;
            KAFKA = null;
        }
    }

    private static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @DynamicPropertySource
    static void sharedContainerProperties(DynamicPropertyRegistry registry) {
        // TASK-BE-101: when Docker is unavailable the shared containers are
        // never started, so do not register their accessors — calling them
        // would NPE. The DockerAvailableCondition aborts the test before any
        // method executes; this guard is purely defensive against partial
        // class init.
        if (MYSQL == null || KAFKA == null) {
            return;
        }
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}
