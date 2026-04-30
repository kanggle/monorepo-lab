package com.example.testsupport.integration;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * JUnit 5 {@link ExecutionCondition} that disables a test container when Docker
 * is not available on the host (TASK-BE-101).
 *
 * <h2>Why this exists</h2>
 *
 * <p>{@link AbstractIntegrationTest} starts MySQL/Kafka Testcontainers in a
 * {@code static { }} block so the containers outlive any single Spring
 * {@code ApplicationContext} (see TASK-BE-076 / TASK-BE-077). That is the right
 * call for production CI, but on developer machines without Docker the static
 * initializer threw {@code IllegalStateException} during container startup and
 * the test class failed with {@code ExceptionInInitializerError} — even when a
 * subclass tried to gate itself with
 * {@code @EnabledIf("isDockerAvailable")}, because evaluating that annotation
 * requires invoking the method, which triggers class initialization, which
 * triggers the parent static block before the condition has a chance to skip
 * the test.
 *
 * <p>An {@link ExecutionCondition} runs <em>before</em> JUnit invokes any
 * static method on the test class, so we can probe Docker via
 * {@link DockerClientFactory#isDockerAvailable()} (a static method on a
 * Testcontainers utility class — no test-class loading involved) and disable
 * the test before its enclosing class is initialised. When Docker is
 * unavailable JUnit reports the test as <em>SKIPPED</em>, not FAILED, so
 * {@code ./gradlew test} on a Docker-less laptop produces
 * {@code BUILD SUCCESSFUL}.
 *
 * <p>Wired onto {@link AbstractIntegrationTest} via
 * {@code @ExtendWith(DockerAvailableCondition.class)} — every subclass
 * inherits the guard automatically. Subclasses no longer need their own
 * {@code @EnabledIf("isDockerAvailable")} + {@code isDockerAvailable()}
 * method.
 *
 * @see <a href="file:../../../../../../../../../platform/testing-strategy.md">platform/testing-strategy.md</a>
 *      "Docker Availability Guard"
 */
public final class DockerAvailableCondition implements ExecutionCondition {

    private static final ConditionEvaluationResult ENABLED =
            ConditionEvaluationResult.enabled("Docker is available");

    private static final ConditionEvaluationResult DISABLED =
            ConditionEvaluationResult.disabled(
                    "Docker is not available — skipping integration test");

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return DockerHostState.AVAILABLE ? ENABLED : DISABLED;
    }

    /**
     * Cache the Docker availability check once per JVM. {@code isDockerAvailable()}
     * attempts to connect to the Docker daemon, which can take a few seconds on
     * first call; we want exactly one probe per test forked JVM.
     */
    private static final class DockerHostState {
        static final boolean AVAILABLE = probe();

        private static boolean probe() {
            try {
                return DockerClientFactory.instance().isDockerAvailable();
            } catch (Throwable t) {
                return false;
            }
        }
    }
}
