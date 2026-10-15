package com.example.admin.presentation.tenant;

import com.example.admin.presentation.ClientIdempotencyKey;
import com.example.admin.application.OperatorInvitationResult;
import com.example.admin.application.OperatorInvitationUseCase;
import com.example.admin.application.TenantScopeGuard;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.tenant.CreateTenantCommand;
import com.example.admin.application.tenant.CreateTenantResult;
import com.example.admin.application.tenant.CreateTenantUseCase;
import com.example.admin.application.tenant.GetTenantUseCase;
import com.example.admin.application.tenant.ListTenantsUseCase;
import com.example.admin.application.tenant.TenantFirstAdminSummaryQuery;
import com.example.admin.application.tenant.TenantFirstAdminSummaryQuery.FirstAdminSummary;
import com.example.admin.application.tenant.TenantPageSummary;
import com.example.admin.application.tenant.TenantSummary;
import com.example.admin.application.tenant.UpdateTenantUseCase;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.security.OperatorContextHolder;
import com.example.admin.application.OperatorContext;
import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import com.example.admin.presentation.aspect.RequiresPermission;
import com.example.admin.presentation.dto.OperatorInvitationResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
import java.util.Map;
import java.util.UUID;

/**
 * TASK-BE-250: Tenant lifecycle CRUD endpoints for admin operators.
 *
 * <ul>
 *   <li>POST   /api/admin/tenants           — SUPER_ADMIN only (platform scope) → 201</li>
 *   <li>PATCH  /api/admin/tenants/{id}      — SUPER_ADMIN only → 200</li>
 *   <li>GET    /api/admin/tenants           — SUPER_ADMIN only → 200 paginated</li>
 *   <li>GET    /api/admin/tenants/{id}      — SUPER_ADMIN only ({@code tenant.manage}) → 200</li>
 *   <li>POST   /api/admin/tenants/{id}/first-admin-invitation — SUPER_ADMIN only → 201 (TASK-MONO-773 S4)</li>
 * </ul>
 *
 * <p>TASK-MONO-773 S4: {@code POST} requires {@code firstAdmin} and answers the {@code PENDING} tenant + its
 * first-admin invitation; every tenant response carries the {@code firstAdmin} summary
 * ({@link TenantFirstAdminSummaryQuery}).
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
    private final AdminOperatorJpaRepository operatorRepository;
    private final TenantScopeGuard tenantScopeGuard;
    private final com.example.admin.application.TenantOrgNodePlacementUseCase placementUseCase;
    /** TASK-MONO-773 S4 — the {@code firstAdmin} summary of every tenant response. */
    private final TenantFirstAdminSummaryQuery firstAdminSummaryQuery;
    /** TASK-MONO-773 S4 — {@code POST …/first-admin-invitation} (OD-2 re-invite). */
    private final OperatorInvitationUseCase invitationUseCase;

    /**
     * POST /api/admin/tenants
     * SUPER_ADMIN only. Creates a new tenant ({@code PENDING}) and its first-admin invitation (TASK-MONO-773 S4).
     * {@code @RequiresPermission} satisfies the deny-by-default AOP guardrail in
     * {@code RequiresPermissionAspect}; platform-scope is enforced additionally below.
     */
    @PostMapping
    @RequiresPermission(Permission.TENANT_MANAGE)
    public ResponseEntity<TenantResponse> createTenant(
            @RequestHeader("X-Operator-Reason") String reason,
            // TASK-BE-635: CreateTenantUseCase stores this verbatim in admin_actions.idempotency_key
            // VARCHAR(100) (or a random UUID fallback when absent — always ≤100).
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Size(max = ClientIdempotencyKey.MAX_LENGTH, message = "Idempotency-Key must be ≤100 characters")
            String idempotencyKey,
            @Valid @RequestBody CreateTenantRequest request) {

        OperatorContext operator = OperatorContextHolder.require();
        requirePlatformScope(operator);

        FirstAdminRequest firstAdmin = request.firstAdmin();
        CreateTenantCommand command = new CreateTenantCommand(
                request.tenantId(), request.displayName(), request.tenantType(),
                firstAdmin.email(), firstAdmin.displayName(),
                operator, decodeReason(reason), resolveIdempotencyKey(idempotencyKey));

        // TASK-BE-625: an optional org-node goes through the placement rule (destination
        // checked BEFORE creation). Absent → the pre-existing path, byte-unchanged.
        String orgNodeId = request.orgNodeId();
        CreateTenantResult result = (orgNodeId == null || orgNodeId.isBlank())
                ? createTenantUseCase.execute(command)
                : placementUseCase.createTenantUnder(command, orgNodeId);

        return ResponseEntity.status(HttpStatus.CREATED).body(TenantResponse.from(
                result.tenant(),
                TenantFirstAdminSummaryQuery.ofIssued(result.firstAdminInvitation()),
                result.firstAdminInvitation() == null
                        ? null : OperatorInvitationResponse.from(result.firstAdminInvitation())));
    }

    /**
     * POST /api/admin/tenants/{tenantId}/first-admin-invitation — TASK-MONO-773 S4 (OD-2 «재초대»: another address,
     * or a PENDING tenant whose invitation was cancelled). SUPER_ADMIN ({@code '*'} grant scope) only.
     */
    @PostMapping("/{tenantId}/first-admin-invitation")
    @RequiresPermission(Permission.TENANT_MANAGE)
    public ResponseEntity<OperatorInvitationResponse> reinviteFirstAdmin(
            @PathVariable String tenantId,
            @RequestHeader("X-Operator-Reason") String reason,
            // Required by the contract; checked in the use case (→ 400 VALIDATION_ERROR) so the order is the
            // contract's (reason · key · body after the permission and scope checks).
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Size(max = ClientIdempotencyKey.MAX_LENGTH, message = "Idempotency-Key must be ≤100 characters")
            String idempotencyKey,
            @RequestBody(required = false) FirstAdminRequest request) {

        OperatorContext operator = OperatorContextHolder.require();
        requirePlatformScope(operator);

        OperatorInvitationResult result = invitationUseCase.reinviteFirstAdmin(operator, tenantId,
                request == null ? null : request.email(), request == null ? null : request.displayName(),
                idempotencyKey, decodeReason(reason));
        return ResponseEntity.status(HttpStatus.CREATED).body(OperatorInvitationResponse.from(result));
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
            // TASK-BE-635: UpdateTenantUseCase appends ":status" (7 chars) to this for the status-change audit
            // row when both displayName and status change — bound to TENANT_UPDATE_HEADER_MAX (93), not the
            // general 100, so the suffixed form still fits admin_actions.idempotency_key VARCHAR(100).
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Size(max = ClientIdempotencyKey.TENANT_UPDATE_HEADER_MAX,
                    message = "Idempotency-Key must be ≤93 characters")
            String idempotencyKey,
            @Valid @RequestBody UpdateTenantRequest request) {

        OperatorContext operator = OperatorContextHolder.require();
        requirePlatformScope(operator);

        TenantSummary result = updateTenantUseCase.execute(
                tenantId, request.displayName(), request.status(),
                operator, decodeReason(reason), resolveIdempotencyKey(idempotencyKey));

        return ResponseEntity.ok(TenantResponse.from(result, firstAdminSummaryQuery.summarize(result), null));
    }

    /**
     * GET /api/admin/tenants
     * SUPER_ADMIN only. Paginated tenant list.
     * {@code @RequiresPermission} is the primary RBAC gate (TASK-BE-408); the
     * inline {@code requirePlatformScope} check remains as defense-in-depth.
     */
    @GetMapping
    @RequiresPermission(Permission.TENANT_MANAGE)
    public ResponseEntity<TenantPageResponse> listTenants(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tenantType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        OperatorContext operator = OperatorContextHolder.require();
        requirePlatformScope(operator);

        TenantPageSummary result = listTenantsUseCase.execute(status, tenantType, page, size);
        // TASK-MONO-773 S4 — one operator_invitation read for the whole page.
        Map<String, FirstAdminSummary> summaries = firstAdminSummaryQuery.summarizeAll(result.items());
        return ResponseEntity.ok(TenantPageResponse.from(result, summaries));
    }

    /**
     * GET /api/admin/tenants/{tenantId}
     * SUPER_ADMIN only — {@code tenant.manage} (seeded to SUPER_ADMIN only) is the
     * primary RBAC gate (TASK-BE-408), so no non-SUPER_ADMIN operator reaches the
     * body. The inline {@code isTenantAllowed} scope check remains as defense-in-depth
     * (reachable only after the RBAC aspect passes); it does NOT widen access to
     * non-{@code tenant.manage} holders.
     */
    @GetMapping("/{tenantId}")
    @RequiresPermission(Permission.TENANT_MANAGE)
    public ResponseEntity<TenantResponse> getTenant(@PathVariable String tenantId) {
        OperatorContext operator = OperatorContextHolder.require();

        // TASK-MONO-784 S3 (§ 행동 테넌트 #17 · D-4): the path tenant must be in the tenant.manage GRANT scope
        // — the shared decision site, no DENIED row (a read). This controller used to carry its own copy of
        // the effective-scope rule (home ∪ assignments, TASK-BE-326).
        tenantScopeGuard.requireTenantReadable(operator, Permission.TENANT_MANAGE, tenantId);

        TenantSummary result = getTenantUseCase.execute(tenantId);
        return ResponseEntity.ok(TenantResponse.from(result, firstAdminSummaryQuery.summarize(result), null));
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
                        e.getTenantId(),
                        // TASK-BE-304: not read here (tenant admin path); pass through
                        // so the domain record stays internally consistent.
                        e.getFinanceDefaultAccountId()))
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

    /**
     * {@code orgNodeId} (TASK-BE-625) is optional; absent or null = created ungrouped, as before.
     * {@code firstAdmin} (TASK-MONO-773 S4) is REQUIRED — absent / null → 400 VALIDATION_ERROR (breaking, admin-api.md).
     */
    public record CreateTenantRequest(
            @NotBlank String tenantId,
            @NotBlank @Size(min = 1, max = 100) String displayName,
            @NotBlank String tenantType,
            String orgNodeId,
            @NotNull @Valid FirstAdminRequest firstAdmin
    ) {}

    /**
     * TASK-MONO-773 S4 — {@code firstAdmin} of {@code POST /api/admin/tenants} and the body of
     * {@code POST …/first-admin-invitation}. Format rules (email · 1–64 chars) are judged by the use case AFTER
     * trim — the 772 issuance's rule — so they are not bean-validation constraints here.
     */
    public record FirstAdminRequest(String email, String displayName) {}

    public record UpdateTenantRequest(
            @Size(min = 1, max = 100) String displayName,
            String status
    ) {}

    /**
     * {@code firstAdmin} (TASK-MONO-773 S4) is always present — {@code null} for a tenant without a first-admin
     * record — whatever the ObjectMapper's global inclusion (the {@code test} profile sets {@code non_null});
     * {@code firstAdminInvitation} exists only on the POST response.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TenantResponse(
            String tenantId,
            String displayName,
            String tenantType,
            String status,
            Instant createdAt,
            Instant updatedAt,
            FirstAdminResponse firstAdmin,
            @JsonInclude(JsonInclude.Include.NON_NULL) OperatorInvitationResponse firstAdminInvitation
    ) {
        public static TenantResponse from(TenantSummary s) {
            return from(s, null, null);
        }

        public static TenantResponse from(TenantSummary s, FirstAdminSummary firstAdmin,
                                          OperatorInvitationResponse firstAdminInvitation) {
            return new TenantResponse(
                    s.tenantId(), s.displayName(), s.tenantType(),
                    s.status(), s.createdAt(), s.updatedAt(),
                    FirstAdminResponse.from(firstAdmin), firstAdminInvitation);
        }
    }

    /** admin-api.md «테넌트 응답의 firstAdmin 요약» — every key present ({@code NONE} → three {@code null}s). */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record FirstAdminResponse(String state, String email, String invitationId, Instant expiresAt) {
        static FirstAdminResponse from(FirstAdminSummary s) {
            return s == null ? null : new FirstAdminResponse(s.state(), s.email(), s.invitationId(), s.expiresAt());
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
            return from(s, Map.of());
        }

        public static TenantPageResponse from(TenantPageSummary s, Map<String, FirstAdminSummary> firstAdmins) {
            return new TenantPageResponse(
                    s.items().stream()
                            .map(t -> TenantResponse.from(t, firstAdmins.get(t.tenantId()), null))
                            .toList(),
                    s.page(), s.size(), s.totalElements(), s.totalPages());
        }
    }
}
