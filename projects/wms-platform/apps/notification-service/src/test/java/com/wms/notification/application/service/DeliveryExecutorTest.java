package com.wms.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.notification.application.port.outbound.ChannelPort;
import com.wms.notification.application.port.outbound.SlackChannelPort;
import com.wms.notification.application.service.fakes.InMemoryDeliveryRepository;
import com.wms.notification.application.service.fakes.RecordingOutboxPort;
import com.wms.notification.domain.delivery.DeliveryStatus;
import com.wms.notification.domain.delivery.NotificationDelivery;
import com.wms.notification.domain.error.ChannelNotConfiguredException;
import com.wms.notification.domain.error.ChannelPermanentFailureException;
import com.wms.notification.domain.routing.ChannelType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * DeliveryExecutor unit tests covering retry arithmetic + terminal status
 * decisions for the three vendor outcomes (success, transient, permanent).
 */
class DeliveryExecutorTest {

    private static final Instant NOW = Instant.parse("2025-01-01T00:00:00Z");

    @Test
    void successTransitionsToSucceededAndAppendsCompletedOutbox() {
        InMemoryDeliveryRepository deliveries = new InMemoryDeliveryRepository();
        RecordingOutboxPort outbox = new RecordingOutboxPort();
        StubChannelPort port = new StubChannelPort(StubChannelPort.Mode.SUCCESS);
        DeliveryExecutor exec = newExecutor(deliveries, outbox, port);

        NotificationDelivery d = newPending();
        deliveries.save(d);

        exec.execute(deliveries.findById(d.id()).orElseThrow());

        NotificationDelivery after = deliveries.findById(d.id()).orElseThrow();
        assertThat(after.status()).isEqualTo(DeliveryStatus.SUCCEEDED);
        assertThat(after.attemptCount()).isEqualTo(1);
        assertThat(outbox.rows).anyMatch(r -> r.eventType().equals("notification.delivered")
                && "SUCCEEDED".equals(r.outcomeCode()));
    }

