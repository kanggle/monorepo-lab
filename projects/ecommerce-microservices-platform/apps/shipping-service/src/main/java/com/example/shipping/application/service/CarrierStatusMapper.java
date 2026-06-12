package com.example.shipping.application.service;

import com.example.shipping.domain.model.ShippingStatus;

import java.util.Map;
import java.util.Optional;

/**
 * Maps an aggregator-reported raw status string to the domain {@link ShippingStatus}
 * (TASK-BE-293; aggregator mapping table TASK-BE-362 / ADR-007 D2).
 *
 * <p>The real provider behind {@code shipping.carrier.mode=http} is a <b>logistics
 * aggregator</b> (굿스플로 / 스윗트래커 / 셀메이트 등), not a single carrier — the aggregator
 * normalises every underlying carrier's vocabulary into its own <b>unified status code
 * scheme</b>. This table maps that unified scheme onto the four-state shipping domain.
 * The mapping is tolerant of vocabulary / separators (Korean aggregator tokens, English
 * synonyms, {@code -}/space/blank separators) so a vendor that emits {@code 배송출발},
 * {@code out-for-delivery} or {@code IN_TRANSIT} all land on the same domain status.
 *
 * <p>An unknown or blank status maps to {@link Optional#empty()} — the refresh / webhook
 * treats it as "no usable signal" (a no-op). Unmapped (non-blank) statuses are made
 * observable by the caller via {@link CarrierStatusObserver} (a counter + WARN log) so a
 * new/changed aggregator code does not silently stall a shipment (ADR-007 F1 / BE-362 AC-2).
 * {@code PREPARING} is intentionally NOT a target — a shipment only reaches the aggregator
 * once it is SHIPPED with a tracking number.
 *
 * <p>This type is <b>pure</b> (no Spring / metrics) — observability is wired by the caller.
 */
public final class CarrierStatusMapper {

    /**
     * Aggregator unified status code → domain {@link ShippingStatus}. Keys are the
     * normalised form (upper-case, {@code -}/space → {@code _}). Korean aggregator tokens
     * are kept verbatim (normalisation only upper-cases ASCII, which leaves Hangul intact).
     */
    private static final Map<String, ShippingStatus> AGGREGATOR_STATUS_TABLE = Map.ofEntries(
            // → SHIPPED (집화 완료 / 발송 단계: the aggregator has taken custody from the seller)
            Map.entry("SHIPPED", ShippingStatus.SHIPPED),
            Map.entry("DISPATCHED", ShippingStatus.SHIPPED),
            Map.entry("PICKED_UP", ShippingStatus.SHIPPED),
            Map.entry("집화완료", ShippingStatus.SHIPPED),
            Map.entry("집화처리", ShippingStatus.SHIPPED),
            Map.entry("상품인수", ShippingStatus.SHIPPED),

            // → IN_TRANSIT (이동중 / 배송출발: moving between hubs or out for delivery)
            Map.entry("IN_TRANSIT", ShippingStatus.IN_TRANSIT),
            Map.entry("INTRANSIT", ShippingStatus.IN_TRANSIT),
            Map.entry("OUT_FOR_DELIVERY", ShippingStatus.IN_TRANSIT),
            Map.entry("이동중", ShippingStatus.IN_TRANSIT),
            Map.entry("간선상차", ShippingStatus.IN_TRANSIT),
            Map.entry("간선하차", ShippingStatus.IN_TRANSIT),
            Map.entry("배송출발", ShippingStatus.IN_TRANSIT),
            Map.entry("배송중", ShippingStatus.IN_TRANSIT),

            // → DELIVERED (배송완료: handed to the recipient)
            Map.entry("DELIVERED", ShippingStatus.DELIVERED),
            Map.entry("COMPLETED", ShippingStatus.DELIVERED),
            Map.entry("배송완료", ShippingStatus.DELIVERED),
            Map.entry("배달완료", ShippingStatus.DELIVERED),

            // ---- Delivery Tracker unified scheme (TASK-BE-364 / external-integrations.md § 1.4) ----
            // The concrete aggregator (ADR-007 D2) is Delivery Tracker; its `TrackEventStatusCode`
            // enum is the unified scheme. The normaliser upper-cases + maps `-`/space → `_`, so the
            // plain enum names land here directly. INFORMATION_RECEIVED / ATTEMPT_FAIL / EXCEPTION /
            // UNKNOWN / NOT_FOUND are intentionally NOT listed = unmapped → no-op (forward-only,
            // no regress; observable via CarrierStatusObserver).
            // (IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED already covered by the aggregator rows above.)
            Map.entry("AT_PICKUP", ShippingStatus.SHIPPED),
            Map.entry("AVAILABLE_FOR_PICKUP", ShippingStatus.IN_TRANSIT));

    private CarrierStatusMapper() {
    }

    public static Optional<ShippingStatus> toShippingStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalize(rawStatus);
        return Optional.ofNullable(AGGREGATOR_STATUS_TABLE.get(normalized));
    }

    private static String normalize(String rawStatus) {
        return rawStatus.trim().toUpperCase().replace('-', '_').replace(' ', '_');
    }
}
