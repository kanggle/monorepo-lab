package com.wms.inbound.adapter.in.messaging.scm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wms.inbound.application.command.CancelScmInboundExpectationCommand;
import com.wms.inbound.application.command.CreateScmInboundExpectationCommand;
import com.wms.inbound.application.port.in.CancelScmInboundExpectationUseCase;
import com.wms.inbound.application.port.in.CreateScmInboundExpectationUseCase;
import com.wms.inbound.application.port.out.EventDedupePort;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link ScmInboundExpectedConsumer} dedupe + dispatch wiring (ADR-MONO-050).
 * The Kafka container and DB are not exercised — ports are mocked, parser is real.
 */
class ScmInboundExpectedConsumerTest {

    private EventDedupePort dedupe;
    private CreateScmInboundExpectationUseCase createUseCase;
    private CancelScmInboundExpectationUseCase cancelUseCase;
    private ScmInboundExpectedConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        ScmInboundExpectedEventParser parser = new ScmInboundExpectedEventParser(objectMapper);
        dedupe = mock(EventDedupePort.class);
        createUseCase = mock(CreateScmInboundExpectationUseCase.class);
        cancelUseCase = mock(CancelScmInboundExpectationUseCase.class);
        consumer = new ScmInboundExpectedConsumer(parser, dedupe, createUseCase, cancelUseCase);

        doAnswer(invocation -> {
            Runnable work = invocation.getArgument(2);
            work.run();
            return EventDedupePort.Outcome.APPLIED;
        }).when(dedupe).process(any(UUID.class), any(String.class), any(Runnable.class));
    }

    @Test
    void inboundExpected_dispatchesParsedCommandToCreateUseCase() {
        consumer.onInboundExpected(inboundExpectedEvent());

        ArgumentCaptor<CreateScmInboundExpectationCommand> captor =
                ArgumentCaptor.forClass(CreateScmInboundExpectationCommand.class);
        verify(createUseCase).create(captor.capture());
        CreateScmInboundExpectationCommand cmd = captor.getValue();
        assertThat(cmd.poNumber()).isEqualTo("SCM-PO-2026-00187");
        assertThat(cmd.destinationWarehouseCode()).isEqualTo("WH-SEOUL-01");
        assertThat(cmd.destinationNodeType()).isEqualTo("WMS_WAREHOUSE");
        assertThat(cmd.supplierCode()).isEqualTo("SUP-0043");
        assertThat(cmd.lines()).singleElement().satisfies(l -> {
            assertThat(l.skuCode()).isEqualTo("SKU-A");
            assertThat(l.expectedQty()).isEqualTo(100);
        });
    }

    @Test
    void duplicateEvent_skipsCreateEntirely() {
        when(dedupe.process(any(UUID.class), any(String.class), any(Runnable.class)))
                .thenReturn(EventDedupePort.Outcome.IGNORED_DUPLICATE);

        consumer.onInboundExpected(inboundExpectedEvent());

        verify(createUseCase, never()).create(any());
    }

    @Test
    void malformedJson_rejectedAsIllegalArgument() {
        assertThatThrownBy(() -> consumer.onInboundExpected("not json"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(dedupe, never()).process(any(), any(), any());
        verify(createUseCase, never()).create(any());
    }

    @Test
    void cancelledEvent_dispatchesToCancelUseCase() {
        consumer.onInboundExpectedCancelled(cancelledEvent());

        ArgumentCaptor<CancelScmInboundExpectationCommand> captor =
                ArgumentCaptor.forClass(CancelScmInboundExpectationCommand.class);
        verify(cancelUseCase).cancel(captor.capture());
        assertThat(captor.getValue().poNumber()).isEqualTo("SCM-PO-2026-00187");
    }

    private static String inboundExpectedEvent() {
        return """
                {
                  "eventId": "%s",
                  "eventType": "scm.procurement.inbound-expected",
                  "occurredAt": "2026-07-19T04:12:00Z",
                  "aggregateType": "purchase_order",
                  "aggregateId": "%s",
                  "payload": {
                    "poId": "%s",
                    "poNumber": "SCM-PO-2026-00187",
                    "supplierId": "SUP-0043",
                    "destinationWarehouseId": "WH-SEOUL-01",
                    "destinationNodeType": "WMS_WAREHOUSE",
                    "expectedArrivalDate": "2026-07-24",
                    "currency": "KRW",
                    "lines": [
                      { "skuCode": "SKU-A", "expectedQty": 100, "uom": "EA" }
                    ]
                  }
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    private static String cancelledEvent() {
        return """
                {
                  "eventId": "%s",
                  "eventType": "scm.procurement.inbound-expected.cancelled",
                  "occurredAt": "2026-07-19T05:00:00Z",
                  "aggregateType": "purchase_order",
                  "aggregateId": "%s",
                  "payload": {
                    "poId": "%s",
                    "poNumber": "SCM-PO-2026-00187",
                    "reason": "SUPPLIER_WITHDREW"
                  }
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }
}
