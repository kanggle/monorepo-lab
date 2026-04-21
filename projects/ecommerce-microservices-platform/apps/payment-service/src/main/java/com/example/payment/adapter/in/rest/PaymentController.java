package com.example.payment.adapter.in.rest;

import com.example.payment.application.service.PaymentConfirmResult;
import com.example.payment.application.service.PaymentConfirmService;
import com.example.payment.application.service.PaymentProcessingService;
import com.example.payment.application.service.PaymentQueryService;
import com.example.payment.domain.exception.InvalidPaymentException;
import com.example.payment.domain.model.Payment;
import com.example.payment.adapter.in.rest.dto.PaymentConfirmRequest;
import com.example.payment.adapter.in.rest.dto.PaymentConfirmResponse;
import com.example.payment.adapter.in.rest.dto.PaymentCreateRequest;
import com.example.payment.adapter.in.rest.dto.PaymentDetailResponse;
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
            @Valid @RequestBody PaymentCreateRequest request
    ) {
        paymentProcessingService.processPayment(
                request.orderId(), request.userId(), request.amount()
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

    private void requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new InvalidPaymentException("X-User-Id 헤더는 필수입니다");
        }
    }
}
