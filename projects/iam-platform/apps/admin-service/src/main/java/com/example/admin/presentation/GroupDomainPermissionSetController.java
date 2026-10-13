package com.example.admin.presentation;

import com.example.admin.application.GrantGroupDomainPermissionSetCommand;
import com.example.admin.application.GroupDomainPermissionSetUseCase;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.security.OperatorContextHolder;
import com.example.admin.presentation.aspect.RequiresPermission;
import com.example.admin.presentation.dto.DomainPermissionSetDtos.GrantGroupDomainPermissionSetRequest;
import com.example.admin.presentation.dto.DomainPermissionSetDtos.GrantGroupDomainPermissionSetResponse;
import com.example.admin.presentation.dto.DomainPermissionSetDtos.GroupDomainPermissionSetResponse;
import com.example.admin.presentation.dto.DomainPermissionSetDtos.ItemsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TASK-BE-631 slice B (ADR-MONO-083 D4) — the domain permission sets of a group (admin-api.md § 도메인 업무 권한 세트:
 * {@code GET|POST /api/admin/groups/{groupId}/domain-permission-sets} · {@code DELETE …/{setId}}). Thin: every
 * endpoint is gated by {@code @RequiresPermission("domain_access.manage")} (DENIED row on refusal); the group-tenant
 * scope, the set / entitlement / duplicate rules, the fan-out and the audit row live in
 * {@link GroupDomainPermissionSetUseCase}. No {@code X-Tenant-Id}: the acting tenant is the group's (§ 행동 테넌트 27).
 */
@RestController
@RequestMapping("/api/admin/groups")
@RequiredArgsConstructor
public class GroupDomainPermissionSetController {

    private final GroupDomainPermissionSetUseCase useCase;

    @GetMapping("/{groupId}/domain-permission-sets")
    @RequiresPermission(Permission.DOMAIN_ACCESS_MANAGE)
    public ResponseEntity<ItemsResponse<GroupDomainPermissionSetResponse>> listGroupSets(
            @PathVariable String groupId) {
        return ResponseEntity.ok(new ItemsResponse<>(useCase.list(OperatorContextHolder.require(), groupId)
                .stream().map(GroupDomainPermissionSetResponse::from).toList()));
    }

    @PostMapping("/{groupId}/domain-permission-sets")
    @RequiresPermission(Permission.DOMAIN_ACCESS_MANAGE)
    public ResponseEntity<GrantGroupDomainPermissionSetResponse> grantGroupSet(
            @PathVariable String groupId,
            @RequestHeader(value = "X-Operator-Reason", required = false) String reason,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) GrantGroupDomainPermissionSetRequest body) {
        String decoded = DomainPermissionSetController.decodeReason(ControllerReasonSupport.requireReason(reason));
        String setId = body == null ? null : body.setId();
        return ResponseEntity.status(HttpStatus.CREATED).body(GrantGroupDomainPermissionSetResponse.from(
                useCase.grant(new GrantGroupDomainPermissionSetCommand(OperatorContextHolder.require(), groupId,
                        setId, decoded, idempotencyKey))));
    }

    @DeleteMapping("/{groupId}/domain-permission-sets/{setId}")
    @RequiresPermission(Permission.DOMAIN_ACCESS_MANAGE)
    public ResponseEntity<Void> revokeGroupSet(
            @PathVariable String groupId,
            @PathVariable String setId,
            @RequestHeader(value = "X-Operator-Reason", required = false) String reason) {
        String decoded = DomainPermissionSetController.decodeReason(ControllerReasonSupport.requireReason(reason));
        useCase.revoke(OperatorContextHolder.require(), groupId, setId, decoded);
        return ResponseEntity.noContent().build();
    }
}
