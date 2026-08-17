package com.example.testsupport.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.testcontainers.DockerClientFactory;

/**
 * Tests for {@link DockerAvailableCondition} — TASK-MONO-546.
 *
 * <h2>What is NOT exercised here, and why</h2>
 *
 * <p>The condition has two outcomes and this test can only reach the one matching
 * the host it runs on. On CI, and on any developer machine with a Docker daemon,
 * the <em>disabled</em> branch is never constructed; on a Docker-less laptop the
 * <em>enabled</em> branch is never constructed. The assertions below therefore say
 * "the verdict agrees with the host and carries that branch's message" rather than
 * pretending both branches are covered, and
 * {@link #verdictMatchesTheHostAndSaysWhichBranchRan()} prints the branch that
 * actually ran.
 *
 * <p>Where that line ends up, measured rather than assumed: Gradle does not forward
 * test stdout to the console, so the marker appears in this module's test report
 * (and in a local run's XML) but <strong>not</strong> in the CI job log — grepping a
 * green CI run for it returns nothing. It is a record for whoever opens the report,
 * not a signal a CI query can read.
 *
 * <p>Reaching both branches deterministically would mean making the probe
 * injectable — a change to production behaviour, deliberately outside this task.
 *
 * <h2>Why this does not start containers</h2>
 *
 * <p>Nothing here touches {@link AbstractIntegrationTest}: that class starts MySQL
 * and Kafka in its static initializer, and this module's {@code test} task runs in
 * the Docker-free `build-and-test` lane. {@link AbstractIntegrationTestWiringTest}
 * covers that class without initialising it.
 */
class DockerAvailableConditionTest {

    private static final String ENABLED_REASON = "Docker is available";
    private static final String DISABLED_REASON = "Docker is not available — skipping integration test";

    /** The same oracle the condition consults, read once for the whole class. */
    private static final boolean HOST_HAS_DOCKER = probeHost();

    private static boolean probeHost() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @Test
    @DisplayName("is an ExecutionCondition — @ExtendWith only honours the interfaces a class implements")
    void isAnExecutionCondition() {
        assertThat(new DockerAvailableCondition()).isInstanceOf(ExecutionCondition.class);
    }

    @Test
    @DisplayName("never propagates a probe failure — a broken Docker socket must skip the test, not error it")
    void neverThrows() {
        // The class exists because this runs BEFORE JUnit loads the test class, so
        // anything thrown here surfaces as an engine-level error instead of a skip.
        // `probe()` catches Throwable for that reason; this pins it. Passing a null
        // ExtensionContext also pins that the verdict does not depend on the context —
        // it must not, or it could not be evaluated this early.
        assertThatCode(() -> new DockerAvailableCondition().evaluateExecutionCondition(null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("probes the daemon once per JVM, not once per test — evaluations return the identical object")
    void probesOnce() {
        DockerAvailableCondition condition = new DockerAvailableCondition();

        ConditionEvaluationResult first = condition.evaluateExecutionCondition(null);
        ConditionEvaluationResult second = condition.evaluateExecutionCondition(null);
        ConditionEvaluationResult fromAnotherInstance =
                new DockerAvailableCondition().evaluateExecutionCondition(null);

        // Identity, not equality. The two outcomes are static finals behind a static
        // holder, so sameness is what shows the daemon is contacted once. Note that
        // ConditionEvaluationResult does not override equals(), so isEqualTo() would
        // assert identity by accident and quietly stop meaning it if that changed.
        assertThat(first).isSameAs(second).isSameAs(fromAnotherInstance);
    }

    @Test
    @DisplayName("the verdict agrees with the host and carries that branch's message")
    void verdictMatchesTheHostAndSaysWhichBranchRan() {
        // Printed, not asserted: it stops "the test passed" from being read as "both
        // outcomes were covered". Only one of them can be, on any given host.
        // Lands in this module's test report, not in the CI job log — see the class javadoc.
        System.out.println("DOCKER-CONDITION-BRANCH exercised="
                + (HOST_HAS_DOCKER ? "enabled" : "disabled")
                + " unexercised=" + (HOST_HAS_DOCKER ? "disabled" : "enabled"));

        ConditionEvaluationResult result =
                new DockerAvailableCondition().evaluateExecutionCondition(null);

        assertThat(result.isDisabled()).isNotEqualTo(HOST_HAS_DOCKER);
        assertThat(result.getReason())
                .hasValue(HOST_HAS_DOCKER ? ENABLED_REASON : DISABLED_REASON);
    }
}
