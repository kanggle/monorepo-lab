package com.example.admin.presentation;

import com.example.admin.application.OperatorContext;
import com.example.admin.application.OrgNodeAdminUseCase;
import com.example.admin.application.orgnode.CeilingView;
import com.example.admin.application.orgnode.OrgNodeView;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.security.OperatorContextHolder;
import com.example.admin.presentation.aspect.RequiresPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * TASK-BE-492 (ADR-MONO-047) — the operator-facing org-node surface, a <b>thin command
 * gateway</b> (the shape {@code TenantAdminController} already uses). It authorizes, audits,
 * and forwards; account-service owns the tree.
 *
 * <p>Every endpoint is gated by {@code @RequiresPermission("org.manage")} (deny-default, with
 * a DENIED {@code admin_actions} row on refusal). The reach predicates
 * ({@code administers} / {@code strictlyAdministers}) are applied in the use-case, over the
 * authority's flat node snapshot; a target outside the actor's reach yields
 * {@code 404 ORG_NODE_NOT_FOUND}, never a 403 that would confirm its existence.
 *
 * <p>Reads write no audit row (the {@code grantable-roles} / BE-486 read-path convention);
 * mutations write one on success and a best-effort DENIED row on refusal.
 */
@RestController
@RequestMapping("/api/admin/org-nodes")
@RequiredArgsConstructor
@Validated
public class OrgNodeAdminController {

    private final OrgNodeAdminUseCase orgNodeAdminUseCase;

    @GetMapping
    @RequiresPermission(Permission.ORG_MANAGE)
    public ResponseEntity<OrgNodeListResponse> listNodes() {
        OperatorContext operator = OperatorContextHolder.require();
        List<OrgNodeView> nodes = orgNodeAdminUseCase.listNodes(operator);
        return ResponseEntity.ok(new OrgNodeListResponse(
                nodes.stream().map(OrgNodeResponse::from).toList()));
    }

    @GetMapping("/{orgNodeId}")
    @RequiresPermission(Permission.ORG_MANAGE)
    public ResponseEntity<OrgNodeResponse> getNode(@PathVariable String orgNodeId) {
        OperatorContext operator = OperatorContextHolder.require();
        return ResponseEntity.ok(OrgNodeResponse.from(orgNodeAdminUseCase.getNode(operator, orgNodeId)));
    }

    @PostMapping
    @RequiresPermission(Permission.ORG_MANAGE)
    public ResponseEntity<OrgNodeResponse> createNode(
            @RequestHeader("X-Operator-Reason") String reason,
            @Valid @RequestBody CreateOrgNodeRequest request) {
        OperatorContext operator = OperatorContextHolder.require();
        OrgNodeView created = orgNodeAdminUseCase.createNode(
                operator, request.name(), request.parentId(),
                request.ceiling().toView(), decodeReason(reason));
        return ResponseEntity.status(HttpStatus.CREATED).body(OrgNodeResponse.from(created));
    }

    @PatchMapping("/{orgNodeId}")
    @RequiresPermission(Permission.ORG_MANAGE)
    public ResponseEntity<OrgNodeResponse> updateNode(
            @PathVariable String orgNodeId,
            @RequestHeader("X-Operator-Reason") String reason,
            @Valid @RequestBody UpdateOrgNodeRequest request) {
        if (request.name() == null && request.parentId() == null) {
            throw new IllegalArgumentException("At least one of 'name' or 'parentId' must be supplied");
        }
        OperatorContext operator = OperatorContextHolder.require();
        OrgNodeView updated = orgNodeAdminUseCase.updateNode(
                operator, orgNodeId, request.name(), request.parentId(), decodeReason(reason));
        return ResponseEntity.ok(OrgNodeResponse.from(updated));
    }

