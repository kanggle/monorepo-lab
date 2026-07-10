package com.example.account.presentation.internal;

import com.example.account.application.service.OrgNodeCommandUseCase;
import com.example.account.application.service.OrgNodeQueryUseCase;
import com.example.account.domain.orgnode.EntitlementCeiling;
import com.example.account.domain.orgnode.OrgNode;
import com.example.account.domain.orgnode.OrgNodeId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * TASK-BE-491 (ADR-MONO-047): the org-node tree's authoritative surface.
 *
 * <p>URL prefix: {@code /internal/org-nodes} — under the {@code /internal/} gate (GAP
 * {@code client_credentials} Bearer JWT, fail-closed).
 *
 * <p>admin-service proxies this as a thin command gateway: it authorizes ({@code org.manage}),
 * audits, and forwards. The tree, the cycle/depth checks, and the ceiling math live here,
 * next to the data. Contract: {@code specs/contracts/http/internal/admin-to-account.md}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/org-nodes")
public class OrgNodeInternalController {

    private final OrgNodeQueryUseCase queryUseCase;
    private final OrgNodeCommandUseCase commandUseCase;

    /** Flat list with {@code parentId}; the client assembles the tree. */
    @GetMapping
    public ResponseEntity<OrgNodeListResponse> list() {
        return ResponseEntity.ok(new OrgNodeListResponse(
                queryUseCase.tree().stream().map(OrgNodeResponse::from).toList()));
    }

    @PostMapping
    public ResponseEntity<OrgNodeResponse> create(@Valid @RequestBody CreateOrgNodeRequest body) {
        OrgNode created = commandUseCase.create(
                body.name(),
                body.parentId() == null ? null : new OrgNodeId(body.parentId()),
                body.ceiling() == null ? null : body.ceiling().toDomain());
        return ResponseEntity.status(HttpStatus.CREATED).body(OrgNodeResponse.from(created));
    }

    @GetMapping("/{orgNodeId}")
    public ResponseEntity<OrgNodeResponse> get(@PathVariable String orgNodeId) {
        return ResponseEntity.ok(OrgNodeResponse.from(queryUseCase.get(new OrgNodeId(orgNodeId))));
    }

    /**
     * Rename and/or re-parent. Both fields are optional; {@code parentId} present-and-null
     * cannot be distinguished from absent in a plain record, so re-parenting to root uses
     * the explicit {@code toRoot} flag rather than a null that also means "leave alone".
     */
    @PatchMapping("/{orgNodeId}")
    public ResponseEntity<OrgNodeResponse> update(@PathVariable String orgNodeId,
                                                  @RequestBody UpdateOrgNodeRequest body) {
        OrgNodeId id = new OrgNodeId(orgNodeId);
        OrgNode result = queryUseCase.get(id);
        if (body.name() != null && !body.name().isBlank()) {
            result = commandUseCase.rename(id, body.name());
        }
        if (Boolean.TRUE.equals(body.toRoot())) {
            result = commandUseCase.reparent(id, null);
        } else if (body.parentId() != null && !body.parentId().isBlank()) {
            result = commandUseCase.reparent(id, new OrgNodeId(body.parentId()));
        }
        return ResponseEntity.ok(OrgNodeResponse.from(result));
    }

    @DeleteMapping("/{orgNodeId}")
    public ResponseEntity<Void> delete(@PathVariable String orgNodeId) {
        commandUseCase.delete(new OrgNodeId(orgNodeId));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{orgNodeId}/ceiling")
    public ResponseEntity<OrgNodeResponse> setCeiling(@PathVariable String orgNodeId,
                                                      @Valid @RequestBody CeilingPayload body) {
        return ResponseEntity.ok(OrgNodeResponse.from(
                commandUseCase.setCeiling(new OrgNodeId(orgNodeId), body.toDomain())));
    }

    /** Self + descendants. Backs admin-service's {@code ORG_ADMIN @ node} scope expansion. */
    @GetMapping("/{orgNodeId}/tenants")
    public ResponseEntity<SubtreeTenantsResponse> subtreeTenants(@PathVariable String orgNodeId) {
        return ResponseEntity.ok(new SubtreeTenantsResponse(
                queryUseCase.subtreeTenantIds(new OrgNodeId(orgNodeId))));
    }

    @GetMapping("/{orgNodeId}/effective-ceiling")
    public ResponseEntity<CeilingPayload> effectiveCeiling(@PathVariable String orgNodeId) {
        return ResponseEntity.ok(CeilingPayload.from(
                queryUseCase.effectiveCeiling(new OrgNodeId(orgNodeId))));
    }

    // ---- DTOs ----------------------------------------------------------------

    /**
     * {@code {"mode":"UNBOUNDED"}} or {@code {"mode":"BOUNDED","domains":[...]}}.
     *
     * <p>A {@code BOUNDED} payload with an empty/absent {@code domains} is the EMPTY set —
     * nothing permitted. That is NOT the same as {@code UNBOUNDED} (no ceiling), and the
     * {@code mode} discriminator exists so the two can never collapse on the wire.
     */
    public record CeilingPayload(@NotBlank String mode, List<String> domains) {

        EntitlementCeiling toDomain() {
            EntitlementCeiling.Mode parsed = EntitlementCeiling.Mode.valueOf(mode);
            if (parsed == EntitlementCeiling.Mode.UNBOUNDED) {
                return EntitlementCeiling.unbounded();
            }
            return EntitlementCeiling.bounded(domains == null ? List.of() : domains);
        }

        static CeilingPayload from(EntitlementCeiling ceiling) {
            return ceiling.isUnbounded()
                    ? new CeilingPayload(ceiling.mode().name(), null)
                    : new CeilingPayload(ceiling.mode().name(), List.copyOf(ceiling.domains()));
        }
    }

    public record OrgNodeResponse(
            String orgNodeId,
            String parentId,
            String name,
            int depth,
            CeilingPayload ceiling,
            Instant createdAt,
            Instant updatedAt
    ) {
        static OrgNodeResponse from(OrgNode node) {
            return new OrgNodeResponse(
                    node.getId().value(),
                    node.getParentId() == null ? null : node.getParentId().value(),
                    node.getName(),
                    node.getDepth(),
                    CeilingPayload.from(node.getCeiling()),
                    node.getCreatedAt(),
                    node.getUpdatedAt());
        }
    }

    public record OrgNodeListResponse(List<OrgNodeResponse> items) {}

    public record SubtreeTenantsResponse(List<String> tenantIds) {}

    public record CreateOrgNodeRequest(
            @NotBlank String name,
            String parentId,
            CeilingPayload ceiling
    ) {}

    /** {@code toRoot=true} promotes the node to a root; otherwise a blank parentId means "unchanged". */
    public record UpdateOrgNodeRequest(
            String name,
            String parentId,
            Boolean toRoot
    ) {}
}
