package com.example.product.infrastructure.event;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.product.domain.event.OrderReservationFailedPayload;
import com.example.product.domain.event.ProductCreatedPayload;
import com.example.product.domain.event.ProductEvent;
import com.example.product.infrastructure.metrics.ProductMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaProductEventPublisher 단위 테스트")
class KafkaProductEventPublisherTest {

    @InjectMocks
    private KafkaProductEventPublisher publisher;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private ProductMetrics productMetrics;

    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @BeforeEach
    void setUpLogCapture() {
        logger = (Logger) LoggerFactory.getLogger(KafkaProductEventPublisher.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDownLogCapture() {
        logger.detachAppender(listAppender);
    }

    @Test
    @DisplayName("이벤트 발행 실패 시 event_publish_failure_total 메트릭이 증가한다")
    void publish_kafkaFailure_incrementsEventPublishFailureMetric() {
        ProductEvent event = ProductEvent.created(
                new ProductCreatedPayload("product-1", "테스트 상품", "설명", 10000L, "ACTIVE", "cat-1", null, "default", List.of()));
        given(kafkaTemplate.send(eq("product.product.created"), any(), any()))
                .willThrow(new RuntimeException("Kafka broker unavailable"));

        publisher.publish(event);

        then(productMetrics).should().incrementEventPublishFailure("ProductCreated");
    }

    @Test
    @DisplayName("이벤트 발행 실패 시 ERROR 레벨로 로그가 기록된다")
    void publish_kafkaFailure_logsAtErrorLevel() {
        ProductEvent event = ProductEvent.created(
                new ProductCreatedPayload("product-1", "테스트 상품", "설명", 10000L, "ACTIVE", "cat-1", null, "default", List.of()));
        given(kafkaTemplate.send(eq("product.product.created"), any(), any()))
                .willThrow(new RuntimeException("Kafka broker unavailable"));

        publisher.publish(event);

        assertThat(listAppender.list)
                .filteredOn(e -> e.getLevel() == Level.ERROR)
                .anyMatch(e -> e.getFormattedMessage().contains("Event publishing failed")
                        && e.getFormattedMessage().contains("ProductCreated"));
    }

    @Test
    @DisplayName("이벤트 발행 성공 시 메트릭이 증가하지 않는다")
    void publish_success_doesNotIncrementFailureMetric() {
        ProductEvent event = ProductEvent.created(
                new ProductCreatedPayload("product-1", "테스트 상품", "설명", 10000L, "ACTIVE", "cat-1", null, "default", List.of()));

        publisher.publish(event);

        then(productMetrics).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("OrderReservationFailed는 product.product.reservation-failed 토픽으로 발행된다 (TASK-BE-428)")
    void publish_orderReservationFailed_routesToReservationFailedTopic() {
        ProductEvent event = ProductEvent.orderReservationFailed(
                new OrderReservationFailedPayload("order-1", "INSUFFICIENT_STOCK",
                        List.of(new OrderReservationFailedPayload.Shortage("var-1", 5, 2))));

        publisher.publish(event);

        then(kafkaTemplate).should().send(eq("product.product.reservation-failed"), any(), eq(event));
        then(productMetrics).shouldHaveNoInteractions();
    }
}
