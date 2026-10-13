package com.example.admin.presentation;

import com.example.admin.application.GrantDomainPermissionSetCommand;
import com.example.admin.application.OperatorDomainAccessUseCase;
import com.example.admin.application.RevokeDomainPermissionSetCommand;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.security.OperatorContextHolder;
import com.example.admin.presentation.aspect.RequiresPermission;
import com.example.admin.presentation.dto.DomainPermissionSetDtos.DomainAccessResponse;
import com.example.admin.presentation.dto.DomainPermissionSetDtos.GrantDomainPermissionSetRequest;
import com.example.admin.presentation.dto.DomainPermissionSetDtos.GrantDomainPermissionSetResponse;
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
 * TASK-BE-631 (ADR-MONO-083 D4 · D5 · D9) — an operator's domain permission sets per tenant (admin-api.md § 도메인
 * 업무 권한 세트): the effective-access read, the DIRECT grant and the personal revoke
 * ({@code domain_access.manage}, § 행동 테넌트 row 26), and the caller's own value ({@code GET /me/domain-access},
 * no permission key — row 28). Thin: rules and audit live in {@link OperatorDomainAccessUseCase}.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class OperatorDomainAccessController {

    private final OperatorDomainAccessUseCase useCase;

    @GetMapping("/operators/{operatorId}/domain-access")
    @RequiresPermission(Permission.DOMAIN_ACCESS_MANAGE)
    public ResponseEntity<DomainAccessResponse> getDomainAccess(
            @PathVariable String operatorId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        return ResponseEntity.ok(DomainAccessResponse.from(
                useCase.domainAccess(OperatorContextHolder.require(), tenantId, operatorId)));
    }

    @PostMapping("/operators/{operatorId}/domain-permission-sets")
    @RequiresPermission(Permission.DOMAIN_ACCESS_MANAGE)
    public ResponseEntity<GrantDomainPermissionSetResponse> grantSet(
            @PathVariable String operatorId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-Operator-Reason", required = false) String reason,
            @RequestBody(required = false) GrantDomainPermissionSetRequest body) {
        String decoded = DomainPermissionSetController.decodeReason(ControllerReasonSupport.requireReason(reason));
        String setId = body == null ? null : body.setId();
        return ResponseEntity.status(HttpStatus.CREATED).body(GrantDomainPermissionSetResponse.from(useCase.grant(
                new GrantDomainPermissionSetCommand(OperatorContextHolder.require(), tenantId, operatorId, setId,
                        decoded))));
    }

    @DeleteMapping("/operators/{operatorId}/domain-permission-sets/{setId}")
    @RequiresPermission(Permission.DOMAIN_ACCESS_MANAGE)
    public ResponseEntity<Void> revokeSet(
            @PathVariable String operatorId,
            @PathVariable String setId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-Operator-Reason", required = false) String reason) {
        String decoded = DomainPermissionSetController.decodeReason(ControllerReasonSupport.requireReason(reason));
        useCase.revoke(new RevokeDomainPermissionSetCommand(OperatorContextHolder.require(), tenantId, operatorId,
                setId, decoded));
        return ResponseEntity.noContent().build();
    }

    /** No permission key — the caller's own value (like {@code GET /me}); {@code X-Tenant-Id} is required. */
    @GetMapping("/me/domain-access")
    public ResponseEntity<DomainAccessResponse> getMyDomainAccess(
            @RequestHeader(value = "X-Tenant-Id") String tenantId) {
        return ResponseEntity.ok(DomainAccessResponse.from(
                useCase.myDomainAccess(OperatorContextHolder.require(), tenantId)));
    }
}
