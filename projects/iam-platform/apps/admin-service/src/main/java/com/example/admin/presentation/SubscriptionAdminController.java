package com.example.admin.presentation;

import com.example.admin.application.ManageSubscriptionUseCase;
import com.example.admin.application.exception.ReasonRequiredException;
import com.example.admin.application.tenant.SubscriptionMutationSummary;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.security.OperatorContextHolder;
import com.example.admin.presentation.aspect.RequiresPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TASK-BE-343 (ADR-MONO-023 § 3.3 step 2b — D3): the operator-facing tenant↔domain
 * subscription management surface. Gated by {@code subscription.manage} (DISTINCT
 * from {@code operator.manage} — entitlement and IAM planes are separately
 * delegable, ADR-023 D2/D3). The actual entitlement write is delegated to
 * account-service (the entitlement authority) via {@link ManageSubscriptionUseCase}.
 *
 * <p>Downstream account-service errors surface unchanged: 404
 * {@code TENANT_NOT_FOUND}/{@code SUBSCRIPTION_NOT_FOUND}, 409
 * {@code SUBSCRIPTION_ALREADY_EXISTS}/{@code SUBSCRIPTION_TRANSITION_INVALID},
 * 5xx → 503 {@code DOWNSTREAM_ERROR}/{@code CIRCUIT_OPEN}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/subscriptions")
public class SubscriptionAdminController {

    private final ManageSubscriptionUseCase manageSubscriptionUseCase;

    /** POST /api/admin/subscriptions — subscribe (create, ACTIVE). */
    @PostMapping
    @RequiresPermission(Permission.SUBSCRIPTION_MANAGE)
    public ResponseEntity<SubscriptionResponse> subscribe(
            @RequestHeader(value = "X-Operator-Reason", required = false) String headerReason,
            @Valid @RequestBody SubscribeRequest body) {
        String reason = requireReason(headerReason);
        SubscriptionMutationSummary result = manageSubscriptionUseCase.subscribe(
                body.tenantId(), body.domainKey(), OperatorContextHolder.require(), reason);
        return ResponseEntity.status(HttpStatus.CREATED).body(SubscriptionResponse.from(result));
    }

    /**
     * PATCH /api/admin/subscriptions/{tenantId}/{domainKey}/status — transition
     * (suspend/resume/cancel). The SubscriptionStatus guard (account-service)
     * rejects illegal transitions (409).
     */
    @PatchMapping("/{tenantId}/{domainKey}/status")
    @RequiresPermission(Permission.SUBSCRIPTION_MANAGE)
    public ResponseEntity<SubscriptionResponse> changeStatus(
            @PathVariable String tenantId,
            @PathVariable String domainKey,
            @RequestHeader(value = "X-Operator-Reason", required = false) String headerReason,
            @Valid @RequestBody ChangeStatusRequest body) {
        String reason = requireReason(headerReason);
        SubscriptionMutationSummary result = manageSubscriptionUseCase.changeStatus(
                tenantId, domainKey, body.status(), OperatorContextHolder.require(), reason);
        return ResponseEntity.ok(SubscriptionResponse.from(result));
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ReasonRequiredException();
        }
        return reason;
    }

    // ---- DTOs ----------------------------------------------------------------

    public record SubscribeRequest(
            @NotBlank String tenantId,
            @NotBlank String domainKey
    ) {}

    public record ChangeStatusRequest(
            @NotBlank String status
    ) {}

    public record SubscriptionResponse(
            String tenantId,
            String domainKey,
            String previousStatus,
            String currentStatus,
            String occurredAt
    ) {
        static SubscriptionResponse from(SubscriptionMutationSummary s) {
            return new SubscriptionResponse(
                    s.tenantId(), s.domainKey(), s.previousStatus(), s.currentStatus(),
                    s.occurredAt() == null ? null : s.occurredAt().toString());
        }
    }
}
