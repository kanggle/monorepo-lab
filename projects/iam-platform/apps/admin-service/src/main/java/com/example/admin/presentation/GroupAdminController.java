package com.example.admin.presentation;

import com.example.admin.application.GroupAdminUseCase;
import com.example.admin.application.OperatorContext;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.security.OperatorContextHolder;
import com.example.admin.presentation.aspect.RequiresPermission;
import com.example.admin.presentation.dto.AddGroupGrantsRequest;
import com.example.admin.presentation.dto.AddGroupGrantsResponse;
import com.example.admin.presentation.dto.AddGroupMemberRequest;
import com.example.admin.presentation.dto.AddGroupMemberResponse;
import com.example.admin.presentation.dto.CreateGroupRequest;
import com.example.admin.presentation.dto.GroupGrantListResponse;
import com.example.admin.presentation.dto.GroupListResponse;
import com.example.admin.presentation.dto.GroupMemberListResponse;
import com.example.admin.presentation.dto.GroupMemberResponse;
import com.example.admin.presentation.dto.GroupResponse;
import com.example.admin.presentation.dto.UpdateGroupRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * TASK-BE-520 (ADR-MONO-046) — the operator-group surface, a <b>thin command gateway</b>
 * (the shape {@code OrgNodeAdminController} already uses). Every endpoint (read included) is
 * gated by {@code @RequiresPermission("group.manage")} (deny-default, DENIED
 * {@code admin_actions} row on refusal — D6). Mutations additionally take the
 * {@code X-Operator-Reason} header and are confined by the D3 {@code TenantScopeGuard} +
 * D4 {@code RoleGrantGuard} inside {@link GroupAdminUseCase} — this class only authorizes,
 * decodes, and forwards.
 *
 * <p>v1 is fan-out (D2-A): a group grant materialises ordinary per-operator rows tagged
 * {@code group_origin}; group membership is not an evaluation-time edge and the
 * evaluator/cache/confinement axes are byte-unchanged (rbac.md § Operator Group Fan-Out).
 */
@RestController
@RequestMapping("/api/admin/groups")
@RequiredArgsConstructor
@Validated
public class GroupAdminController {

    private final GroupAdminUseCase groupAdminUseCase;

    // ==== Group CRUD ===========================================================

    @PostMapping
    @RequiresPermission(Permission.GROUP_MANAGE)
    public ResponseEntity<GroupResponse> createGroup(
            @RequestHeader("X-Operator-Reason") String reason,
            @Valid @RequestBody CreateGroupRequest request) {
        OperatorContext operator = OperatorContextHolder.require();
        GroupAdminUseCase.GroupView created = groupAdminUseCase.createGroup(
                operator, request.tenantId(), request.name(), request.description(), decodeReason(reason));
        return ResponseEntity.status(HttpStatus.CREATED).body(GroupResponse.from(created));
    }

    @GetMapping
    @RequiresPermission(Permission.GROUP_MANAGE)
    public ResponseEntity<GroupListResponse> listGroups(
            @RequestParam(value = "tenantId", required = false) String tenantId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        OperatorContext operator = OperatorContextHolder.require();
        int cappedSize = Math.min(Math.max(size, 1), 100);
        return ResponseEntity.ok(GroupListResponse.from(
                groupAdminUseCase.listGroups(operator, tenantId, Math.max(page, 0), cappedSize)));
    }

    @GetMapping("/{groupId}")
    @RequiresPermission(Permission.GROUP_MANAGE)
    public ResponseEntity<GroupResponse> getGroup(@PathVariable String groupId) {
        OperatorContext operator = OperatorContextHolder.require();
        return ResponseEntity.ok(GroupResponse.from(groupAdminUseCase.getGroup(operator, groupId)));
    }

    @PatchMapping("/{groupId}")
    @RequiresPermission(Permission.GROUP_MANAGE)
    public ResponseEntity<GroupResponse> updateGroup(
            @PathVariable String groupId,
            @RequestHeader("X-Operator-Reason") String reason,
            @Valid @RequestBody UpdateGroupRequest request) {
        if (request.name() == null && request.description() == null) {
            throw new IllegalArgumentException("At least one of 'name' or 'description' must be supplied");
        }
        OperatorContext operator = OperatorContextHolder.require();
        GroupAdminUseCase.GroupView updated = groupAdminUseCase.updateGroup(
                operator, groupId, request.name(), request.description(), decodeReason(reason));
        return ResponseEntity.ok(GroupResponse.from(updated));
    }

