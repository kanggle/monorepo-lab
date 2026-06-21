package com.example.payment.contract;

import com.example.payment.application.service.PaymentConfirmResult;
import com.example.payment.application.service.PaymentConfirmService;
import com.example.payment.application.service.PaymentProcessingService;
import com.example.payment.application.service.PaymentQueryService;
import com.example.payment.domain.exception.PaymentNotFoundException;
import com.example.payment.domain.model.Payment;
import com.example.payment.adapter.in.rest.GlobalExceptionHandler;
import com.example.payment.adapter.in.rest.PaymentController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Set;

import static com.example.payment.contract.ContractTestHelper.assertFieldsMatch;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * payment-service API 응답 스키마 컨트랙트 검증 테스트.
 * 검증 근거: specs/contracts/http/payment-api.md
 */
@WebMvcTest(PaymentController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("Payment API 컨트랙트 테스트 — specs/contracts/http/payment-api.md")
class PaymentApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentQueryService paymentQueryService;

    @MockitoBean
    private PaymentConfirmService paymentConfirmService;

    @MockitoBean
    private PaymentProcessingService paymentProcessingService;

    @MockitoBean
    private com.example.payment.application.service.PaymentRefundService paymentRefundService;

    private static final String SPEC_REF = "specs/contracts/http/payment-api.md";

    // ─── GET /api/payments/orders/{orderId} — 200 ───────────────────────

    @Test
    @DisplayName("GET /api/payments/orders/{orderId} 응답은 스펙 정의 필드만 포함한다")
    void getPayment_response_containsSpecFields() throws Exception {
        Payment payment = Payment.create("order-1", "user-1", 30000L);
        payment.confirm("test-payment-key", "CARD", "http://receipt.example.com");
        given(paymentQueryService.getPaymentByOrderId("order-1", "user-1")).willReturn(payment);

        MvcResult result = mockMvc.perform(get("/api/payments/orders/order-1")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andReturn();

        assertFieldsMatch(result.getResponse().getContentAsString(),
                Set.of("paymentId", "orderId", "userId", "amount", "status",
                        "paymentKey", "paymentMethod", "receiptUrl",
                        "createdAt", "paidAt", "refundedAt"),
                SPEC_REF + " GET /api/payments/orders/{orderId} 200");
    }

    // ─── POST /api/payments/confirm — 200 ───────────────────────────────

    @Test
    @DisplayName("POST /api/payments/confirm 응답은 스펙 정의 필드만 포함한다")
    void confirmPayment_response_containsSpecFields() throws Exception {
        LocalDateTime paidAt = LocalDateTime.of(2026, 4, 6, 12, 0, 0);
        given(paymentConfirmService.confirm(eq("user-1"), eq("pk_test_123"), eq("order-1"), eq(30000L)))
                .willReturn(new PaymentConfirmResult("pay-1", "order-1", "COMPLETED", "CARD", "https://receipt.url", paidAt));

        MvcResult result = mockMvc.perform(post("/api/payments/confirm")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentKey":"pk_test_123","orderId":"order-1","amount":30000}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        assertFieldsMatch(result.getResponse().getContentAsString(),
                Set.of("paymentId", "orderId", "status", "paymentMethod", "receiptUrl", "paidAt"),
                SPEC_REF + " POST /api/payments/confirm 200");
    }

    // ─── POST /api/payments/{paymentId}/refund — 200 ────────────────────

    @Test
    @DisplayName("POST /api/payments/{paymentId}/refund 응답은 스펙 정의 필드만 포함한다")
    void refundPayment_response_containsSpecFields() throws Exception {
        Payment refunded = Payment.reconstitute(
                "pay-1", "order-1", "user-1", "ecommerce", 30000L, 10000L,
                com.example.payment.domain.model.PaymentStatus.PARTIALLY_REFUNDED,
                LocalDateTime.of(2026, 4, 6, 12, 0, 0),
                LocalDateTime.of(2026, 4, 6, 12, 0, 0),
                LocalDateTime.of(2026, 4, 6, 13, 0, 0),
                "pk_test_123", "CARD", null);
        given(paymentRefundService.refundPayment(eq("pay-1"), eq("user-1"), eq(10000L)))
                .willReturn(refunded);

        MvcResult result = mockMvc.perform(post("/api/payments/pay-1/refund")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":10000}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        assertFieldsMatch(result.getResponse().getContentAsString(),
                Set.of("paymentId", "orderId", "userId", "amount", "refundedAmount", "status", "refundedAt"),
                SPEC_REF + " POST /api/payments/{paymentId}/refund 200");
    }

    // ─── Error Response Format ──────────────────────────────────────────

    @Test
    @DisplayName("에러 응답은 {code, message, timestamp}만 포함한다")
    void errorResponse_containsOnlyCodeMessageTimestamp() throws Exception {
        given(paymentQueryService.getPaymentByOrderId(any(), any()))
                .willThrow(new PaymentNotFoundException("order-x"));

        MvcResult result = mockMvc.perform(get("/api/payments/orders/order-x")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isNotFound())
                .andReturn();

        assertFieldsMatch(result.getResponse().getContentAsString(),
                Set.of("code", "message", "timestamp"),
                "specs/platform/error-handling.md error format");
    }
}
