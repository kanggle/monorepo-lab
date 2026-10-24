package com.example.scmplatform.inventoryvisibility.adapter.inbound.messaging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService;
import com.example.scmplatform.inventoryvisibility.config.ProjectionTenant;
import com.example.scmplatform.inventoryvisibility.domain.error.NodeNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.Acknowledgment;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * TASK-MONO-816 AC-3 — the {@code @RetryableTopic} shape. The retry-topic machinery gives the
 * operator no cause of its own: the framework's default DLT handler logs only
 * {@code "Received message in dlt listener: <topic-partition@offset>"} at INFO (measured on
 * spring-kafka 3.3.1 bytecode: {@code RetryTopicConfigurer$LoggingDltListenerHandlerMethod}), and the
 * exception travels in record headers. So for these five services the listener method's own
 * {@code catch} block is the only place the cause can be logged — on every attempt, including the
 * last one that is routed to the DLT.
 *
 * <p>Most of those catch blocks already log {@code message + exception + partition + offset}. This
 * one — the fail-closed «node absent» rejection — logged {@code e.getMessage()} only, with no
 * partition and no exception class, on an exception that is retried three times and then
 * dead-lettered.
 */
@DisplayName("ScmThirdPartyInboundExpectedConsumer — 실패 원인이 로그에 남는다 (TASK-MONO-816)")
class ConsumerFailureCauseLoggingTest {

    private static final String BODY_SECRET = "PO-NUMBER-MUST-NOT-BE-LOGGED";

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final InventoryVisibilityApplicationService service = mock(InventoryVisibilityApplicationService.class);
    private final Acknowledgment ack = mock(Acknowledgment.class);
    private final ScmThirdPartyInboundExpectedConsumer consumer =
            new ScmThirdPartyInboundExpectedConsumer(service, objectMapper, new ProjectionTenant("scm"));

    private Logger consumerLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        consumerLogger = (Logger) LoggerFactory.getLogger(ScmThirdPartyInboundExpectedConsumer.class);
        appender = new ListAppender<>();
        appender.start();
        consumerLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        consumerLogger.detachAppender(appender);
    }

    @Test
    @DisplayName("fail-closed 거절(NodeNotFoundException) → ERROR 로 예외 클래스 · 메시지 · topic/partition/offset 이 남는다")
    void failClosedRejectionLogsRootCauseAndCoordinates() {
        doThrow(new NodeNotFoundException("node-3pl-7")).when(service)
                .recordThirdPartyInboundExpectation(anyString(), anyString(), anyString(), anyString(), any(), anyList());
        String json = """
                {"eventId":"11111111-1111-1111-1111-111111111111","eventType":"scm.procurement.inbound-expected.third-party",
                 "eventVersion":1,"occurredAt":"2026-10-24T00:00:00Z","producer":"procurement-service",
                 "aggregateType":"PurchaseOrder","aggregateId":"po-1",
                 "payload":{"poId":"po-1","poNumber":"%s","destinationNodeId":"node-3pl-7",
                            "lines":[{"skuCode":"SKU-1","expectedQty":"5"}]}}
                """.formatted(BODY_SECRET);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                ScmThirdPartyInboundExpectedConsumer.TOPIC, 2, 17L, "po-1", json);

        assertThatThrownBy(() -> consumer.consume(record, ack)).isInstanceOf(NodeNotFoundException.class);

        List<ILoggingEvent> errors = appender.list.stream().filter(e -> e.getLevel() == Level.ERROR).toList();
        assertThat(errors).hasSize(1);
        ILoggingEvent error = errors.get(0);
        assertThat(error.getFormattedMessage())
                .contains(ScmThirdPartyInboundExpectedConsumer.TOPIC)
                .contains("partition=2").contains("offset=17")
                .contains("NodeNotFoundException").contains("node-3pl-7");
        assertThat(causeChain(error)).contains(NodeNotFoundException.class.getName());
        assertThat(appender.list).allSatisfy(e ->
                assertThat(e.getFormattedMessage()).doesNotContain(BODY_SECRET));
    }

    private static List<String> causeChain(ILoggingEvent event) {
        List<String> names = new ArrayList<>();
        for (IThrowableProxy t = event.getThrowableProxy(); t != null; t = t.getCause()) {
            names.add(t.getClassName());
        }
        return names;
    }
}
