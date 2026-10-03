package com.example.product.presentation.controller;

import com.example.product.application.dto.SellerSummary;
import com.example.product.application.service.SellerQueryService;
import com.example.product.domain.tenant.TenantContext;
import com.example.product.presentation.dto.InternalSellerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Workload-identity seller read — TASK-MONO-759 ({@code product-api.md} § Internal seller read).
 * The store side of the fan agency → store seller verification (ADR-MONO-079 D2).
 *
 * <p>Authorization is entirely {@code ProductSecurityConfig}'s {@code /internal/**} chain: by
 * the time this method runs the token has verified, carries {@code store.seller.read}, and its
 * {@code tenant_id} is {@code ecommerce}.
 *
 * <p>🔴 <b>The tenant comes from the verified token, not from a header.</b>
 * {@code TenantContextFilter} binds {@code X-Tenant-Id} before security runs; this method
 * re-binds the token's {@code tenant_id} so a forged or stale header cannot point the lookup at
 * another tenant's sellers. The filter clears the binding when the request ends.
 *
 * <p>A seller that does not exist in that tenant → {@code SellerNotFoundException} → 404
 * {@code SELLER_NOT_FOUND} (the existing {@code GlobalExceptionHandler} arm) — the one
 * <em>definite</em> «no such seller» the fan caller maps to {@code Optional.empty()}.
 */
@RestController
@RequestMapping("/internal/sellers")
@RequiredArgsConstructor
public class InternalSellerController {

    private final SellerQueryService sellerQueryService;

    @GetMapping("/{sellerId}")
    public ResponseEntity<InternalSellerResponse> get(@AuthenticationPrincipal Jwt jwt,
                                                      @PathVariable String sellerId) {
        TenantContext.set(jwt.getClaimAsString("tenant_id"));
        SellerSummary seller = sellerQueryService.getSeller(sellerId);
        return ResponseEntity.ok(InternalSellerResponse.from(seller));
    }
}
