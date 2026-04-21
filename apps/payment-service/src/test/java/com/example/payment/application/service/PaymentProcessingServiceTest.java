package com.example.payment.application.service;

import com.example.payment.application.port.out.PaymentMetricRecorder;
import com.example.payment.domain.model.Payment;
import com.example.payment.domain.model.PaymentStatus;
import com.example.payment.application.port.out.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentProcessingService 단위 테스트")
class PaymentProcessingServiceTest {

    private PaymentProcessingService paymentProcessingService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentMetricRecorder paymentMetricRecorder;

    @BeforeEach
    void setUp() {
        paymentProcessingService = new PaymentProcessingService(
                paymentRepository, paymentMetricRecorder
        );
    }

    @Test
    @DisplayName("OrderPlaced 처리 시 Payment가 PENDING 상태로 저장된다")
    void processPayment_newOrder_savesPendingPayment() {
        given(paymentRepository.findByOrderId("order-1")).willReturn(Optional.empty());
        given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        paymentProcessingService.processPayment("order-1", "user-1", 30000L);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(paymentCaptor.getValue().getOrderId()).isEqualTo("order-1");

        verify(paymentMetricRecorder).incrementPaymentCreated();
        verify(paymentMetricRecorder, never()).incrementPaymentCompleted();
    }

    @Test
    @DisplayName("동일 orderId에 대해 이미 Payment가 존재하면 처리를 생략한다 (멱등)")
    void processPayment_duplicateOrder_skipsProcessing() {
        Payment existing = Payment.create("order-1", "user-1", 30000L);
        given(paymentRepository.findByOrderId("order-1")).willReturn(Optional.of(existing));

        paymentProcessingService.processPayment("order-1", "user-1", 30000L);

        verify(paymentRepository, never()).save(any());
    }
}
