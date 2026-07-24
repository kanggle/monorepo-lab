package com.example.scmplatform.logistics.application.routing;

import com.example.scmplatform.logistics.domain.model.Dispatch;
import com.example.scmplatform.logistics.domain.model.ShipmentId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FulfillmentRouter} — the Phase-1 multimodal seam (ADR-053 §D4). Asserts the ONLY wired
 * branch (self-fulfillment → carrier dispatch) resolves, and that the 3PL branch is a documented
 * <b>extension point</b> present in the enum but never returned by {@code route} in Phase 1
 * (task Failure Scenario A — no active 3PL routing).
 */
class FulfillmentRouterTest {

    private final FulfillmentRouter router = new FulfillmentRouter();

    private static Dispatch anyDispatch(String requestedCarrierCode) {
        return Dispatch.create(UUID.randomUUID(), ShipmentId.of(UUID.randomUUID()),
                "SHP-1", UUID.randomUUID(), "ORD-1", "scm", requestedCarrierCode, Instant.now());
    }

    @Test
    void route_domesticCarrierCode_resolvesSelfBranch() {
        assertThat(router.route(anyDispatch("CJ-LOGISTICS")))
                .isEqualTo(FulfillmentRouter.FulfillmentMode.SELF);
    }

    @Test
    void route_internationalCarrierCode_resolvesSelfBranch() {
        assertThat(router.route(anyDispatch("UPS")))
                .isEqualTo(FulfillmentRouter.FulfillmentMode.SELF);
    }

    @Test
    void route_nullCarrierCode_resolvesSelfBranch() {
        // A null carrierCode is still a self-fulfilled (wms-shipped) parcel — SELF; the null degrade
        // to the default vendor is the CarrierRouter's concern, not the FulfillmentRouter's.
        assertThat(router.route(anyDispatch(null)))
                .isEqualTo(FulfillmentRouter.FulfillmentMode.SELF);
    }

    @Test
    void thirdPartyLogistics_isAnInertExtensionPoint_neverResolvedInPhase1() {
        // The enum value EXISTS (the Phase-2 seam attaches here) but route() must never return it
        // in Phase 1 — every input resolves SELF. Sweep a spread of routing signals.
        for (String code : new String[]{null, "CJ-LOGISTICS", "HANJIN", "UPS", "FEDEX", "UNKNOWN-XYZ"}) {
            assertThat(router.route(anyDispatch(code)))
                    .as("carrierCode=%s must resolve SELF (no active 3PL routing in Phase 1)", code)
                    .isNotEqualTo(FulfillmentRouter.FulfillmentMode.THIRD_PARTY_LOGISTICS);
        }
    }
}
