package com.example.admin.presentation;

import com.example.admin.application.OperatorContext;
import com.example.admin.application.TenantOrgNodePlacementUseCase;
import com.example.admin.application.orgnode.TenantPlacementView;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.security.OperatorContextHolder;
import com.example.admin.presentation.aspect.RequiresPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * TASK-BE-625 (ADR-MONO-047 § 개정 2026-10-07) — a tenant's org-node placement: put it under a
 * node, move it, or take it out. Thin like {@link OrgNodeAdminController}: the endpoint gate is
 * {@code org.manage}, the two-sided reach check lives in the use-case, and account-service owns
 * the data. Contract: {@code admin-api.md} § Org Hierarchy «테넌트 소속».
 *
 * <p>Out of reach is 404 on either side, never 403 — a 403 would confirm that a tenant or node
 * outside the actor's reach exists.
 */
@RestController
@RequestMapping("/api/admin/tenants/{tenantId}/org-node")
@RequiredArgsConstructor
@Validated
public class TenantOrgNodePlacementController {

    private final TenantOrgNodePlacementUseCase placementUseCase;

    /** Rider P2 — the effect of the move, for the confirmation screen. {@code orgNodeId} absent = detach. */
    @GetMapping("/preview")
    @RequiresPermission(Permission.ORG_MANAGE)
    public ResponseEntity<PlacementEffectResponse> previewPlacement(
            @PathVariable String tenantId,
            @RequestParam(value = "orgNodeId", required = false) String orgNodeId) {
        OperatorContext operator = OperatorContextHolder.require();
        return ResponseEntity.ok(PlacementEffectResponse.from(
                placementUseCase.preview(operator, tenantId, orgNodeId)));
    }

    @PutMapping
    @RequiresPermission(Permission.ORG_MANAGE)
    public ResponseEntity<PlacementResponse> placeTenant(
            @PathVariable String tenantId,
            @RequestHeader("X-Operator-Reason") String reason,
            @RequestBody com.fasterxml.jackson.databind.JsonNode request) {
        String target = targetOf(request);
        OperatorContext operator = OperatorContextHolder.require();
        TenantPlacementView result = placementUseCase.place(
                operator, tenantId, target, decodeReason(reason));
        return ResponseEntity.ok(PlacementResponse.from(result));
    }

    /**
     * The {@code orgNodeId} key must be present: {@code null} means "detach", so an empty
     * {@code {}} body must not silently mean the same thing (a typo'd key would otherwise
     * detach the tenant and lift its ceiling).
     */
    private static String targetOf(com.fasterxml.jackson.databind.JsonNode body) {
        if (body == null || !body.isObject() || !body.has("orgNodeId")) {
            throw new IllegalArgumentException("Body must carry the 'orgNodeId' key (null detaches the tenant)");
        }
        com.fasterxml.jackson.databind.JsonNode node = body.get("orgNodeId");
        if (node.isNull()) {
            return null;
        }
        if (!node.isTextual() || node.asText().isBlank()) {
            throw new IllegalArgumentException("'orgNodeId' must be a non-blank string or null");
        }
        return node.asText();
    }

    private static String decodeReason(String value) {
        if (value == null) return null;
        try {
            return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    // ---- DTOs -----------------------------------------------------------------

    public record PlacementEffectResponse(
            String tenantId,
            String fromOrgNodeId,
            String toOrgNodeId,
            List<String> domainsBefore,
            List<String> domainsAfter,
            List<String> lostDomains,
            List<String> gainedDomains
    ) {
        static PlacementEffectResponse from(TenantPlacementView v) {
            return new PlacementEffectResponse(v.tenantId(), v.fromOrgNodeId(), v.toOrgNodeId(),
                    v.domainsBefore(), v.domainsAfter(), v.lostDomains(), v.gainedDomains());
        }
    }

    public record PlacementResponse(
            String tenantId,
            String fromOrgNodeId,
            String toOrgNodeId,
            List<String> domainsBefore,
            List<String> domainsAfter,
            List<String> lostDomains,
            List<String> gainedDomains,
            boolean changed
    ) {
        static PlacementResponse from(TenantPlacementView v) {
            return new PlacementResponse(v.tenantId(), v.fromOrgNodeId(), v.toOrgNodeId(),
                    v.domainsBefore(), v.domainsAfter(), v.lostDomains(), v.gainedDomains(), v.changed());
        }
    }
}
