package com.example.scmplatform.logistics.application.usecase;

import com.example.scmplatform.logistics.domain.model.Dispatch;

/**
 * Outcome of {@link ConsumeShippingConfirmedUseCase#consume}. Lets the caller (and tests) observe
 * which of the two idempotency layers short-circuited vs a fresh dispatch, without inspecting the DB.
 *
 * <p>Note {@link Outcome#DISPATCHED_OR_FAILED}: a fresh consume that reached the vendor is reported
 * the same whether the vendor succeeded ({@code DISPATCHED}) or failed ({@code DISPATCH_FAILED}) —
 * because <b>a vendor failure is not a consume failure</b> (S5). The {@link #dispatch} carries the
 * resulting status.
 *
 * @param outcome  which path the consume took
 * @param dispatch the dispatch row (present for {@code DISPATCHED_OR_FAILED} and
 *                 {@code SHIPMENT_ALREADY_DISPATCHED}; {@code null} for {@code DUPLICATE_EVENT})
 */
public record ConsumeShippingConfirmedResult(Outcome outcome, Dispatch dispatch) {

    public enum Outcome {
        /** Fresh consume drove the vendor; the dispatch is DISPATCHED or DISPATCH_FAILED. */
        DISPATCHED_OR_FAILED,
        /** Layer 1 (eventId T8): the event was already processed — skipped, no mutation. */
        DUPLICATE_EVENT,
        /** Layer 2 (shipment_id): a redelivery under a new eventId found the existing dispatch — no double-dispatch. */
        SHIPMENT_ALREADY_DISPATCHED
    }

    public static ConsumeShippingConfirmedResult dispatched(Dispatch dispatch) {
        return new ConsumeShippingConfirmedResult(Outcome.DISPATCHED_OR_FAILED, dispatch);
    }

    public static ConsumeShippingConfirmedResult duplicateEvent() {
        return new ConsumeShippingConfirmedResult(Outcome.DUPLICATE_EVENT, null);
    }

    public static ConsumeShippingConfirmedResult alreadyDispatched(Dispatch dispatch) {
        return new ConsumeShippingConfirmedResult(Outcome.SHIPMENT_ALREADY_DISPATCHED, dispatch);
    }
}
