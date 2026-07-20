package com.example.payment.application.service;

import com.example.payment.application.exception.UnauthorizedPaymentAccessException;
import com.example.payment.application.port.out.PaymentMetricRecorder;
import com.example.payment.application.port.out.PaymentRepository;
import com.example.payment.domain.exception.PaymentNotFoundException;
import com.example.payment.domain.model.Payment;
import com.example.payment.domain.model.PaymentStatus;
import org.springframework.dao.DataIntegrityViolationException;
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
        given(paymentRepository.existsByOrderIdAcrossTenants("order-1")).willReturn(false);
        given(paymentRepository.saveAndFlush(any())).willAnswer(inv -> inv.getArgument(0));

        paymentProcessingService.processPayment("order-1", "user-1", 30000L);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        // saveAndFlush, not save: the assigned-@Id deferred-flush hazard (TASK-BE-541)
        // means only a flushed INSERT makes the unique violation catchable.
        verify(paymentRepository).saveAndFlush(paymentCaptor.capture());
        verify(paymentRepository, never()).save(any());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(paymentCaptor.getValue().getOrderId()).isEqualTo("order-1");

        verify(paymentMetricRecorder).incrementPaymentCreated();
        verify(paymentMetricRecorder, never()).incrementPaymentCompleted();
    }

    @Test
    @DisplayName("동일 orderId에 대해 동일 userId로 재시도 시 처리를 생략한다 (멱등)")
    void processPayment_duplicateOrderSameOwner_skipsProcessing() {
        Payment existing = Payment.create("order-1", "user-1", 30000L);
        given(paymentRepository.findByOrderId("order-1")).willReturn(Optional.of(existing));

        paymentProcessingService.processPayment("order-1", "user-1", 30000L);

        verify(paymentRepository, never()).save(any());
        verify(paymentMetricRecorder, never()).incrementPaymentCreated();
    }

    @Test
    @DisplayName("동일 orderId에 대해 다른 userId가 결제를 생성하려 하면 UnauthorizedPaymentAccessException 발생 (TASK-BE-128)")
    void processPayment_duplicateOrderDifferentOwner_throwsUnauthorized() {
        Payment existing = Payment.create("order-1", "victim-user", 30000L);
        given(paymentRepository.findByOrderId("order-1")).willReturn(Optional.of(existing));

        assertThatThrownBy(() ->
                paymentProcessingService.processPayment("order-1", "attacker", 30000L)
        ).isInstanceOf(UnauthorizedPaymentAccessException.class);

        verify(paymentRepository, never()).save(any());
        verify(paymentMetricRecorder, never()).incrementPaymentCreated();
    }

    @Test
    @DisplayName("다른 테넌트가 이미 결제한 orderId 는 404 로 거부한다 — 409 는 교차 테넌트 존재를 누설한다 (TASK-BE-543 AC-1)")
    void processPayment_orderIdOwnedByAnotherTenant_throwsNotFound() {
        // 자기 테넌트 조회는 비어 있다(선체크가 테넌트 스코프이므로 남의 행은 안 보인다).
        given(paymentRepository.findByOrderId("order-1")).willReturn(Optional.empty());
        given(paymentRepository.existsByOrderIdAcrossTenants("order-1")).willReturn(true);

        assertThatThrownBy(() ->
                paymentProcessingService.processPayment("order-1", "user-1", 30000L)
        ).isInstanceOf(PaymentNotFoundException.class);

        // 쓰기에 도달하지 않는다 = 전역 UNIQUE 제약을 건드리지 않는다.
        verify(paymentRepository, never()).saveAndFlush(any());
        verify(paymentRepository, never()).save(any());
        // 거부된 요청을 "생성됨" 으로 세지 않는다.
        verify(paymentMetricRecorder, never()).incrementPaymentCreated();
    }

    @Test
    @DisplayName("선체크를 통과한 두 요청이 경합하면 제약 위반을 같은 404 로 번역한다 — 패자에게 409 가 새면 안 된다 (TASK-BE-543 AC-1)")
    void processPayment_concurrentInsertLosesRace_throwsNotFound() {
        // 경합 창: 두 요청 모두 선체크를 통과한 뒤 한쪽이 먼저 커밋한다.
        // 진짜 중재자는 전역 payments.order_id UNIQUE 이고, 패자는 여기서 DIVE 를 받는다.
        given(paymentRepository.findByOrderId("order-1")).willReturn(Optional.empty());
        given(paymentRepository.existsByOrderIdAcrossTenants("order-1")).willReturn(false);
        given(paymentRepository.saveAndFlush(any()))
                .willThrow(new DataIntegrityViolationException("uq payments.order_id"));

        assertThatThrownBy(() ->
                paymentProcessingService.processPayment("order-1", "user-1", 30000L)
        ).isInstanceOf(PaymentNotFoundException.class);

        // 순차 경로와 같은 응답이어야 은닉이 성립한다.
        verify(paymentMetricRecorder, never()).incrementPaymentCreated();
    }
}
