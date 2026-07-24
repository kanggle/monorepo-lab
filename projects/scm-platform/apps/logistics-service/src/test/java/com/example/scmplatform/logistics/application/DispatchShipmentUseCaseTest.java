package com.example.scmplatform.logistics.application;

import com.example.scmplatform.logistics.application.port.outbound.DispatchAck;
import com.example.scmplatform.logistics.application.port.outbound.DispatchPersistencePort;
import com.example.scmplatform.logistics.application.port.outbound.ShipmentDispatchPort;
import com.example.scmplatform.logistics.application.usecase.DispatchShipmentUseCase;
import com.example.scmplatform.logistics.domain.error.ShipmentDispatchException;
import com.example.scmplatform.logistics.domain.model.Carrier;
import com.example.scmplatform.logistics.domain.model.CarrierCode;
import com.example.scmplatform.logistics.domain.model.Dispatch;
import com.example.scmplatform.logistics.domain.model.DispatchStatus;
import com.example.scmplatform.logistics.domain.model.ShipmentId;
import com.example.scmplatform.logistics.domain.model.TrackingNo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DispatchShipmentUseCaseTest {

    @Mock ShipmentDispatchPort dispatchPort;
    @Mock DispatchPersistencePort persistencePort;
    @InjectMocks DispatchShipmentUseCase useCase;

    private Dispatch pending() {
        return Dispatch.create(UUID.randomUUID(), ShipmentId.of(UUID.randomUUID()),
                "SHP-001", UUID.randomUUID(), "ORD-001", "scm", Instant.now());
    }

    @Test
    void dispatch_vendorSuccess_recordsDispatched() {
        Dispatch d = pending();
        when(dispatchPort.dispatch(d)).thenReturn(new DispatchAck("TRACK-1", "USPS", Carrier.EASYPOST));
        when(persistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Dispatch result = useCase.dispatch(d);

        assertThat(result.getStatus()).isEqualTo(DispatchStatus.DISPATCHED);
        assertThat(result.getTrackingNo().value()).isEqualTo("TRACK-1");
        assertThat(result.getVendor()).isEqualTo(Carrier.EASYPOST);
        verify(dispatchPort).dispatch(d);
        verify(persistencePort).save(d);
    }

    @Test
    void dispatch_vendorFailure_recordsFailed() {
        Dispatch d = pending();
        when(dispatchPort.dispatch(d))
                .thenThrow(new ShipmentDispatchException("EasyPost 503", true, null));
        when(persistencePort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Dispatch result = useCase.dispatch(d);

        assertThat(result.getStatus()).isEqualTo(DispatchStatus.DISPATCH_FAILED);
        assertThat(result.getFailureReason()).isEqualTo("EasyPost 503");
        verify(persistencePort).save(d);
    }

    @Test
    void dispatch_alreadyDispatched_isNoOp_noVendorCall() {
        Dispatch dispatched = Dispatch.reconstitute(
                UUID.randomUUID(), ShipmentId.of(UUID.randomUUID()), "SHP-001",
                UUID.randomUUID(), "ORD-001", "scm",
                CarrierCode.of("USPS"), TrackingNo.of("TRACK-1"),
                DispatchStatus.DISPATCHED, null, Carrier.EASYPOST, 1, Instant.now(), Instant.now());

        Dispatch result = useCase.dispatch(dispatched);

        assertThat(result).isSameAs(dispatched);
        assertThat(result.getStatus()).isEqualTo(DispatchStatus.DISPATCHED);
        // Idempotent short-circuit — the cached ack stands: no vendor call, no write.
        verify(dispatchPort, never()).dispatch(any());
        verify(persistencePort, never()).save(any());
    }
}
