package com.example.payment.adapter.in.rest;

import com.example.payment.application.service.PaymentConfirmResult;
import com.example.payment.application.service.PaymentConfirmService;
import com.example.payment.application.service.PaymentProcessingService;
import com.example.payment.application.service.PaymentQueryService;
import com.example.payment.application.service.PaymentRefundService;
import com.example.payment.domain.exception.InvalidPaymentException;
import com.example.payment.domain.model.Payment;
import com.example.payment.adapter.in.rest.dto.PaymentConfirmRequest;
import com.example.payment.adapter.in.rest.dto.PaymentConfirmResponse;
import com.example.payment.adapter.in.rest.dto.PaymentCreateRequest;
import com.example.payment.adapter.in.rest.dto.PaymentDetailResponse;
import com.example.payment.adapter.in.rest.dto.PaymentRefundRequest;
import com.example.payment.adapter.in.rest.dto.PaymentRefundResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
@Validated
public class PaymentController {

    private final PaymentQueryService paymentQueryService;
    private final PaymentConfirmService paymentConfirmService;
    private final PaymentProcessingService paymentProcessingService;
    private final PaymentRefundService paymentRefundService;

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<PaymentDetailResponse> getPaymentByOrderId(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String orderId
    ) {
        requireUserId(userId);
        Payment payment = paymentQueryService.getPaymentByOrderId(orderId, userId);
        return ResponseEntity.ok(PaymentDetailResponse.from(payment));
    }

    @PostMapping
    public ResponseEntity<Void> createPayment(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody PaymentCreateRequest request
    ) {
        // userId is sourced from the X-User-Id header only — never from the request body.
        // See TASK-BE-128 / specs/contracts/http/payment-api.md.
        requireUserId(userId);
        paymentProcessingService.processPayment(
                request.orderId(), userId, request.amount()
        );
        return ResponseEntity.status(201).build();
    }

    @PostMapping("/confirm")
    public ResponseEntity<PaymentConfirmResponse> confirmPayment(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody PaymentConfirmRequest request
    ) {
        requireUserId(userId);
        PaymentConfirmResult result = paymentConfirmService.confirm(
                userId, request.paymentKey(), request.orderId(), request.amount()
        );
        return ResponseEntity.ok(PaymentConfirmResponse.from(result));
    }

    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<PaymentRefundResponse> refundPayment(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String paymentId,
            @Valid @RequestBody PaymentRefundRequest request
    ) {
        requireUserId(userId);
        Payment payment = paymentRefundService.refundPayment(paymentId, userId, request.amount());
        return ResponseEntity.ok(PaymentRefundResponse.from(payment));
    }

    private void requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new InvalidPaymentException("X-User-Id 헤더는 필수입니다");
        }
    }
}
