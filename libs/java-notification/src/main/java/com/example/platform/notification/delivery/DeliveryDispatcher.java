package com.example.platform.notification.delivery;

import com.example.platform.notification.channel.ChannelDeliveryRequest;
import com.example.platform.notification.channel.ChannelResult;
import com.example.platform.notification.channel.NotificationChannelAdapter;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The core Category-C delivery engine, lifted + generalised from the wms reference
 * ({@code DeliveryDispatchPerRow}). It owns the dispatch <i>logic</i> for a single
 * record: resolve the channel adapter by {@link DeliveryRecord#channel()}, call
 * {@link NotificationChannelAdapter#deliver}, apply the state transition (using
 * {@link BackoffCalculator} for retries), persist via {@link DeliveryStore}, and
 * return a {@link DeliveryOutcome}.
 *
 * <h2>What stays service-side (ADR-MONO-038 posture)</h2>
 * <ul>
 *   <li><b>The per-row {@code REQUIRES_NEW} transactional boundary</b> — the service wraps
 *       {@link #dispatch(DeliveryRecord)} in its own {@code @Transactional(REQUIRES_NEW)} bean
 *       (the wms bean-extraction pattern that breaks Spring AOP self-invocation), so one row's
 *       failure does not roll back already-succeeded siblings. The lib provides only the callable.</li>
 *   <li><b>The {@code @Scheduled} retry trigger + the {@code FOR UPDATE SKIP LOCKED} query</b> — the
 *       service's scheduler scans {@link DeliveryStore#findDuePending(Instant, int)} and calls
 *       {@code dispatch} per row. The lib bakes in neither a scheduler nor a DB dialect.</li>
 *   <li><b>The outbox re-emission</b> — the service reacts to the returned {@link DeliveryOutcome}
 *       (or a registered {@link DeliveryOutcomeListener}) and writes its own event. The lib carries
 *       no domain event types (HARDSTOP-03).</li>
 * </ul>
 *
 * <h2>Channel-result mapping (preserves the wms permanent-vs-retryable invariant)</h2>
 * <ul>
 *   <li>{@link ChannelResult#delivered()} → {@link DeliveryRecord#markSucceeded()} → {@link DeliveryOutcome#SUCCEEDED}.</li>
 *   <li>permanent failure → {@link DeliveryRecord#markFailedPermanent} → {@link DeliveryOutcome#FAILED_PERMANENT}.</li>
 *   <li>transient failure → {@link DeliveryRecord#markRetryable} → {@link DeliveryOutcome#RETRY_SCHEDULED}
 *       (or {@link DeliveryOutcome#FAILED_RETRY_EXHAUSTED} when the budget runs out).</li>
 * </ul>
 *
 * <p>This class is pure logic (no Spring annotations) so it is trivially unit-testable
 * with fakes and so the service controls its own bean wiring + transaction boundary.
 */
public final class DeliveryDispatcher {

    private final DeliveryStore store;
    private final Map<String, NotificationChannelAdapter> adaptersByChannel;
    private final BackoffCalculator backoff;
    private final Clock clock;
    private final DeliveryOutcomeListener outcomeListener;

    public DeliveryDispatcher(DeliveryStore store,
                              List<NotificationChannelAdapter> adapters,
                              BackoffCalculator backoff,
                              Clock clock) {
        this(store, adapters, backoff, clock, DeliveryOutcomeListener.NOOP);
    }

    public DeliveryDispatcher(DeliveryStore store,
                              List<NotificationChannelAdapter> adapters,
                              BackoffCalculator backoff,
                              Clock clock,
                              DeliveryOutcomeListener outcomeListener) {
        this.store = Objects.requireNonNull(store, "store");
        this.backoff = Objects.requireNonNull(backoff, "backoff");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.outcomeListener = Objects.requireNonNull(outcomeListener, "outcomeListener");
        this.adaptersByChannel = new HashMap<>();
        for (NotificationChannelAdapter adapter : Objects.requireNonNull(adapters, "adapters")) {
            this.adaptersByChannel.put(adapter.channel(), adapter);
        }
    }

    /**
     * Dispatch one delivery. <b>The caller is responsible for the
     * {@code @Transactional(REQUIRES_NEW)} boundary</b> (invoke this from a distinct
     * Spring bean so the proxy is honoured — the wms reference's per-row pattern).
     *
     * <p>A record already in a terminal state is a no-op (returns its terminal
     * outcome). An unconfigured channel is treated as a permanent failure (the wms
     * {@code ChannelNotConfiguredException} → FAILED behaviour).
     *
     * @return the classified outcome (also delivered to any registered listener)
     */
    public DeliveryOutcome dispatch(DeliveryRecord record) {
        Objects.requireNonNull(record, "record");
        if (record.isTerminal()) {
            return terminalOutcome(record);
        }

        NotificationChannelAdapter adapter = adaptersByChannel.get(record.channel());
        if (adapter == null) {
            return applyPermanent(record, "CHANNEL_NOT_CONFIGURED: no adapter for channel " + record.channel());
        }

        ChannelResult result = safeDeliver(adapter, record);

        if (result.delivered()) {
            record.markSucceeded();
            store.save(record);
            return emit(record, DeliveryOutcome.SUCCEEDED);
        }
        if (result.permanent()) {
            return applyPermanent(record, result.error());
        }
        return applyRetryable(record, result.error());
    }

    private ChannelResult safeDeliver(NotificationChannelAdapter adapter, DeliveryRecord record) {
        ChannelDeliveryRequest request = new ChannelDeliveryRequest(
                record.recipient(), record.title(), record.body(), record.payloadJson(), Map.of());
        try {
            ChannelResult result = adapter.deliver(request);
            // SPI contract is never-throw + never-null; be defensive if an adapter violates it.
            return result != null
                    ? result
                    : ChannelResult.transientFailure("channel adapter returned null result");
        } catch (RuntimeException unexpected) {
            // An adapter that throws despite the contract — treat as transient.
            return ChannelResult.transientFailure(
                    "channel adapter threw: " + unexpected.getClass().getSimpleName()
                            + ": " + unexpected.getMessage());
        }
    }

    private DeliveryOutcome applyPermanent(DeliveryRecord record, String error) {
        record.markFailedPermanent(error);
        store.save(record);
        return emit(record, DeliveryOutcome.FAILED_PERMANENT);
    }

    private DeliveryOutcome applyRetryable(DeliveryRecord record, String error) {
        Instant now = clock.instant();
        Instant nextRetryAt = backoff.nextRetryAt(record.attemptCount(), now);
        try {
            record.markRetryable(error, nextRetryAt);
            store.save(record);
            return emit(record, DeliveryOutcome.RETRY_SCHEDULED);
        } catch (DeliveryRetryExhaustedException exhausted) {
            // markRetryable already transitioned to terminal FAILED before throwing.
            store.save(record);
            return emit(record, DeliveryOutcome.FAILED_RETRY_EXHAUSTED);
        }
    }

    private DeliveryOutcome emit(DeliveryRecord record, DeliveryOutcome outcome) {
        outcomeListener.onOutcome(record, outcome);
        return outcome;
    }

    private static DeliveryOutcome terminalOutcome(DeliveryRecord record) {
        return record.status() == DeliveryStatus.SUCCEEDED
                ? DeliveryOutcome.SUCCEEDED
                : DeliveryOutcome.FAILED_PERMANENT;
    }

    /** The channel adapter resolved for {@code channel}, if any (test/diagnostic seam). */
    public Optional<NotificationChannelAdapter> adapterFor(String channel) {
        return Optional.ofNullable(adaptersByChannel.get(channel));
    }
}
