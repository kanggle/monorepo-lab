package com.example.product.presentation.controller;

import com.example.product.application.service.SellerMemberService;
import com.example.product.presentation.dto.SellerMemberDtos.AcceptRequest;
import com.example.product.presentation.dto.SellerMemberDtos.AcceptResponse;
import com.example.web.dto.ErrorResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CONSUMER-plane seller-invitation accept (ADR-MONO-079 D5, TASK-MONO-752; product-api.md
 * § POST /api/seller-invitations/accept). The caller is the invited person, logged in to the store: the gateway
 * authenticates the token and admits {@code CUSTOMER}, then injects {@code X-User-Id} (= {@code sub}, the pool
 * account id) and {@code X-Tenant-Id}.
 *
 * <p>🔴 The account comes <b>only</b> from {@code X-User-Id} (the gateway strips any client-supplied copy). The
 * body carries the token and nothing that names an account or an email — the email check happens in IAM against
 * the account's own email (ticket Failure Scenario 1).
 */
@RestController
@RequestMapping("/api/seller-invitations")
@RequiredArgsConstructor
public class SellerInvitationController {

    private final SellerMemberService sellerMemberService;

    @PostMapping("/accept")
    public ResponseEntity<?> accept(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody AcceptRequest request) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ErrorResponse.of("UNAUTHORIZED", "Sign in to the store to accept the invitation"));
        }
        return ResponseEntity.ok(AcceptResponse.from(sellerMemberService.accept(request.token(), userId)));
    }
}
