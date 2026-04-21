package com.example.payment.application.service;

import com.example.payment.application.exception.UnauthorizedPaymentAccessException;
import com.example.payment.domain.exception.PaymentNotFoundException;
import com.example.payment.domain.model.Payment;
import com.example.payment.application.port.out.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentQueryService 단위 테스트")
class PaymentQueryServiceTest {

    @InjectMocks
    private PaymentQueryService paymentQueryService;

    @Mock
    private PaymentRepository paymentRepository;

    @Test
    @DisplayName("정상 조회 시 Payment를 반환한다")
    void getPaymentByOrderId_validRequest_returnsPayment() {
        Payment payment = Payment.create("order-1", "user-1", 30000L);
        given(paymentRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));

        Payment result = paymentQueryService.getPaymentByOrderId("order-1", "user-1");

        assertThat(result.getOrderId()).isEqualTo("order-1");
    }

    @Test
    @DisplayName("존재하지 않는 orderId 조회 시 PaymentNotFoundException이 발생한다")
    void getPaymentByOrderId_notFound_throwsPaymentNotFoundException() {
        given(paymentRepository.findByOrderId("order-x")).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentQueryService.getPaymentByOrderId("order-x", "user-1"))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    @DisplayName("다른 사용자가 조회 시 UnauthorizedPaymentAccessException이 발생한다")
    void getPaymentByOrderId_differentUser_throwsUnauthorized() {
        Payment payment = Payment.create("order-1", "user-1", 30000L);
        given(paymentRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentQueryService.getPaymentByOrderId("order-1", "attacker"))
                .isInstanceOf(UnauthorizedPaymentAccessException.class);
    }
}
