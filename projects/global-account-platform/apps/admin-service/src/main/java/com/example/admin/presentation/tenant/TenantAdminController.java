package com.example.admin.presentation.tenant;

import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.tenant.CreateTenantUseCase;
import com.example.admin.application.tenant.GetTenantUseCase;
import com.example.admin.application.tenant.ListTenantsUseCase;
import com.example.admin.application.tenant.TenantPageSummary;
import com.example.admin.application.tenant.TenantSummary;
import com.example.admin.application.tenant.UpdateTenantUseCase;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.domain.rbac.PermissionEvaluator;
import com.example.admin.infrastructure.security.OperatorContextHolder;
import com.example.admin.application.OperatorContext;
import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import com.example.admin.presentation.aspect.RequiresPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * TASK-BE-250: Tenant lifecycle CRUD endpoints for admin operators.
 *
 * <ul>
 *   <li>POST   /api/admin/tenants           — SUPER_ADMIN only (platform scope) → 201</li>
 *   <li>PATCH  /api/admin/tenants/{id}      — SUPER_ADMIN only → 200</li>
 *   <li>GET    /api/admin/tenants           — SUPER_ADMIN only → 200 paginated</li>
 *   <li>GET    /api/admin/tenants/{id}      — SUPER_ADMIN OR own-tenant scoped operator → 200</li>
 * </ul>
 *
 * <p>auth: {@code OperatorAuthenticationFilter} validates the operator JWT before this
 * controller is reached. Tenant-scope enforcement is an explicit check here (not via AOP)
 * because the permission semantics differ per endpoint.
 */
@RestController
@RequestMapping("/api/admin/tenants")
@RequiredArgsConstructor
@Validated
public class TenantAdminController {

    private final CreateTenantUseCase createTenantUseCase;
    private final UpdateTenantUseCase updateTenantUseCase;
    private final GetTenantUseCase getTenantUseCase;
    private final ListTenantsUseCase listTenantsUseCase;
    private final PermissionEvaluator permissionEvaluator;
    private final AdminOperatorJpaRepository operatorRepository;

    /**
     * POST /api/admin/tenants
     * SUPER_ADMIN only. Creates a new tenant.
     * {@code @RequiresPermission} satisfies the deny-by-default AOP guardrail in
     * {@code RequiresPermissionAspect}; platform-scope is enforced additionally below.
     */
    @PostMapping
    @RequiresPermission(Permission.TENANT_MANAGE)
    public ResponseEntity<TenantResponse> createTenant(
            @RequestHeader("X-Operator-Reason") String reason,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateTenantRequest request) {

        OperatorContext operator = OperatorContextHolder.require();
        requirePlatformScope(operator);

        TenantSummary result = createTenantUseCase.execute(
                request.tenantId(), request.displayName(), request.tenantType(),
                operator, decodeReason(reason), resolveIdempotencyKey(idempotencyKey));

        return ResponseEntity.status(HttpStatus.CREATED).body(TenantResponse.from(result));
    }

    /**
     * PATCH /api/admin/tenants/{tenantId}
     * SUPER_ADMIN only. Updates displayName and/or status.
     */
    @PatchMapping("/{tenantId}")
    @RequiresPermission(Permission.TENANT_MANAGE)
    public ResponseEntity<TenantResponse> updateTenant(
            @PathVariable String tenantId,
            @RequestHeader("X-Operator-Reason") String reason,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody UpdateTenantRequest request) {

        OperatorContext operator = OperatorContextHolder.require();
        requirePlatformScope(operator);

        TenantSummary result = updateTenantUseCase.execute(
                tenantId, request.displayName(), request.status(),
                operator, decodeReason(reason), resolveIdempotencyKey(idempotencyKey));

        return ResponseEntity.ok(TenantResponse.from(result));
    }

    /**
     * GET /api/admin/tenants
     * SUPER_ADMIN only. Paginated tenant list.
     */
    @GetMapping
    public ResponseEntity<TenantPageResponse> listTenants(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tenantType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        OperatorContext operator = OperatorContextHolder.require();
        requirePlatformScope(operator);

        TenantPageSummary result = listTenantsUseCase.execute(status, tenantType, page, size);
        return ResponseEntity.ok(TenantPageResponse.from(result));
    }

    /**
     * GET /api/admin/tenants/{tenantId}
     * SUPER_ADMIN OR scoped operator whose tenantId matches the path.
     */
    @GetMapping("/{tenantId}")
    public ResponseEntity<TenantResponse> getTenant(@PathVariable String tenantId) {
        OperatorContext operator = OperatorContextHolder.require();
        AdminOperator adminOperator = resolveAdminOperator(operator);

        if (!permissionEvaluator.isTenantAllowed(adminOperator, tenantId)) {
            throw new TenantScopeDeniedException(
                    "Operator is not allowed to access tenant: " + tenantId);
        }

        TenantSummary result = getTenantUseCase.execute(tenantId);
        return ResponseEntity.ok(TenantResponse.from(result));
    }

    // ---- Private helpers ---------------------------------------------------

    private void requirePlatformScope(OperatorContext operator) {
        AdminOperator adminOperator = resolveAdminOperator(operator);
        if (!adminOperator.isPlatformScope()) {
            throw new TenantScopeDeniedException(
                    "Only SUPER_ADMIN (platform-scope) operators may manage tenants");
        }
    }

    private AdminOperator resolveAdminOperator(OperatorContext operator) {
        return operatorRepository.findByOperatorId(operator.operatorId())
                .map(e -> new AdminOperator(
                        e.getOperatorId(),
                        e.getEmail(),
                        e.getDisplayName(),
                        AdminOperator.Status.valueOf(e.getStatus()),
                        e.getVersion(),
                        e.getTenantId()))
                .orElseThrow(() -> new com.example.admin.application.exception.OperatorUnauthorizedException(
                        "Operator not found: " + operator.operatorId()));
    }

    private static String decodeReason(String value) {
        if (value == null) return null;
        try {
            return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    private static String resolveIdempotencyKey(String header) {
        return header != null && !header.isBlank() ? header : UUID.randomUUID().toString();
    }

    // ---- DTOs ---------------------------------------------------------------

    public record CreateTenantRequest(
            @NotBlank String tenantId,
            @NotBlank @Size(min = 1, max = 100) String displayName,
            @NotBlank String tenantType
    ) {}

    public record UpdateTenantRequest(
            @Size(min = 1, max = 100) String displayName,
            String status
    ) {}

    public record TenantResponse(
            String tenantId,
            String displayName,
            String tenantType,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static TenantResponse from(TenantSummary s) {
            return new TenantResponse(
                    s.tenantId(), s.displayName(), s.tenantType(),
                    s.status(), s.createdAt(), s.updatedAt());
        }
    }

    public record TenantPageResponse(
            List<TenantResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static TenantPageResponse from(TenantPageSummary s) {
            return new TenantPageResponse(
                    s.items().stream().map(TenantResponse::from).toList(),
                    s.page(), s.size(), s.totalElements(), s.totalPages());
        }
    }
}
