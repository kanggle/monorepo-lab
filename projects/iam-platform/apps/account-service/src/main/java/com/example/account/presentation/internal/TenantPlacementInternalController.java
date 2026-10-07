package com.example.account.presentation.internal;

import com.example.account.application.exception.OrgNodeNotFoundException;
import com.example.account.application.result.TenantPlacementResult;
import com.example.account.application.service.TenantOrgNodePlacementUseCase;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * TASK-BE-625 (ADR-MONO-047 § 개정 2026-10-07): a tenant's org-node placement — read, preview,
 * and the compare-then-write. Pure command surface: admin-service decides who may move a
 * tenant; this controller only knows what exists and what the move does.
 *
 * <p><b>Why {@code /internal/tenant-placements} and not {@code /internal/tenants/{tenantId}/…}.</b>
 * The IAM gateway routes {@code /internal/tenants/**} to this service for tenant workloads and
 * checks only path-tenant ↔ JWT-tenant. A placement write under that prefix would let a tenant's
 * own workload token detach its tenant from a node and escape the node's ceiling — the exact
 * hole the ADR amendment closes. This prefix has no gateway route; it is reachable only
 * service-to-service, behind the {@code /internal/**} client_credentials gate.
 *
 * <p>Contract: {@code specs/contracts/http/internal/admin-to-account.md} § Tenant placement.
 */
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/internal/tenant-placements")
public class TenantPlacementInternalController {

    private final TenantOrgNodePlacementUseCase placementUseCase;

    @GetMapping("/{tenantId}")
    public ResponseEntity<PlacementResponse> current(@PathVariable String tenantId) {
        return ResponseEntity.ok(new PlacementResponse(tenantId, placementUseCase.currentOrgNodeId(tenantId)));
    }

    /** {@code orgNodeId} absent ⟹ the preview of a detach. */
    @GetMapping("/{tenantId}/preview")
    public ResponseEntity<PlacementEffectResponse> preview(
            @PathVariable String tenantId,
            @RequestParam(value = "orgNodeId", required = false) String orgNodeId) {
        return ResponseEntity.ok(PlacementEffectResponse.from(placementUseCase.preview(tenantId, orgNodeId)));
    }

    @PutMapping("/{tenantId}")
    public ResponseEntity<PlacementWriteResponse> place(
            @PathVariable String tenantId,
            @RequestBody @NotNull PlaceRequest body) {
        try {
            TenantPlacementResult result =
                    placementUseCase.place(tenantId, body.orgNodeId(), body.expectedOrgNodeId());
            return ResponseEntity.ok(PlacementWriteResponse.from(result));
        } catch (DataIntegrityViolationException e) {
            // The target node was deleted after the existence check but before commit:
            // fk_tenants_org_node refused the row. The node is gone — that is a 404, the same
            // answer the existence check would have given a moment later.
            throw new OrgNodeNotFoundException(body.orgNodeId());
        }
    }

    // ---- DTOs ----------------------------------------------------------------

    /** Both keys are read as {@code null} when absent: null target = detach, null expected = "was ungrouped". */
    public record PlaceRequest(String orgNodeId, String expectedOrgNodeId) {}

    public record PlacementResponse(String tenantId, String orgNodeId) {}

    public record PlacementEffectResponse(
            String tenantId,
            String fromOrgNodeId,
            String toOrgNodeId,
            List<String> domainsBefore,
            List<String> domainsAfter,
            List<String> lostDomains,
            List<String> gainedDomains
    ) {
        static PlacementEffectResponse from(TenantPlacementResult r) {
            return new PlacementEffectResponse(r.tenantId(), r.fromOrgNodeId(), r.toOrgNodeId(),
                    r.domainsBefore(), r.domainsAfter(), r.lostDomains(), r.gainedDomains());
        }
    }

    public record PlacementWriteResponse(
            String tenantId,
            String fromOrgNodeId,
            String toOrgNodeId,
            List<String> domainsBefore,
            List<String> domainsAfter,
            List<String> lostDomains,
            List<String> gainedDomains,
            boolean changed
    ) {
        static PlacementWriteResponse from(TenantPlacementResult r) {
            return new PlacementWriteResponse(r.tenantId(), r.fromOrgNodeId(), r.toOrgNodeId(),
                    r.domainsBefore(), r.domainsAfter(), r.lostDomains(), r.gainedDomains(), r.changed());
        }
    }
}
