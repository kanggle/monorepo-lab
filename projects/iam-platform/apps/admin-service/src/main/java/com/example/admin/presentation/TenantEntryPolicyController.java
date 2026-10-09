package com.example.admin.presentation;

import com.example.admin.application.TenantEntryPolicyPrecheckUseCase;
import com.example.admin.application.TenantEntryPolicyPrecheckUseCase.EnrolmentSummary;
import com.example.admin.application.TenantEntryPolicyUseCase;
import com.example.admin.application.port.TenantEntryPolicyManagementPort.EntryPolicyView;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.security.OperatorContextHolder;
import com.example.admin.presentation.aspect.RequiresPermission;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * TASK-MONO-771 S5 (ADR-MONO-080 D4 · R2, owner decision OD-1) — the tenant entry policy:
 * «entering this tenant as an operator requires a second factor». Contract: {@code admin-api.md}
 * § Tenant Entry Policy. Thin: the endpoint gate is {@code tenant.security.manage}; the per-tenant scope
 * check, the existence check and the audit row live in {@link TenantEntryPolicyUseCase}.
 *
 * <p>{@code PUT} is an idempotent full replace: {@code X-Operator-Reason} required, no
 * {@code Idempotency-Key} (the org-scope PUT convention).
 */
@RestController
@RequestMapping("/api/admin/tenants/{tenantId}/entry-policy")
@RequiredArgsConstructor
public class TenantEntryPolicyController {

    private final TenantEntryPolicyUseCase useCase;
    private final TenantEntryPolicyPrecheckUseCase precheckUseCase;

    @GetMapping
    @RequiresPermission(Permission.TENANT_SECURITY_MANAGE)
    public ResponseEntity<EntryPolicyResponse> getEntryPolicy(@PathVariable String tenantId) {
        return ResponseEntity.ok(EntryPolicyResponse.from(
                useCase.get(OperatorContextHolder.require(), tenantId)));
    }

    /** The «켜기 전 사전 점검» read — how many of the tenant's operators have no second factor yet. */
    @GetMapping("/enrolment-summary")
    @RequiresPermission(Permission.TENANT_SECURITY_MANAGE)
    public ResponseEntity<EnrolmentSummaryResponse> getEnrolmentSummary(@PathVariable String tenantId) {
        return ResponseEntity.ok(EnrolmentSummaryResponse.from(
                precheckUseCase.summarize(OperatorContextHolder.require(), tenantId)));
    }

    @PutMapping
    @RequiresPermission(Permission.TENANT_SECURITY_MANAGE)
    public ResponseEntity<EntryPolicyResponse> setEntryPolicy(
            @PathVariable String tenantId,
            @RequestHeader(value = "X-Operator-Reason", required = false) String headerReason,
            @RequestBody(required = false) JsonNode body) {
        String reason = decodeReason(ControllerReasonSupport.requireReason(headerReason));
        boolean requireMfa = requireMfaOf(body);
        return ResponseEntity.ok(EntryPolicyResponse.from(
                useCase.set(OperatorContextHolder.require(), tenantId, requireMfa, reason)));
    }

    /** {@code requireMfa} must be present and a JSON boolean — {@code "true"} / {@code 1} / absent → 400. */
    private static boolean requireMfaOf(JsonNode body) {
        if (body == null || !body.isObject() || !body.has("requireMfa") || !body.get("requireMfa").isBoolean()) {
            throw new IllegalArgumentException("Body must carry 'requireMfa' as a JSON boolean");
        }
        return body.get("requireMfa").booleanValue();
    }

    /** The console percent-encodes the reason header (TASK-MONO-176); a raw value passes through. */
    private static String decodeReason(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }

    /** enrolment-summary response — {@code operators = enrolled + notEnrolled + unlinked}. */
    public record EnrolmentSummaryResponse(String tenantId, int operators, int enrolled, int notEnrolled, int unlinked) {
        static EnrolmentSummaryResponse from(EnrolmentSummary s) {
            return new EnrolmentSummaryResponse(s.tenantId(), s.operators(), s.enrolled(), s.notEnrolled(), s.unlinked());
        }
    }

    /** GET / PUT response — every key always present ({@code null} when the policy was never set). */
    public record EntryPolicyResponse(String tenantId, boolean requireMfa, Instant updatedAt, String updatedBy) {
        static EntryPolicyResponse from(EntryPolicyView v) {
            return new EntryPolicyResponse(v.tenantId(), v.requireMfa(), v.updatedAt(), v.updatedByOperatorId());
        }
    }
}
