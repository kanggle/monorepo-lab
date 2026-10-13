package com.example.admin.presentation;

import com.example.admin.application.CreateDomainPermissionSetCommand;
import com.example.admin.application.DomainPermissionSetUseCase;
import com.example.admin.application.UpdateDomainPermissionSetCommand;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.security.OperatorContextHolder;
import com.example.admin.presentation.aspect.RequiresPermission;
import com.example.admin.presentation.dto.DomainPermissionSetDtos.CreateDomainPermissionSetRequest;
import com.example.admin.presentation.dto.DomainPermissionSetDtos.DomainPermissionSetResponse;
import com.example.admin.presentation.dto.DomainPermissionSetDtos.DomainRoleResponse;
import com.example.admin.presentation.dto.DomainPermissionSetDtos.ItemsResponse;
import com.example.admin.presentation.dto.DomainPermissionSetDtos.RoleRefDto;
import com.example.admin.presentation.dto.DomainPermissionSetDtos.UpdateDomainPermissionSetRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * TASK-BE-631 (ADR-MONO-083) — the work-role catalog and the domain permission set CRUD (admin-api.md § 도메인 업무
 * 권한 세트: {@code GET /domain-roles} · {@code GET|POST /domain-permission-sets} ·
 * {@code GET|PATCH|DELETE /domain-permission-sets/{setId}}). Thin: every endpoint is gated by
 * {@code @RequiresPermission("domain_access.manage")} (DENIED row on refusal); the acting-tenant gate, the catalog /
 * entitlement / read-only / in-use rules and the audit row live in {@link DomainPermissionSetUseCase}.
 *
 * <p>Headers: {@code X-Tenant-Id} (the acting tenant — omitted-tenant rule) on every set endpoint;
 * {@code X-Operator-Reason} on the writes; {@code Idempotency-Key} on create only.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class DomainPermissionSetController {

    private final DomainPermissionSetUseCase useCase;

    @GetMapping("/domain-roles")
    @RequiresPermission(Permission.DOMAIN_ACCESS_MANAGE)
    public ResponseEntity<ItemsResponse<DomainRoleResponse>> listDomainRoles() {
        return ResponseEntity.ok(new ItemsResponse<>(
                useCase.catalog().stream().map(DomainRoleResponse::from).toList()));
    }

    @GetMapping("/domain-permission-sets")
    @RequiresPermission(Permission.DOMAIN_ACCESS_MANAGE)
    public ResponseEntity<ItemsResponse<DomainPermissionSetResponse>> listSets(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        return ResponseEntity.ok(new ItemsResponse<>(useCase.list(OperatorContextHolder.require(), tenantId)
                .stream().map(DomainPermissionSetResponse::from).toList()));
    }

    @GetMapping("/domain-permission-sets/{setId}")
    @RequiresPermission(Permission.DOMAIN_ACCESS_MANAGE)
    public ResponseEntity<DomainPermissionSetResponse> getSet(
            @PathVariable String setId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        return ResponseEntity.ok(DomainPermissionSetResponse.from(
                useCase.get(OperatorContextHolder.require(), tenantId, setId)));
    }

    @PostMapping("/domain-permission-sets")
    @RequiresPermission(Permission.DOMAIN_ACCESS_MANAGE)
    public ResponseEntity<DomainPermissionSetResponse> createSet(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-Operator-Reason", required = false) String reason,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) CreateDomainPermissionSetRequest body) {
        String decoded = decodeReason(ControllerReasonSupport.requireReason(reason));
        if (body == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(DomainPermissionSetResponse.from(useCase.create(
                new CreateDomainPermissionSetCommand(OperatorContextHolder.require(), tenantId, body.name(),
                        body.description(), RoleRefDto.toRefs(body.roles()), decoded, idempotencyKey))));
    }

    @PatchMapping("/domain-permission-sets/{setId}")
    @RequiresPermission(Permission.DOMAIN_ACCESS_MANAGE)
    public ResponseEntity<DomainPermissionSetResponse> updateSet(
            @PathVariable String setId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-Operator-Reason", required = false) String reason,
            @RequestBody(required = false) UpdateDomainPermissionSetRequest body) {
        String decoded = decodeReason(ControllerReasonSupport.requireReason(reason));
        UpdateDomainPermissionSetRequest b = body == null ? new UpdateDomainPermissionSetRequest(null, null, null) : body;
        return ResponseEntity.ok(DomainPermissionSetResponse.from(useCase.update(
                new UpdateDomainPermissionSetCommand(OperatorContextHolder.require(), tenantId, setId, b.name(),
                        b.description(), RoleRefDto.toRefs(b.roles()), decoded))));
    }

    @DeleteMapping("/domain-permission-sets/{setId}")
    @RequiresPermission(Permission.DOMAIN_ACCESS_MANAGE)
    public ResponseEntity<Void> deleteSet(
            @PathVariable String setId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-Operator-Reason", required = false) String reason) {
        String decoded = decodeReason(ControllerReasonSupport.requireReason(reason));
        useCase.delete(OperatorContextHolder.require(), tenantId, setId, decoded);
        return ResponseEntity.noContent().build();
    }

    /** The console percent-encodes the reason header (TASK-MONO-176); a raw value passes through. */
    static String decodeReason(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }
}
