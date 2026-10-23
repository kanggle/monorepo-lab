package com.example.shipping.application.service;

import com.example.shipping.domain.model.Shipping;
import com.example.shipping.domain.model.ShippingStatus;
import com.example.shipping.domain.model.StatusHistoryEntry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ShippingForwardAdvancerTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-12T00:00:00Z"), ZoneOffset.UTC);

    private Shipping shipped() {
        Shipping s = Shipping.create("tenant-a", "order-1", "user-1", clock);
        s.transitionTo(ShippingStatus.SHIPPED, "TRK-1", "CJ", clock);
        return s;
    }

    @Test
    void advancesMultipleStepsForward_recordsIntermediateHistory_returnsOriginal() {
        Shipping s = shipped(); // SHIPPED

        Optional<ShippingStatus> from = ShippingForwardAdvancer.advanceForward(s, ShippingStatus.DELIVERED, clock);

        assertThat(from).contains(ShippingStatus.SHIPPED);
        assertThat(s.getStatus()).isEqualTo(ShippingStatus.DELIVERED);
        assertThat(s.getStatusHistory()).extracting(StatusHistoryEntry::status)
                .containsSubsequence(ShippingStatus.SHIPPED, ShippingStatus.IN_TRANSIT, ShippingStatus.DELIVERED);
    }

    @Test
    void goalEqualToCurrent_isNoOp_returnsEmpty() {
        Shipping s = shipped(); // SHIPPED

        Optional<ShippingStatus> from = ShippingForwardAdvancer.advanceForward(s, ShippingStatus.SHIPPED, clock);

        assertThat(from).isEmpty();
        assertThat(s.getStatus()).isEqualTo(ShippingStatus.SHIPPED);
    }

    @Test
    void goalBehindCurrent_isNoOp_neverRegresses() {
        Shipping s = shipped();
        s.transitionTo(ShippingStatus.IN_TRANSIT, "TRK-1", "CJ", clock); // now IN_TRANSIT

        Optional<ShippingStatus> from = ShippingForwardAdvancer.advanceForward(s, ShippingStatus.SHIPPED, clock);

        assertThat(from).isEmpty();
        assertThat(s.getStatus()).isEqualTo(ShippingStatus.IN_TRANSIT);
    }

    @Test
    void cancelledShipping_isNeverAdvancedByACarrierGoal() {
        // TASK-BE-649: a carrier refresh / webhook / auto-collect for a cancelled shipment
        // must not resurrect it, whatever status the carrier reports.
        Shipping s = Shipping.create("tenant-a", "order-1", "user-1", clock);
        s.cancel(clock);

        for (ShippingStatus goal : new ShippingStatus[] {
                ShippingStatus.SHIPPED, ShippingStatus.IN_TRANSIT, ShippingStatus.DELIVERED}) {
            Optional<ShippingStatus> from = ShippingForwardAdvancer.advanceForward(s, goal, clock);
            assertThat(from).as("goal %s", goal).isEmpty();
            assertThat(s.getStatus()).isEqualTo(ShippingStatus.CANCELLED);
        }
        assertThat(s.getStatusHistory()).hasSize(2);
    }
}
