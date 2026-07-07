package com.example.settlement.presentation;

import com.example.settlement.application.service.CommissionRateAdminService;
import com.example.settlement.application.service.SettlementQueryService;
import com.example.settlement.domain.model.CommissionAccrual;
import com.example.settlement.domain.model.CommissionRate;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.settlement.domain.model.SellerBalance;
import com.example.settlement.presentation.dto.AccrualListResponse;
import com.example.settlement.presentation.dto.CommissionRateResponse;
import com.example.settlement.presentation.dto.SellerBalanceResponse;
import com.example.settlement.presentation.dto.SetCommissionRateRequest;
import com.example.web.exception.AccessDeniedException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operator-plane settlement read + commission-rate admin (settlement-api.md). All
 * endpoints require an operator (gateway-trusted {@code X-User-Role = ECOMMERCE_OPERATOR}). There
 * is <b>no accrual write path</b> — commission is booked only from the event streams.
 * Tenant + seller scope are applied below the controller (filters → context → repo
 * chokepoint).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/settlements")
public class SettlementController {

    private static final String ROLE_ADMIN = "ECOMMERCE_OPERATOR";

    private final SettlementQueryService queryService;
    private final CommissionRateAdminService rateAdminService;

    @GetMapping("/accruals")
    public ResponseEntity<AccrualListResponse> listAccruals(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestParam(required = false) String sellerId,
            @RequestParam(required = false) String orderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validateAdminRole(userRole);
        PageQuery pageQuery = PageQuery.of(page, size, "occurredAt", "DESC");
        PageResult<CommissionAccrual> result = queryService.listAccruals(sellerId, orderId, pageQuery);
        return ResponseEntity.ok(AccrualListResponse.from(result));
    }

    @GetMapping("/sellers/{sellerId}/balance")
    public ResponseEntity<SellerBalanceResponse> sellerBalance(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PathVariable String sellerId) {
        validateAdminRole(userRole);
        SellerBalance balance = queryService.sellerBalance(sellerId);
        return ResponseEntity.ok(SellerBalanceResponse.from(balance));
    }

    @GetMapping("/commission-rates/{sellerId}")
    public ResponseEntity<CommissionRateResponse> getRate(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PathVariable String sellerId) {
        validateAdminRole(userRole);
        CommissionRate rate = rateAdminService.getEffectiveRate(sellerId);
        return ResponseEntity.ok(CommissionRateResponse.from(sellerId, rate));
    }

    @PutMapping("/commission-rates/{sellerId}")
    public ResponseEntity<CommissionRateResponse> setRate(
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @PathVariable String sellerId,
            @Valid @RequestBody SetCommissionRateRequest request) {
        validateAdminRole(userRole);
        CommissionRate rate = rateAdminService.setRate(sellerId, request.rateBps());
        return ResponseEntity.ok(CommissionRateResponse.from(sellerId, rate));
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
