package com.example.payment.adapter.out.event;

import com.example.payment.application.event.PaymentCompletedEvent;
import com.example.payment.application.event.PaymentRefundedEvent;
import com.example.payment.application.port.out.PaymentEventPublisher;
import com.example.payment.application.port.out.PaymentMetricRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@org.springframework.context.annotation.Profile("!standalone")
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {

    private final String topicPaymentCompleted;
    private final String topicPaymentRefunded;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PaymentMetricRecorder paymentMetricRecorder;

    public KafkaPaymentEventPublisher(
            @Value("${app.kafka.topics.payment-completed}") String topicPaymentCompleted,
            @Value("${app.kafka.topics.payment-refunded}") String topicPaymentRefunded,
            KafkaTemplate<String, Object> kafkaTemplate,
            PaymentMetricRecorder paymentMetricRecorder
    ) {
        this.topicPaymentCompleted = topicPaymentCompleted;
        this.topicPaymentRefunded = topicPaymentRefunded;
        this.kafkaTemplate = kafkaTemplate;
        this.paymentMetricRecorder = paymentMetricRecorder;
    }

    @Override
    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        try {
            kafkaTemplate.send(topicPaymentCompleted, event.payload().paymentId(), event);
        } catch (KafkaException e) {
            log.error("Event publishing failed: eventType={}, topic={}, orderId={}", "PaymentCompleted", topicPaymentCompleted, event.payload().orderId(), e);
            paymentMetricRecorder.incrementEventPublishFailure("PaymentCompleted");
        }
    }

    @Override
    public void publishPaymentRefunded(PaymentRefundedEvent event) {
        try {
            kafkaTemplate.send(topicPaymentRefunded, event.payload().paymentId(), event);
        } catch (KafkaException e) {
            log.error("Event publishing failed: eventType={}, topic={}, orderId={}", "PaymentRefunded", topicPaymentRefunded, event.payload().orderId(), e);
            paymentMetricRecorder.incrementEventPublishFailure("PaymentRefunded");
        }
    }
}
