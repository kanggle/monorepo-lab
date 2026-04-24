package com.example.product.application.service;

import com.example.product.domain.event.ProductDeletedPayload;
import com.example.product.domain.event.ProductEvent;
import com.example.product.domain.event.ProductEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventPublishingHelper 단위 테스트")
class EventPublishingHelperTest {

    @Mock
    private ProductEventPublisher productEventPublisher;

    private EventPublishingHelper eventPublishingHelper;

    private ProductEvent testEvent;

    @BeforeEach
    void setUp() {
        eventPublishingHelper = new EventPublishingHelper(productEventPublisher);
        testEvent = ProductEvent.deleted(new ProductDeletedPayload(UUID.randomUUID().toString()));
    }

    @Test
    @DisplayName("이벤트 발행 성공 시 publisher에 이벤트가 전달된다")
    void publishSafely_success_delegatesToPublisher() {
        eventPublishingHelper.publishSafely(testEvent, "product", UUID.randomUUID());

        verify(productEventPublisher).publish(testEvent);
    }

    @Test
    @DisplayName("이벤트 발행 실패해도 예외가 전파되지 않는다")
    void publishSafely_publisherThrows_doesNotPropagate() {
        doThrow(new RuntimeException("Kafka connection failed"))
                .when(productEventPublisher).publish(any());

        assertThatCode(() -> eventPublishingHelper.publishSafely(testEvent, "product", UUID.randomUUID()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("발행 실패 시에도 publisher.publish()는 호출된다")
    void publishSafely_publisherThrows_publishStillCalled() {
        doThrow(new RuntimeException("Kafka connection failed"))
                .when(productEventPublisher).publish(any());

        eventPublishingHelper.publishSafely(testEvent, "product", UUID.randomUUID());

        verify(productEventPublisher).publish(testEvent);
    }

}
