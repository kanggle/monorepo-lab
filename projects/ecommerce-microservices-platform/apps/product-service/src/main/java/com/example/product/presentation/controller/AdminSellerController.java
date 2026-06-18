package com.example.product.presentation.controller;

import com.example.product.application.dto.SellerListResult;
import com.example.product.application.dto.SellerSummary;
import com.example.product.application.service.RegisterSellerService;
import com.example.product.application.service.SellerQueryService;
import com.example.product.presentation.dto.RegisterSellerRequest;
import com.example.product.presentation.dto.RegisterSellerResponse;
import com.example.product.presentation.dto.SellerListResponse;
import com.example.product.presentation.dto.SellerResponse;
import com.example.web.exception.AccessDeniedException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OPERATOR-plane seller onboarding + lifecycle + read surface (ADR-MONO-030 Step 3
 * §3.1 / Step 4 facet f, ADR-MONO-042). Onboarding mints a real IAM seller-operator
 * account fail-soft (the seller is born {@code PENDING_PROVISIONING} → {@code ACTIVE}
 * on provision); operators can re-provision a PENDING seller, and suspend/close a
 * seller (which locks/deactivates the backing account). Seller is the inner
 * marketplace axis nested under {@code tenant_id}; settlement/commission remain Step 4.
 *
 * <p>Authorization is promotions-exact: every endpoint calls
 * {@link #validateAdminRole(String)} ({@code X-User-Role == ADMIN}); the routes sit
 * behind the gateway {@code /api/admin/**} OPERATOR branch.
 */
@RestController
@RequestMapping("/api/admin/sellers")
@RequiredArgsConstructor
public class AdminSellerController {

    private static final String ROLE_ADMIN = "ADMIN";

    /** Page-size cap mirrored from the product operator list. */
    private static final int MAX_PAGE_SIZE = 100;

    private final RegisterSellerService registerSellerService;
    private final SellerQueryService sellerQueryService;

    /**
     * Tenant-scoped paged list of sellers (ADR-MONO-030 Step 4 facet f). Tenant
     * isolation is the repository {@code WHERE tenant_id} chokepoint (M6); the
     * per-tenant {@code default} seller is a real ACTIVE row and appears here.
     */
    @GetMapping
    public ResponseEntity<SellerListResponse> list(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validateAdminRole(userRole);
        int cappedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        SellerListResult result = sellerQueryService.listSellers(Math.max(page, 0), cappedSize);
        return ResponseEntity.ok(SellerListResponse.from(result));
    }

    /**
     * Tenant-scoped seller detail (ADR-MONO-030 Step 4 facet f). A cross-tenant or
     * missing {@code sellerId} → 404 (existence hidden, M3) — the lookup reuses the
     * tenant-scoped {@code findById}.
     */
    @GetMapping("/{sellerId}")
    public ResponseEntity<SellerResponse> detail(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PathVariable String sellerId) {
        validateAdminRole(userRole);
        SellerSummary seller = sellerQueryService.getSeller(sellerId);
        return ResponseEntity.ok(SellerResponse.from(seller));
    }

    @PostMapping
    public ResponseEntity<RegisterSellerResponse> register(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @Valid @RequestBody RegisterSellerRequest request) {
        validateAdminRole(userRole);
        String sellerId = registerSellerService.register(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisterSellerResponse.from(sellerId));
    }

    /**
     * Re-provision a seller stuck in {@code PENDING_PROVISIONING} (ADR-042 D3 retry —
     * e.g. IAM was unavailable at onboarding). Idempotent: an already-ACTIVE seller is a
     * no-op. 204 on success/no-op; 404 if the seller does not exist in the tenant.
     */
    @PostMapping("/{sellerId}/provision")
    public ResponseEntity<Void> provision(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PathVariable String sellerId) {
        validateAdminRole(userRole);
        registerSellerService.provisionPending(sellerId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Operator SUSPEND (ADR-042 D4): seller ACTIVE → SUSPENDED + lock the backing
     * account. Idempotent + null-safe. 204 on success/no-op; 404 if missing.
     */
    @PostMapping("/{sellerId}/suspend")
    public ResponseEntity<Void> suspend(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PathVariable String sellerId) {
        validateAdminRole(userRole);
        registerSellerService.suspend(sellerId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Operator CLOSE (ADR-042 D4): seller → CLOSED (terminal) + deactivate the backing
     * account. Idempotent + null-safe. 204 on success/no-op; 404 if missing.
     */
    @PostMapping("/{sellerId}/close")
    public ResponseEntity<Void> close(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PathVariable String sellerId) {
        validateAdminRole(userRole);
        registerSellerService.close(sellerId);
        return ResponseEntity.noContent().build();
    }

    private void validateAdminRole(String userRole) {
        if (!hasAdminRole(userRole)) {
            throw new AccessDeniedException();
        }
    }

    private static boolean hasAdminRole(String userRole) {
        if (userRole == null || userRole.isBlank()) {
            return false;
        }
        for (String role : userRole.split(",")) {
            if (ROLE_ADMIN.equalsIgnoreCase(role.trim())) {
                return true;
            }
        }
        return false;
    }
}