    @Test
    void transient5xxStaysPendingWithBackoff() {
        InMemoryDeliveryRepository deliveries = new InMemoryDeliveryRepository();
        RecordingOutboxPort outbox = new RecordingOutboxPort();
        StubChannelPort port = new StubChannelPort(StubChannelPort.Mode.TRANSIENT);
        DeliveryExecutor exec = newExecutor(deliveries, outbox, port);

        NotificationDelivery d = newPending();
        deliveries.save(d);

        exec.execute(deliveries.findById(d.id()).orElseThrow());

        NotificationDelivery after = deliveries.findById(d.id()).orElseThrow();
        assertThat(after.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(after.attemptCount()).isEqualTo(1);
        assertThat(after.scheduledRetryAt()).isPresent();
        assertThat(after.lastError()).isPresent();
        // No "delivered" outbox until terminal state.
        assertThat(outbox.rows.stream().anyMatch(r -> r.eventType().equals("notification.delivered")))
                .isFalse();
    }

    @Test
    void permanentFailureWritesFailedTerminal() {
        InMemoryDeliveryRepository deliveries = new InMemoryDeliveryRepository();
        RecordingOutboxPort outbox = new RecordingOutboxPort();
        StubChannelPort port = new StubChannelPort(StubChannelPort.Mode.PERMANENT_4XX);
        DeliveryExecutor exec = newExecutor(deliveries, outbox, port);

        NotificationDelivery d = newPending();
        deliveries.save(d);

        exec.execute(deliveries.findById(d.id()).orElseThrow());

        NotificationDelivery after = deliveries.findById(d.id()).orElseThrow();
        assertThat(after.status()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(outbox.rows).anyMatch(r -> "FAILED_PERMANENT".equals(r.outcomeCode()));
    }

    @Test
    void channelNotConfiguredFailsClosed() {
        InMemoryDeliveryRepository deliveries = new InMemoryDeliveryRepository();
        RecordingOutboxPort outbox = new RecordingOutboxPort();
        StubChannelPort port = new StubChannelPort(StubChannelPort.Mode.NOT_CONFIGURED);
        DeliveryExecutor exec = newExecutor(deliveries, outbox, port);

        NotificationDelivery d = newPending();
        deliveries.save(d);

        exec.execute(deliveries.findById(d.id()).orElseThrow());

        NotificationDelivery after = deliveries.findById(d.id()).orElseThrow();
        assertThat(after.status()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(outbox.rows)
                .anyMatch(r -> "FAILED_CHANNEL_NOT_CONFIGURED".equals(r.outcomeCode()));
    }

    @Test
    void retryBudgetArithmeticFollowsConfiguredSchedule() {
        InMemoryDeliveryRepository deliveries = new InMemoryDeliveryRepository();
        RecordingOutboxPort outbox = new RecordingOutboxPort();
        DeliveryExecutor exec = newExecutor(deliveries, outbox, new StubChannelPort(StubChannelPort.Mode.SUCCESS));
        // backoffSeconds defaults: [1, 5, 30, 120, 600]
        assertThat(exec.backoffBaseSeconds(0)).isEqualTo(1L);
        assertThat(exec.backoffBaseSeconds(1)).isEqualTo(5L);
        assertThat(exec.backoffBaseSeconds(4)).isEqualTo(600L);
        // Beyond the schedule → cap at the last entry.
        assertThat(exec.backoffBaseSeconds(99)).isEqualTo(600L);
    }

    @Test
    void retryArithmeticAppliesJitterWithinTwentyPercent() {
        InMemoryDeliveryRepository deliveries = new InMemoryDeliveryRepository();
        RecordingOutboxPort outbox = new RecordingOutboxPort();
        DeliveryExecutor exec = newExecutor(deliveries, outbox, new StubChannelPort(StubChannelPort.Mode.SUCCESS));
        for (int i = 0; i < 100; i++) {
            long ms = exec.nextBackoff(0).toMillis();
            // base = 1s, jitter = ±20% → range [0.8s .. 1.2s]
            assertThat(ms).isBetween(800L, 1200L);
        }
    }

    @Test
    void terminalDeliveryRetryNoOps() {
        InMemoryDeliveryRepository deliveries = new InMemoryDeliveryRepository();
        RecordingOutboxPort outbox = new RecordingOutboxPort();
        StubChannelPort port = new StubChannelPort(StubChannelPort.Mode.SUCCESS);
        DeliveryExecutor exec = newExecutor(deliveries, outbox, port);

        NotificationDelivery d = newPending();
        d.markFailedPermanent("done", NOW);
        deliveries.save(d);

        exec.retry(d.id());

        // Adapter must NOT be called.
        assertThat(port.invocations).isZero();
    }

    private static DeliveryExecutor newExecutor(InMemoryDeliveryRepository deliveries,
                                                RecordingOutboxPort outbox,
                                                ChannelPort port) {
        return new DeliveryExecutor(
                deliveries, outbox, List.of(port),
                List.of(1, 5, 30, 120, 600),
                Clock.fixed(NOW, ZoneId.of("UTC")),
                new SimpleMeterRegistry());
    }

    private static NotificationDelivery newPending() {
        return NotificationDelivery.createPending(
                UUID.randomUUID(), UUID.randomUUID(),
                "wms.x.v1", "wms-alerts", "wms-alerts",
                UUID.randomUUID().toString(), "{}", NOW);
    }

    private static class StubChannelPort implements SlackChannelPort {
        enum Mode { SUCCESS, TRANSIENT, PERMANENT_4XX, NOT_CONFIGURED }

        final Mode mode;
        int invocations;

        StubChannelPort(Mode mode) {
            this.mode = mode;
        }

        @Override
        public ChannelType channelType() {
            return ChannelType.SLACK;
        }

        @Override
        public void send(String recipient, String messageJson) {
            invocations++;
            switch (mode) {
                case SUCCESS -> { /* ok */ }
                case TRANSIENT -> throw new RuntimeException("503 vendor outage");
                case PERMANENT_4XX -> throw new ChannelPermanentFailureException(404, "channel not found");
                case NOT_CONFIGURED -> throw new ChannelNotConfiguredException(
                        "Slack webhook URL not configured for channel alias: " + recipient);
            }
        }
    }
}
