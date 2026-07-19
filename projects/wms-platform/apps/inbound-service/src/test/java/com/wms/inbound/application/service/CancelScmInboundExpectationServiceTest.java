package com.wms.inbound.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wms.inbound.application.command.CancelScmInboundExpectationCommand;
import com.wms.inbound.application.port.out.AsnPersistencePort;
import com.wms.inbound.application.port.out.InboundEventPort;
import com.wms.inbound.domain.event.AsnCancelledEvent;
import com.wms.inbound.domain.model.Asn;
import com.wms.inbound.domain.model.AsnSource;
import com.wms.inbound.domain.model.AsnStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CancelScmInboundExpectationServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-19T06:00:00Z");

    @Mock AsnPersistencePort asnPersistence;
    @Mock InboundEventPort eventPort;

    CancelScmInboundExpectationService sut;

    @BeforeEach
    void setUp() {
        sut = new CancelScmInboundExpectationService(asnPersistence, eventPort,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    @Test
    void cancel_openExpectation_marksCancelledAndPublishes() {
        Asn open = asn("PO-1", AsnStatus.CREATED);
        when(asnPersistence.findOpenByPoNumber("PO-1")).thenReturn(Optional.of(open));
        when(asnPersistence.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sut.cancel(new CancelScmInboundExpectationCommand(UUID.randomUUID(), "PO-1", "SUPPLIER_WITHDREW"));

        ArgumentCaptor<Asn> captor = ArgumentCaptor.forClass(Asn.class);
        verify(asnPersistence).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AsnStatus.CANCELLED);
        verify(eventPort).publish(any(AsnCancelledEvent.class));
    }

    @Test
    void cancel_noOpenExpectation_isNoOp() {
        when(asnPersistence.findOpenByPoNumber("PO-GONE")).thenReturn(Optional.empty());

        sut.cancel(new CancelScmInboundExpectationCommand(UUID.randomUUID(), "PO-GONE", "x"));

        verify(asnPersistence, never()).save(any());
        verify(eventPort, never()).publish(any());
    }

    @Test
    void cancel_alreadyReceived_isNoOp() {
        // IN_PUTAWAY is still "open" (not CLOSED/CANCELLED) but no longer cancellable — goods received.
        Asn received = asn("PO-2", AsnStatus.IN_PUTAWAY);
        when(asnPersistence.findOpenByPoNumber("PO-2")).thenReturn(Optional.of(received));

        sut.cancel(new CancelScmInboundExpectationCommand(UUID.randomUUID(), "PO-2", "x"));

        verify(asnPersistence, never()).save(any());
        verify(eventPort, never()).publish(any());
    }

    private static Asn asn(String poNumber, AsnStatus status) {
        return new Asn(UUID.randomUUID(), "ASN-" + poNumber, AsnSource.SCM_PROCUREMENT,
                UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 7, 24), null,
                poNumber, UUID.randomUUID(), status, 0L,
                FIXED_NOW, "system:scm-procurement", FIXED_NOW, "system:scm-procurement", List.of());
    }
}
