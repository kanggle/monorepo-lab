package com.example.erp.approval.integration;

import com.example.erp.approval.application.event.ApprovalEventPublisher;
import com.example.erp.approval.infrastructure.outbox.ApprovalOutboxPublisher;
import com.example.erp.approval.infrastructure.persistence.jpa.ApprovalOutboxJpaEntity;
import com.example.erp.approval.infrastructure.persistence.jpa.ApprovalOutboxJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * TASK-ERP-BE-042 regression guard: the outbox relay is <strong>actually
 * registered with the scheduler and actually drains</strong>.
 *
 * <p><b>Why this test exists at all.</b> {@code ApprovalOutboxPublisher} carries
 * {@code @Scheduled(fixedDelay …)} and {@code @ConditionalOnProperty(matchIfMissing =
 * true)}, so every signal a reader can cheaply reach says "on by default". It was
 * not: Spring does not register {@code @Scheduled} methods unless something switches
 * scheduling on with {@code @EnableScheduling}, and erp had that annotation in
 * exactly one of its five services — notification-service, the one <em>without</em>
 * an outbox. The relay had therefore never published a single row, and nothing said
 * so: no exception, no warning, a healthy container, and a read-model service whose
 * projections were simply always empty.
 *
 * <p><b>Why this is not a grep.</b> The ticket forbids asserting the presence of the
 * annotation, because that is a proxy: the annotation can be present while a
 * conditional bean, a profile, or a missing property keeps the relay from ever
 * running — which is one layer below where this defect actually lived. So the two
 * assertions here are about the running context and the live table:
 *
 * <ol>
 *   <li>the scheduler holds a registered task naming
 *       {@code ApprovalOutboxPublisher#publishPending} — precise enough to name the
 *       broken thing in the failure message;</li>
 *   <li>a pending row written to {@code approval_outbox} becomes published without
 *       anybody calling the publisher — the property the demo actually needs, and the
 *       one that stays true no matter how the wiring is later refactored.</li>
 * </ol>
 *
 * <p>(2) subsumes (1); (1) is kept because a bare "the row never published" is a poor
 * report when the cause is a one-word annotation.
 *
 * <p><b>{@code outbox.polling.enabled=true} is load-bearing here.</b> The {@code test}
 * profile turns the relay off and its comment claims "integration tests explicitly
 * enable it via {@code @TestPropertySource}" — when this test was written, a sweep of
 * both services' test sources found <em>no</em> test that did. The relay was therefore
 * never exercised in CI at all, which is why a green pipeline coexisted with a relay
 * that had never run. This is the first test that turns it on.
 */
@TestPropertySource(properties = "outbox.polling.enabled=true")
class OutboxRelayIsScheduledIntegrationTest extends AbstractApprovalIntegrationTest {

    @Autowired
    private ObjectProvider<ScheduledAnnotationBeanPostProcessor> scheduling;

    @Autowired
    private ApprovalOutboxJpaRepository outboxRepository;

    @Test
    @DisplayName("the scheduler holds a task targeting ApprovalOutboxPublisher#publishPending")
    void the_relay_method_is_registered_with_the_scheduler() {
        ScheduledAnnotationBeanPostProcessor postProcessor = scheduling.getIfAvailable();
        assertThat(postProcessor)
                .as("no ScheduledAnnotationBeanPostProcessor in the context — scheduling is "
                        + "not enabled, so every @Scheduled method in this service is dead "
                        + "code (add @EnableScheduling to OutboxConfig)")
                .isNotNull();

        Set<ScheduledTask> tasks = postProcessor.getScheduledTasks();
        assertThat(tasks)
                .as("scheduling is enabled but no task is registered at all")
                .isNotEmpty();

        // Identify the task by the scheduler's own description rather than by casting
        // the runnable: Spring wraps the `ScheduledMethodRunnable` (error handling /
        // observability), so `getRunnable() instanceof ScheduledMethodRunnable` is
        // version-dependent — measured false here on the first run, which would have
        // reported "the relay is not registered" while the relay was demonstrably
        // running. `Task#toString` delegates to the runnable, and for an
        // annotation-scheduled method that is the qualified method name.
        assertThat(tasks.stream().map(t -> t.getTask().toString()).toList())
                .as("the outbox relay's publishPending is not among the registered "
                        + "scheduled tasks — the outbox will fill and never drain")
                .anySatisfy(description -> assertThat(description)
                        .contains(ApprovalOutboxPublisher.class.getSimpleName())
                        .contains("publishPending"));
    }

    @Test
    @DisplayName("a pending outbox row is published by the relay, with nobody calling it")
    void a_pending_row_drains_without_anyone_calling_the_publisher() {
        UUID id = UUID.randomUUID();
        String aggregateId = "guard-" + id;
        outboxRepository.saveAndFlush(ApprovalOutboxJpaEntity.create(
                id,
                "ApprovalRequest",
                aggregateId,
                // the real constant, not a literal — an unmapped event type is treated
                // as a non-retryable poison pill and would never publish, which would
                // look exactly like the defect this test guards against.
                ApprovalEventPublisher.EVENT_APPROVAL_SUBMITTED,
                "{\"requestId\":\"" + aggregateId + "\",\"guard\":\"TASK-ERP-BE-042\"}",
                aggregateId,
                Instant.now()));

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(
                        outboxRepository.findById(id).orElseThrow().getPublishedAt())
                        .as("the row is still unpublished — the relay is not running "
                                + "(this is what a missing @EnableScheduling looks like "
                                + "from the outside: nothing fails, nothing moves)")
                        .isNotNull());
    }
}