    @DeleteMapping("/{groupId}")
    @RequiresPermission(Permission.GROUP_MANAGE)
    public ResponseEntity<Void> deleteGroup(
            @PathVariable String groupId,
            @RequestHeader("X-Operator-Reason") String reason) {
        OperatorContext operator = OperatorContextHolder.require();
        groupAdminUseCase.deleteGroup(operator, groupId, decodeReason(reason));
        return ResponseEntity.noContent().build();
    }

    // ==== Members ==============================================================

    @GetMapping("/{groupId}/members")
    @RequiresPermission(Permission.GROUP_MANAGE)
    public ResponseEntity<GroupMemberListResponse> listMembers(@PathVariable String groupId) {
        OperatorContext operator = OperatorContextHolder.require();
        return ResponseEntity.ok(new GroupMemberListResponse(
                groupAdminUseCase.listMembers(operator, groupId).stream()
                        .map(GroupMemberResponse::from).toList()));
    }

    @PostMapping("/{groupId}/members")
    @RequiresPermission(Permission.GROUP_MANAGE)
    public ResponseEntity<AddGroupMemberResponse> addMember(
            @PathVariable String groupId,
            @RequestHeader("X-Operator-Reason") String reason,
            @Valid @RequestBody AddGroupMemberRequest request) {
        OperatorContext operator = OperatorContextHolder.require();
        GroupAdminUseCase.MemberAddResult result = groupAdminUseCase.addMember(
                operator, groupId, request.operatorId(), decodeReason(reason));
        return ResponseEntity.status(HttpStatus.CREATED).body(AddGroupMemberResponse.from(result));
    }

    @DeleteMapping("/{groupId}/members/{operatorId}")
    @RequiresPermission(Permission.GROUP_MANAGE)
    public ResponseEntity<Void> removeMember(
            @PathVariable String groupId,
            @PathVariable String operatorId,
            @RequestHeader("X-Operator-Reason") String reason) {
        OperatorContext operator = OperatorContextHolder.require();
        groupAdminUseCase.removeMember(operator, groupId, operatorId, decodeReason(reason));
        return ResponseEntity.noContent().build();
    }

    // ==== Grants ===============================================================

    @GetMapping("/{groupId}/grants")
    @RequiresPermission(Permission.GROUP_MANAGE)
    public ResponseEntity<GroupGrantListResponse> listGrants(@PathVariable String groupId) {
        OperatorContext operator = OperatorContextHolder.require();
        return ResponseEntity.ok(new GroupGrantListResponse(
                groupAdminUseCase.listGrants(operator, groupId).stream()
                        .map(com.example.admin.presentation.dto.GroupGrantResponse::from).toList()));
    }

    @PostMapping("/{groupId}/grants")
    @RequiresPermission(Permission.GROUP_MANAGE)
    public ResponseEntity<AddGroupGrantsResponse> addGrants(
            @PathVariable String groupId,
            @RequestHeader("X-Operator-Reason") String reason,
            @Valid @RequestBody AddGroupGrantsRequest request) {
        OperatorContext operator = OperatorContextHolder.require();
        GroupAdminUseCase.GrantAddResult result = groupAdminUseCase.addGrants(
                operator, groupId, request.roles(), request.tenantIds(), decodeReason(reason));
        return ResponseEntity.status(HttpStatus.CREATED).body(AddGroupGrantsResponse.from(result));
    }

    @DeleteMapping("/{groupId}/grants/{grantId}")
    @RequiresPermission(Permission.GROUP_MANAGE)
    public ResponseEntity<Void> revokeGrant(
            @PathVariable String groupId,
            @PathVariable String grantId,
            @RequestHeader("X-Operator-Reason") String reason) {
        OperatorContext operator = OperatorContextHolder.require();
        groupAdminUseCase.revokeGrant(operator, groupId, grantId, decodeReason(reason));
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
}
