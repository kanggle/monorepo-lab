package com.example.product.presentation.controller;

import com.example.product.application.service.RegisterSellerService;
import com.example.product.presentation.dto.RegisterSellerRequest;
import com.example.product.presentation.dto.RegisterSellerResponse;
import com.example.web.exception.AccessDeniedException;
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
 * OPERATOR-plane seller registration (ADR-MONO-030 Step 3 §3.1). Minimal v1
 * lifecycle: register a seller (ACTIVE) within the current tenant. Seller is the
 * inner marketplace axis nested under {@code tenant_id} — onboarding flow,
 * settlement, and commission are out of scope (Step 4).
 */
@RestController
@RequestMapping("/api/admin/sellers")
@RequiredArgsConstructor
public class AdminSellerController {

    private static final String ROLE_ADMIN = "ADMIN";

    private final RegisterSellerService registerSellerService;

    @PostMapping
    public ResponseEntity<RegisterSellerResponse> register(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @Valid @RequestBody RegisterSellerRequest request) {
        validateAdminRole(userRole);
        String sellerId = registerSellerService.register(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisterSellerResponse.from(sellerId));
    }

    private void validateAdminRole(String userRole) {
        if (!ROLE_ADMIN.equalsIgnoreCase(userRole)) {
            throw new AccessDeniedException();
        }
    }
}
