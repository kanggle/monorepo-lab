package com.example.payment.adapter.in.rest;

import com.example.payment.application.service.PaymentConfirmResult;
import com.example.payment.application.service.PaymentConfirmService;
import com.example.payment.application.service.PaymentProcessingService;
import com.example.payment.application.service.PaymentQueryService;
import com.example.payment.application.service.PaymentRefundService;
import com.example.payment.application.exception.IdempotencyKeyRequiredException;
import com.example.payment.application.exception.IdempotencyKeyConflictException;
import com.example.payment.application.exception.UnauthorizedPaymentAccessException;
import com.example.payment.domain.exception.InvalidPaymentException;
import com.example.payment.domain.exception.PaymentNotFoundException;
import com.example.payment.domain.model.Payment;
import com.example.payment.domain.model.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("PaymentController 슬라이스 테스트")
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentQueryService paymentQueryService;

    @MockitoBean
    private PaymentConfirmService paymentConfirmService;

    @MockitoBean
    private PaymentProcessingService paymentProcessingService;

    @MockitoBean
    private PaymentRefundService paymentRefundService;

    @Nested
    @DisplayName("GET /api/payments/orders/{orderId}")
    class GetPaymentByOrderId {

        @Test
        @DisplayName("정상 조회 시 200과 결제 정보를 반환한다")
        void getPayment_validRequest_returns200() throws Exception {
            Payment payment = Payment.create("order-1", "user-1", 30000L);
            payment.confirm("test-payment-key", "CARD", "http://receipt.example.com");
            given(paymentQueryService.getPaymentByOrderId("order-1", "user-1")).willReturn(payment);

            mockMvc.perform(get("/api/payments/orders/order-1").header("X-User-Id", "user-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderId").value("order-1"))
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.amount").value(30000));
        }

        @Test
        @DisplayName("X-User-Id 헤더 누락 시 400 반환")
        void getPayment_missingUserId_returns400() throws Exception {
            mockMvc.perform(get("/api/payments/orders/order-1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_REQUEST"));
        }

        @Test
        @DisplayName("존재하지 않는 orderId 조회 시 404 반환")
        void getPayment_notFound_returns404() throws Exception {
            given(paymentQueryService.getPaymentByOrderId(any(), any()))
                    .willThrow(new PaymentNotFoundException("order-x"));

            mockMvc.perform(get("/api/payments/orders/order-x").header("X-User-Id", "user-1"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
        }

        @Test
        @DisplayName("다른 사용자 조회 시 403 반환")
        void getPayment_differentUser_returns403() throws Exception {
            given(paymentQueryService.getPaymentByOrderId(any(), any()))
                    .willThrow(new UnauthorizedPaymentAccessException());

            mockMvc.perform(get("/api/payments/orders/order-1").header("X-User-Id", "attacker"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    @Nested
    @DisplayName("POST /api/payments")
    class CreatePayment {

        @Test
        @DisplayName("X-User-Id 헤더 기반으로 결제를 생성하고 201을 반환한다 (요청 바디의 userId 무시)")
        void createPayment_validRequest_returns201() throws Exception {
            mockMvc.perform(post("/api/payments")
                            .header("X-User-Id", "user-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"orderId":"order-1","amount":30000}
                                    """))
                    .andExpect(status().isCreated());

            verify(paymentProcessingService).processPayment("order-1", "user-1", 30000L);
        }

        @Test
        @DisplayName("요청 바디에 userId가 포함되어도 X-User-Id 헤더 값으로만 결제가 생성된다")
        void createPayment_bodyUserIdIgnored_usesHeaderUserId() throws Exception {
            mockMvc.perform(post("/api/payments")
                            .header("X-User-Id", "header-user")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"orderId":"order-1","userId":"body-user","amount":30000}
                                    """))
                    .andExpect(status().isCreated());

            // 바디의 "body-user"가 아닌 헤더의 "header-user"가 전달되어야 한다
            verify(paymentProcessingService).processPayment("order-1", "header-user", 30000L);
        }

        @Test
        @DisplayName("X-User-Id 헤더 누락 시 400 / INVALID_PAYMENT_REQUEST 반환")
        void createPayment_missingUserIdHeader_returns400() throws Exception {
            mockMvc.perform(post("/api/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"orderId":"order-1","amount":30000}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_REQUEST"));
        }

        @Test
        @DisplayName("타인 orderId 접근 시(소유권 불일치) 403 / ACCESS_DENIED 반환")
        void createPayment_foreignOrderId_returns403() throws Exception {
            doThrow(new UnauthorizedPaymentAccessException())
                    .when(paymentProcessingService)
                    .processPayment(eq("order-1"), eq("attacker"), eq(30000L));

            mockMvc.perform(post("/api/payments")
                            .header("X-User-Id", "attacker")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"orderId":"order-1","amount":30000}
                                    """))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("깨진 JSON 본문이면 400 / VALIDATION_ERROR 반환")
        void createPayment_malformedBody_returns400() throws Exception {
            mockMvc.perform(post("/api/payments")
                            .header("X-User-Id", "user-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderId\":"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value("Malformed request body"));
        }

    }

    @Nested
    @DisplayName("POST /api/payments/confirm")
    class ConfirmPayment {

        @Test
        @DisplayName("정상 confirm 시 200과 결제 승인 결과를 반환한다")
        void confirmPayment_validRequest_returns200() throws Exception {
            LocalDateTime paidAt = LocalDateTime.of(2026, 4, 6, 12, 0, 0);
            given(paymentConfirmService.confirm(eq("user-1"), eq("pk_test_123"), eq("order-1"), eq(30000L)))
                    .willReturn(new PaymentConfirmResult("pay-1", "order-1", "COMPLETED", "CARD", "https://receipt.url", paidAt));

            mockMvc.perform(post("/api/payments/confirm")
                            .header("X-User-Id", "user-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"paymentKey":"pk_test_123","orderId":"order-1","amount":30000}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentId").value("pay-1"))
                    .andExpect(jsonPath("$.orderId").value("order-1"))
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.paymentMethod").value("CARD"))
                    .andExpect(jsonPath("$.receiptUrl").value("https://receipt.url"));
        }

        @Test
        @DisplayName("X-User-Id 헤더 누락 시 400 반환")
        void confirmPayment_missingUserId_returns400() throws Exception {
            mockMvc.perform(post("/api/payments/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"paymentKey":"pk_test_123","orderId":"order-1","amount":30000}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_REQUEST"));
        }

        @Test
        @DisplayName("존재하지 않는 orderId로 confirm 시 404 반환")
        void confirmPayment_notFound_returns404() throws Exception {
            given(paymentConfirmService.confirm(any(), any(), any(), any(long.class)))
                    .willThrow(new PaymentNotFoundException("order-x"));

            mockMvc.perform(post("/api/payments/confirm")
                            .header("X-User-Id", "user-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"paymentKey":"pk_test_123","orderId":"order-x","amount":30000}
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
        }

        @Test
        @DisplayName("다른 사용자가 confirm 시 403 반환")
        void confirmPayment_differentUser_returns403() throws Exception {
            given(paymentConfirmService.confirm(any(), any(), any(), any(long.class)))
                    .willThrow(new UnauthorizedPaymentAccessException());

            mockMvc.perform(post("/api/payments/confirm")
                            .header("X-User-Id", "attacker")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"paymentKey":"pk_test_123","orderId":"order-1","amount":30000}
                                    """))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    @Nested
    @DisplayName("POST /api/payments/{paymentId}/refund")
    class RefundPayment {

        private Payment partiallyRefundedPayment() {
            return Payment.reconstitute(
                    "pay-1", "order-1", "user-1", "ecommerce", 30000L, 10000L,
                    PaymentStatus.PARTIALLY_REFUNDED,
                    LocalDateTime.of(2026, 4, 6, 12, 0, 0),
                    LocalDateTime.of(2026, 4, 6, 12, 0, 0),
                    LocalDateTime.of(2026, 4, 6, 13, 0, 0),
                    "pk_test_123", "CARD", null
            );
        }

        /** TASK-BE-535: Idempotency-Key is required on this funds-out endpoint. */
        private static final String KEY = "idem-key-1";

        @Test
        @DisplayName("부분 환불 성공 시 200과 누적 환불액/상태를 반환한다")
        void refund_partialSuccess_returns200() throws Exception {
            given(paymentRefundService.refundPayment(eq("pay-1"), eq("user-1"), eq(10000L), eq(KEY)))
                    .willReturn(partiallyRefundedPayment());

            mockMvc.perform(post("/api/payments/pay-1/refund")
                            .header("X-User-Id", "user-1")
                            .header("Idempotency-Key", KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"amount":10000}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentId").value("pay-1"))
                    .andExpect(jsonPath("$.amount").value(30000))
                    .andExpect(jsonPath("$.refundedAmount").value(10000))
                    .andExpect(jsonPath("$.status").value("PARTIALLY_REFUNDED"));

            // The header must reach the service — it is the whole guard.
            verify(paymentRefundService).refundPayment("pay-1", "user-1", 10000L, KEY);
        }

        @Test
        @DisplayName("초과 환불 시 400 / INVALID_PAYMENT_REQUEST 반환")
        void refund_overRefund_returns400() throws Exception {
            doThrow(new InvalidPaymentException("over-refund"))
                    .when(paymentRefundService)
                    .refundPayment(eq("pay-1"), eq("user-1"), eq(40000L), eq(KEY));

            mockMvc.perform(post("/api/payments/pay-1/refund")
                            .header("X-User-Id", "user-1")
                            .header("Idempotency-Key", KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"amount":40000}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_REQUEST"));
        }

        @Test
        @DisplayName("존재하지 않는 paymentId이면 404 / PAYMENT_NOT_FOUND 반환")
        void refund_notFound_returns404() throws Exception {
            doThrow(new PaymentNotFoundException("missing"))
                    .when(paymentRefundService)
                    .refundPayment(eq("missing"), eq("user-1"), eq(10000L), eq(KEY));

            mockMvc.perform(post("/api/payments/missing/refund")
                            .header("X-User-Id", "user-1")
                            .header("Idempotency-Key", KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"amount":10000}
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
        }

        @Test
        @DisplayName("소유자가 아니면 403 / ACCESS_DENIED 반환")
        void refund_nonOwner_returns403() throws Exception {
            doThrow(new UnauthorizedPaymentAccessException())
                    .when(paymentRefundService)
                    .refundPayment(eq("pay-1"), eq("attacker"), eq(10000L), eq(KEY));

            mockMvc.perform(post("/api/payments/pay-1/refund")
                            .header("X-User-Id", "attacker")
                            .header("Idempotency-Key", KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"amount":10000}
                                    """))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("X-User-Id 헤더 누락 시 400 / INVALID_PAYMENT_REQUEST 반환")
        void refund_missingUserId_returns400() throws Exception {
            mockMvc.perform(post("/api/payments/pay-1/refund")
                            .header("Idempotency-Key", KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"amount":10000}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_REQUEST"));
        }

        // ── TASK-BE-535 ──────────────────────────────────────────────────────

        @Test
        @DisplayName("Idempotency-Key 헤더 누락 시 400 / IDEMPOTENCY_KEY_REQUIRED 반환")
        void refund_missingIdempotencyKey_returns400() throws Exception {
            doThrow(new IdempotencyKeyRequiredException("Idempotency-Key 헤더는 필수입니다"))
                    .when(paymentRefundService)
                    .refundPayment(eq("pay-1"), eq("user-1"), eq(10000L), isNull());

            mockMvc.perform(post("/api/payments/pay-1/refund")
                            .header("X-User-Id", "user-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"amount":10000}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        }

        @Test
        @DisplayName("같은 키를 다른 금액으로 재사용하면 409 / IDEMPOTENCY_KEY_CONFLICT 반환")
        void refund_reusedIdempotencyKey_returns409() throws Exception {
            doThrow(new IdempotencyKeyConflictException("key bound to a different amount"))
                    .when(paymentRefundService)
                    .refundPayment(eq("pay-1"), eq("user-1"), eq(20000L), eq(KEY));

            mockMvc.perform(post("/api/payments/pay-1/refund")
                            .header("X-User-Id", "user-1")
                            .header("Idempotency-Key", KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"amount":20000}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));
        }
    }
}