    @DeleteMapping("/{orgNodeId}")
    @RequiresPermission(Permission.ORG_MANAGE)
    public ResponseEntity<Void> deleteNode(
            @PathVariable String orgNodeId,
            @RequestHeader("X-Operator-Reason") String reason) {
        OperatorContext operator = OperatorContextHolder.require();
        orgNodeAdminUseCase.deleteNode(operator, orgNodeId, decodeReason(reason));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{orgNodeId}/ceiling")
    @RequiresPermission(Permission.ORG_MANAGE)
    public ResponseEntity<OrgNodeResponse> setCeiling(
            @PathVariable String orgNodeId,
            @RequestHeader("X-Operator-Reason") String reason,
            @Valid @RequestBody CeilingPayload request) {
        OperatorContext operator = OperatorContextHolder.require();
        OrgNodeView updated = orgNodeAdminUseCase.setCeiling(
                operator, orgNodeId, request.toView(), decodeReason(reason));
        return ResponseEntity.ok(OrgNodeResponse.from(updated));
    }

    @GetMapping("/{orgNodeId}/tenants")
    @RequiresPermission(Permission.ORG_MANAGE)
    public ResponseEntity<SubtreeTenantsResponse> listSubtreeTenants(@PathVariable String orgNodeId) {
        OperatorContext operator = OperatorContextHolder.require();
        return ResponseEntity.ok(new SubtreeTenantsResponse(
                orgNodeAdminUseCase.listSubtreeTenants(operator, orgNodeId)));
    }

    @GetMapping("/{orgNodeId}/admins")
    @RequiresPermission(Permission.ORG_MANAGE)
    public ResponseEntity<OrgAdminListResponse> listNodeAdmins(@PathVariable String orgNodeId) {
        OperatorContext operator = OperatorContextHolder.require();
        return ResponseEntity.ok(new OrgAdminListResponse(
                orgNodeAdminUseCase.listNodeAdmins(operator, orgNodeId).stream()
                        .map(OrgAdminResponse::from).toList()));
    }

    @PostMapping("/{orgNodeId}/admins")
    @RequiresPermission(Permission.ORG_MANAGE)
    public ResponseEntity<OrgAdminGrantResponse> grantNodeAdmin(
            @PathVariable String orgNodeId,
            @RequestHeader("X-Operator-Reason") String reason,
            @Valid @RequestBody GrantOrgAdminRequest request) {
        OperatorContext operator = OperatorContextHolder.require();
        OrgNodeAdminUseCase.OrgAdminGrant grant = orgNodeAdminUseCase.grantNodeAdmin(
                operator, orgNodeId, request.operatorId(), request.roleName(), decodeReason(reason));
        return ResponseEntity.status(HttpStatus.CREATED).body(new OrgAdminGrantResponse(
                orgNodeId, grant.operatorId(), grant.roleName(), grant.grantedAt()));
    }

    @DeleteMapping("/{orgNodeId}/admins/{operatorId}")
    @RequiresPermission(Permission.ORG_MANAGE)
    public ResponseEntity<Void> revokeNodeAdmin(
            @PathVariable String orgNodeId,
            @PathVariable String operatorId,
            @RequestHeader("X-Operator-Reason") String reason) {
        OperatorContext operator = OperatorContextHolder.require();
        orgNodeAdminUseCase.revokeNodeAdmin(operator, orgNodeId, operatorId, decodeReason(reason));
        return ResponseEntity.noContent().build();
    }

    // ---- Helpers --------------------------------------------------------------

    private static String decodeReason(String value) {
        if (value == null) return null;
        try {
            return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    // ---- DTOs -----------------------------------------------------------------

    /**
     * The ceiling wire shape. {@code mode} is mandatory precisely so {@code UNBOUNDED} (no
     * ceiling — the intersection identity) can never be confused with {@code BOUNDED([])}
     * (permits nothing). They are opposites.
     */
    public record CeilingPayload(@NotBlank String mode, List<String> domains) {
        CeilingView toView() {
            if (CeilingView.MODE_UNBOUNDED.equals(mode)) {
                return CeilingView.unbounded();
            }
            if (CeilingView.MODE_BOUNDED.equals(mode)) {
                if (domains == null) {
                    throw new IllegalArgumentException("ceiling.domains is required when mode=BOUNDED");
                }
                return CeilingView.bounded(domains);
            }
            throw new IllegalArgumentException("ceiling.mode must be UNBOUNDED or BOUNDED: " + mode);
        }

        static CeilingPayload from(CeilingView view) {
            return view.isUnbounded()
                    ? new CeilingPayload(CeilingView.MODE_UNBOUNDED, null)
                    : new CeilingPayload(CeilingView.MODE_BOUNDED, view.domains());
        }
    }

    public record CreateOrgNodeRequest(
            @NotBlank @Size(min = 1, max = 100) String name,
            String parentId,
            @Valid @NotNull CeilingPayload ceiling
    ) {}

    public record UpdateOrgNodeRequest(
            @Size(min = 1, max = 100) String name,
            String parentId
    ) {}

    public record GrantOrgAdminRequest(
            @NotBlank String operatorId,
            @NotBlank String roleName
    ) {}

    public record OrgNodeResponse(
            String orgNodeId,
            String parentId,
            String name,
            int depth,
            CeilingPayload ceiling,
            Instant createdAt,
            Instant updatedAt
    ) {
        static OrgNodeResponse from(OrgNodeView v) {
            return new OrgNodeResponse(v.orgNodeId(), v.parentId(), v.name(), v.depth(),
                    CeilingPayload.from(v.ceiling()), v.createdAt(), v.updatedAt());
        }
    }

    /** Flat array — the client assembles the tree from {@code parentId}. */
    public record OrgNodeListResponse(List<OrgNodeResponse> items) {}

    public record SubtreeTenantsResponse(List<String> tenantIds) {}

    public record OrgAdminResponse(String operatorId, String roleName, Instant grantedAt) {
        static OrgAdminResponse from(OrgNodeAdminUseCase.OrgAdminGrant g) {
            return new OrgAdminResponse(g.operatorId(), g.roleName(), g.grantedAt());
        }
    }

    public record OrgAdminListResponse(List<OrgAdminResponse> items) {}

    public record OrgAdminGrantResponse(String orgNodeId, String operatorId,
                                        String roleName, Instant grantedAt) {}
}
