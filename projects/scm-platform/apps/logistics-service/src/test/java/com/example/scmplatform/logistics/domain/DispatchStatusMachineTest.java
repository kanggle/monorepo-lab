package com.example.scmplatform.logistics.domain;

import com.example.scmplatform.logistics.domain.error.IllegalDispatchTransitionException;
import com.example.scmplatform.logistics.domain.model.Carrier;
import com.example.scmplatform.logistics.domain.model.CarrierCode;
import com.example.scmplatform.logistics.domain.model.Dispatch;
import com.example.scmplatform.logistics.domain.model.DispatchStatus;
import com.example.scmplatform.logistics.domain.model.ShipmentId;
import com.example.scmplatform.logistics.domain.model.TrackingNo;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link Dispatch} status machine (ADR-053 §D2, S1): PENDING → DISPATCHED /
 * PENDING → DISPATCH_FAILED → DISPATCHED, illegal transitions rejected, idempotent re-dispatch.
 */
class DispatchStatusMachineTest {

    private static final UUID ID = UUID.randomUUID();
    private static final ShipmentId SHIPMENT_ID = ShipmentId.of(UUID.randomUUID());

    private Dispatch pending() {
        return Dispatch.create(ID, SHIPMENT_ID, "SHP-001",
                UUID.randomUUID(), "ORD-001", "scm", Instant.now());
    }

    @Test
    void create_startsPending() {
        Dispatch d = pending();
        assertThat(d.getStatus()).isEqualTo(DispatchStatus.PENDING);
        assertThat(d.getVersion()).isEqualTo(0);
        assertThat(d.getTrackingNo()).isNull();
    }

    @Test
    void recordAck_fromPending_transitionsToDispatched() {
        Dispatch d = pending();
        d.recordAck(TrackingNo.of("TRACK-1"), CarrierCode.of("USPS"), Carrier.EASYPOST, Instant.now());
        assertThat(d.getStatus()).isEqualTo(DispatchStatus.DISPATCHED);
        assertThat(d.getTrackingNo().value()).isEqualTo("TRACK-1");
        assertThat(d.getCarrierCode().value()).isEqualTo("USPS");
        assertThat(d.getVendor()).isEqualTo(Carrier.EASYPOST);
        assertThat(d.getVersion()).isEqualTo(1);
    }

    @Test
    void recordFailure_fromPending_transitionsToFailed() {
        Dispatch d = pending();
        d.recordFailure("EasyPost 503", Instant.now());
        assertThat(d.getStatus()).isEqualTo(DispatchStatus.DISPATCH_FAILED);
        assertThat(d.getFailureReason()).isEqualTo("EasyPost 503");
        assertThat(d.getVersion()).isEqualTo(1);
    }

    @Test
    void recordAck_fromFailed_recoversToDispatched() {
        Dispatch d = pending();
        d.recordFailure("EasyPost 503", Instant.now());
        d.recordAck(TrackingNo.of("TRACK-2"), CarrierCode.of("FEDEX"), Carrier.EASYPOST, Instant.now());
        assertThat(d.getStatus()).isEqualTo(DispatchStatus.DISPATCHED);
        assertThat(d.getTrackingNo().value()).isEqualTo("TRACK-2");
        assertThat(d.getFailureReason()).isNull();
        assertThat(d.getVersion()).isEqualTo(2);
    }

    @Test
    void recordAck_onAlreadyDispatched_isIdempotentNoOp() {
        Dispatch d = pending();
        d.recordAck(TrackingNo.of("TRACK-1"), CarrierCode.of("USPS"), Carrier.EASYPOST, Instant.now());
        int versionAfterFirst = d.getVersion();

        // A second ack (redelivery / repeat retry) must not mutate or bump the version.
        d.recordAck(TrackingNo.of("TRACK-CHANGED"), CarrierCode.of("DHL"), Carrier.EASYPOST, Instant.now());

        assertThat(d.getStatus()).isEqualTo(DispatchStatus.DISPATCHED);
        assertThat(d.getTrackingNo().value()).isEqualTo("TRACK-1"); // unchanged
        assertThat(d.getCarrierCode().value()).isEqualTo("USPS");   // unchanged
        assertThat(d.getVersion()).isEqualTo(versionAfterFirst);    // no bump
    }

    @Test
    void recordFailure_onDispatched_isRejected() {
        Dispatch d = pending();
        d.recordAck(TrackingNo.of("TRACK-1"), CarrierCode.of("USPS"), Carrier.EASYPOST, Instant.now());
        assertThatThrownBy(() -> d.recordFailure("late failure", Instant.now()))
                .isInstanceOf(IllegalDispatchTransitionException.class);
    }

    @Test
    void statusHelpers() {
        assertThat(DispatchStatus.PENDING.canDispatch()).isTrue();
        assertThat(DispatchStatus.DISPATCH_FAILED.canDispatch()).isTrue();
        assertThat(DispatchStatus.DISPATCHED.canDispatch()).isFalse();
        assertThat(DispatchStatus.DISPATCHED.isDispatched()).isTrue();
    }
}
