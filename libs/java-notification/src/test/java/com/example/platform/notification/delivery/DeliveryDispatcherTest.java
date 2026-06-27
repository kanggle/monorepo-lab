package com.example.platform.notification.delivery;

import com.example.platform.notification.channel.ChannelDeliveryRequest;
import com.example.platform.notification.channel.ChannelResult;
import com.example.platform.notification.channel.NotificationChannelAdapter;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-06-28T00:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final BackoffCalculator backoff =
            new BackoffCalculator(List.of(1, 5, 30, 120, 600), 0.2, 5, BackoffCalculator.NO_JITTER);

    /** Records every save so the test can assert persistence happened. */
    private static final class RecordingStore implements DeliveryStore {
        final List<DeliveryRecord> saved = new ArrayList<>();
        @Override public List<DeliveryRecord> findDuePending(Instant now, int limit) { return List.of(); }
        @Override public void save(DeliveryRecord record) { saved.add(record); }
    }

    private static final class FakeChannel implements NotificationChannelAdapter {
        private final String channel;
        private final Function<ChannelDeliveryRequest, ChannelResult> response;
        ChannelDeliveryRequest lastRequest;
        FakeChannel(String channel, Function<ChannelDeliveryRequest, ChannelResult> response) {
            this.channel = channel;
            this.response = response;
        }
        @Override public String channel() { return channel; }
        @Override public ChannelResult deliver(ChannelDeliveryRequest request) {
            this.lastRequest = request;
            return response.apply(request);
        }
    }

    private DeliveryRecord pending(String channel) {
        return DeliveryRecord.createPending("d-1", channel, "ops", "title", "body", "{\"k\":1}");
    }

    @Test
    void success_marksSucceededAndPersists_andPassesRequestThrough() {
        RecordingStore store = new RecordingStore();
        FakeChannel slack = new FakeChannel("slack", r -> ChannelResult.delivered("ts-123"));
        DeliveryDispatcher dispatcher =
                new DeliveryDispatcher(store, List.of(slack), backoff, clock);
        DeliveryRecord rec = pending("slack");

        DeliveryOutcome outcome = dispatcher.dispatch(rec);

        assertThat(outcome).isEqualTo(DeliveryOutcome.SUCCEEDED);
        assertThat(rec.status()).isEqualTo(DeliveryStatus.SUCCEEDED);
        assertThat(store.saved).containsExactly(rec);
        assertThat(slack.lastRequest.recipient()).isEqualTo("ops");
        assertThat(slack.lastRequest.title()).isEqualTo("title");
        assertThat(slack.lastRequest.payloadJson()).isEqualTo("{\"k\":1}");
    }

    @Test
    void permanentFailure_marksFailedWithoutRetry() {
        RecordingStore store = new RecordingStore();
        FakeChannel slack = new FakeChannel("slack", r -> ChannelResult.permanentFailure("404"));
        DeliveryDispatcher dispatcher =
                new DeliveryDispatcher(store, List.of(slack), backoff, clock);
        DeliveryRecord rec = pending("slack");

        DeliveryOutcome outcome = dispatcher.dispatch(rec);

        assertThat(outcome).isEqualTo(DeliveryOutcome.FAILED_PERMANENT);
        assertThat(rec.status()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(rec.attemptCount()).isEqualTo(1);
        assertThat(rec.scheduledRetryAt()).isEmpty();
    }

    @Test
    void transientFailure_schedulesRetryUsingBackoff() {
        RecordingStore store = new RecordingStore();
        FakeChannel slack = new FakeChannel("slack", r -> ChannelResult.transientFailure("503"));
        DeliveryDispatcher dispatcher =
                new DeliveryDispatcher(store, List.of(slack), backoff, clock);
        DeliveryRecord rec = pending("slack");

        DeliveryOutcome outcome = dispatcher.dispatch(rec);

        assertThat(outcome).isEqualTo(DeliveryOutcome.RETRY_SCHEDULED);
        assertThat(rec.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(rec.attemptCount()).isEqualTo(1);
        // attemptCount before transition was 0 -> base 1s, no jitter.
        assertThat(rec.scheduledRetryAt()).contains(NOW.plusSeconds(1));
    }

    @Test
    void transientFailure_atLastAttempt_becomesRetryExhausted() {
        RecordingStore store = new RecordingStore();
        FakeChannel slack = new FakeChannel("slack", r -> ChannelResult.transientFailure("503"));
        DeliveryDispatcher dispatcher =
                new DeliveryDispatcher(store, List.of(slack), backoff, clock);
        // maxAttempts 1 -> the single attempt exhausts immediately.
        DeliveryRecord rec = DeliveryRecord.createPending("d-x", "slack", "ops", "t", "b", "{}", 1);

        DeliveryOutcome outcome = dispatcher.dispatch(rec);

        assertThat(outcome).isEqualTo(DeliveryOutcome.FAILED_RETRY_EXHAUSTED);
        assertThat(rec.status()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(store.saved).containsExactly(rec);
    }

    @Test
    void unconfiguredChannel_isTreatedAsPermanentFailure() {
        RecordingStore store = new RecordingStore();
        FakeChannel slack = new FakeChannel("slack", r -> ChannelResult.delivered("x"));
        DeliveryDispatcher dispatcher =
                new DeliveryDispatcher(store, List.of(slack), backoff, clock);
        DeliveryRecord rec = pending("email"); // no adapter for "email"

        DeliveryOutcome outcome = dispatcher.dispatch(rec);

        assertThat(outcome).isEqualTo(DeliveryOutcome.FAILED_PERMANENT);
        assertThat(rec.status()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(rec.lastError().orElseThrow()).contains("CHANNEL_NOT_CONFIGURED");
    }

    @Test
    void adapterThatThrows_isTreatedAsTransient() {
        RecordingStore store = new RecordingStore();
        FakeChannel boom = new FakeChannel("slack", r -> { throw new RuntimeException("kaboom"); });
        DeliveryDispatcher dispatcher =
                new DeliveryDispatcher(store, List.of(boom), backoff, clock);
        DeliveryRecord rec = pending("slack");

        DeliveryOutcome outcome = dispatcher.dispatch(rec);

        assertThat(outcome).isEqualTo(DeliveryOutcome.RETRY_SCHEDULED);
        assertThat(rec.lastError().orElseThrow()).contains("kaboom");
    }

    @Test
    void terminalRecord_isNoOp() {
        RecordingStore store = new RecordingStore();
        FakeChannel slack = new FakeChannel("slack", r -> ChannelResult.delivered("x"));
        DeliveryDispatcher dispatcher =
                new DeliveryDispatcher(store, List.of(slack), backoff, clock);
        DeliveryRecord rec = pending("slack");
        rec.markSucceeded();

        DeliveryOutcome outcome = dispatcher.dispatch(rec);

        assertThat(outcome).isEqualTo(DeliveryOutcome.SUCCEEDED);
        assertThat(store.saved).isEmpty(); // no re-save
    }

    @Test
    void outcomeListener_isInvokedAfterSave() {
        RecordingStore store = new RecordingStore();
        FakeChannel slack = new FakeChannel("slack", r -> ChannelResult.delivered("x"));
        List<DeliveryOutcome> seen = new ArrayList<>();
        DeliveryOutcomeListener listener = (record, outcome) -> {
            assertThat(store.saved).contains(record); // save already happened
            seen.add(outcome);
        };
        DeliveryDispatcher dispatcher =
                new DeliveryDispatcher(store, List.of(slack), backoff, clock, listener);

        dispatcher.dispatch(pending("slack"));

        assertThat(seen).containsExactly(DeliveryOutcome.SUCCEEDED);
    }
}
