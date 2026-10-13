package com.example.admin.application;

import com.example.admin.application.exception.DomainPermissionSetInUseException;
import com.example.admin.application.exception.DomainPermissionSetNotFoundException;
import com.example.admin.application.exception.DomainPermissionSetReadOnlyException;
import com.example.admin.application.exception.DomainRoleNotAssignableException;
import com.example.admin.application.exception.IdempotencyKeyConflictException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.DomainPermissionSetPort;
import com.example.admin.application.port.DomainPermissionSetPort.SetView;
import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.domain.rbac.DomainPermissionSetPresets;
import com.example.admin.domain.rbac.DomainRoleCatalog;
import com.example.admin.domain.rbac.DomainRoleCatalog.RoleRef;
import com.example.admin.domain.rbac.Permission;
import com.example.common.id.UuidV7;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TASK-BE-631 (ADR-MONO-083 D1 · D2 · D3 · D5) — the work-role catalog and the domain permission set CRUD of
 * admin-api.md § 도메인 업무 권한 세트 ({@code GET /domain-roles} · {@code GET|POST /domain-permission-sets} ·
 * {@code GET|PATCH|DELETE /domain-permission-sets/{setId}}).
 *
 * <p><b>Who.</b> {@code domain_access.manage} is the endpoint gate ({@code @RequiresPermission}, stage 1). The acting
 * tenant T comes from {@code X-Tenant-Id} under the omitted-tenant rule and must be in that key's GRANT scope
 * ({@link QueryTenantScopeGate}, stage 2 — § 행동 테넌트 row 25). Reads refuse without a DENIED row, writes with one.
 *
 * <p><b>What T sees.</b> The system presets plus T's own tenant sets; another tenant's set is
 * {@code 404 DOMAIN_PERMISSION_SET_NOT_FOUND} (never disclosed). T = {@code '*'} sees presets only.
 *
 * <p><b>Bounds of a tenant set</b> (create · edit): every role a catalog row ({@code 422 DOMAIN_ROLE_NOT_ASSIGNABLE}),
 * every domain inside T's entitled domains ({@link DomainSetEntitlementGuard} — {@code 422 DOMAIN_NOT_SUBSCRIBED},
 * {@code 503} when that cannot be read). The ≤-own {@code RoleGrantGuard} is deliberately NOT applied: the
 * administrator need not hold the work roles it bundles (rbac.md). Presets are read-only
 * ({@code 409 DOMAIN_PERMISSION_SET_READ_ONLY}); a set still granted anywhere cannot be deleted
 * ({@code 409 DOMAIN_PERMISSION_SET_IN_USE} — no silent mass revoke).
 *
 * <p>Every refusal writes nothing. Every successful write leaves one {@code admin_actions} row
 * ({@code permission_used=domain_access.manage}, target = the set, target tenant = T) in the same transaction
 * (fail-closed: an audit failure rolls the write back → {@code 500 AUDIT_FAILURE}). Set changes never touch the
 * management-plane permission cache — a set grants no {@code /api/admin/**} authority.
 */
@Service
@RequiredArgsConstructor
public class DomainPermissionSetUseCase {

    static final int MAX_NAME = 120;
    static final int MAX_DESCRIPTION = 255;
    static final int MAX_IDEMPOTENCY_KEY = 100;

    private final DomainPermissionSetPort setPort;
    private final AdminOperatorPort operatorPort;
    private final QueryTenantScopeGate tenantGate;
    private final DomainSetEntitlementGuard entitlementGuard;
    private final AdminActionAuditor auditor;

    // ── catalog ─────────────────────────────────────────────────────────────

    /** {@code GET /domain-roles} — the whole catalog, no tenant data, no tenant gate (§ 행동 테넌트 row 24). */
    public List<DomainRoleCatalog.Entry> catalog() {
        return DomainRoleCatalog.entries();
    }

    // ── reads ───────────────────────────────────────────────────────────────

    /** Presets first (table order), then T's tenant sets ({@code createdAt} ascending). No audit row. */
    @Transactional(readOnly = true)
    public List<DomainPermissionSetResult> list(OperatorContext actor, String requestedTenantId) {
        String tenantId = tenantGate.resolveWithoutDenyRow(actor, requestedTenantId, Permission.DOMAIN_ACCESS_MANAGE)
                .tenantId();
        List<SetView> presets = new ArrayList<>(setPort.findPresets());
        presets.sort(Comparator.comparingInt(s -> DomainPermissionSetPresets.orderOf(s.systemKey())));
        List<DomainPermissionSetResult> out = new ArrayList<>();
        for (SetView preset : presets) {
            out.add(withCounts(preset, tenantId));
        }
        if (!AdminOperator.PLATFORM_TENANT_ID.equals(tenantId)) {
            for (SetView set : setPort.findTenantSets(tenantId)) {
                out.add(withCounts(set, tenantId));
            }
        }
        return out;
    }

    /** One preset or one of T's sets; anything else → 404. No audit row. */
    @Transactional(readOnly = true)
    public DomainPermissionSetResult get(OperatorContext actor, String requestedTenantId, String setId) {
        String tenantId = tenantGate.resolveWithoutDenyRow(actor, requestedTenantId, Permission.DOMAIN_ACCESS_MANAGE)
                .tenantId();
        return withCounts(visibleSet(setId, tenantId), tenantId);
    }

    // ── writes ──────────────────────────────────────────────────────────────

    @Transactional
    public DomainPermissionSetResult create(CreateDomainPermissionSetCommand cmd) {
        AuditReasons.require(cmd.reason());
        String key = cmd.idempotencyKey();
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
        if (key.length() > MAX_IDEMPOTENCY_KEY) {
            throw new IllegalArgumentException("Idempotency-Key must be at most " + MAX_IDEMPOTENCY_KEY + " characters");
        }
        String tenantId = tenantGate.resolve(cmd.actor(), cmd.requestedTenantId(),
                ActionCode.DOMAIN_PERMISSION_SET_CREATE, Permission.DOMAIN_ACCESS_MANAGE).tenantId();
        if (AdminOperator.PLATFORM_TENANT_ID.equals(tenantId)) {
            throw new IllegalArgumentException(
                    "A tenant set belongs to one real tenant — '*' has presets only (send X-Tenant-Id)");
        }
        String name = requireName(cmd.name());
        String description = checkDescription(cmd.description());
        List<RoleRef> roles = requireCatalogRoles(cmd.roles());

        // A re-sent key → 409 before anything is written (its audit row would otherwise die on idx_admin_actions_idemp).
        if (auditor.isIdempotencyKeyUsed(cmd.actor().operatorId(), ActionCode.DOMAIN_PERMISSION_SET_CREATE, key)) {
            throw new IdempotencyKeyConflictException(
                    "Idempotency-Key already used for DOMAIN_PERMISSION_SET_CREATE by this operator; retry with a new key");
        }
        // Entitlement LAST before the write: 422 / 503 and nothing written.
        entitlementGuard.requireEntitled(tenantId, DomainRoleCatalog.domainsOf(roles));

        Instant now = Instant.now();
        SetView created = setPort.createTenantSet(UuidV7.randomString(), tenantId, name, description, roles,
                operatorPort.resolveActorInternalId(cmd.actor().operatorId()), now);
        audit(ActionCode.DOMAIN_PERMISSION_SET_CREATE, cmd.actor(), created.setId(), tenantId, cmd.reason(), key,
                "roles=" + render(created.roles()), now);
        return new DomainPermissionSetResult(created, 0, 0);
    }

    @Transactional
    public DomainPermissionSetResult update(UpdateDomainPermissionSetCommand cmd) {
        AuditReasons.require(cmd.reason());
        String tenantId = tenantGate.resolve(cmd.actor(), cmd.requestedTenantId(),
                ActionCode.DOMAIN_PERMISSION_SET_UPDATE, Permission.DOMAIN_ACCESS_MANAGE).tenantId();
        if (cmd.name() == null && cmd.description() == null && cmd.roles() == null) {
            throw new IllegalArgumentException("At least one of 'name', 'description' or 'roles' must be supplied");
        }
        SetView before = visibleSet(cmd.setId(), tenantId);
        if (before.isPreset()) {
            throw new DomainPermissionSetReadOnlyException(
                    "System preset '" + before.systemKey() + "' is read-only — copy it into a tenant set");
        }
        String name = cmd.name() == null ? null : requireName(cmd.name());
        String description = checkDescription(cmd.description());
        List<RoleRef> roles = cmd.roles() == null ? null : requireCatalogRoles(cmd.roles());
        if (roles != null) {
            entitlementGuard.requireEntitled(tenantId, DomainRoleCatalog.domainsOf(roles));
        }

        Instant now = Instant.now();
        SetView after = setPort.updateTenantSet(before.internalId(), name, description, roles, now);
        List<String> changed = new ArrayList<>();
        if (name != null) changed.add("name");
        if (cmd.description() != null) changed.add("description");
        if (roles != null) changed.add("roles");
        String detail = "changed=" + String.join(",", changed)
                + (roles != null ? " rolesBefore=" + render(before.roles()) + " rolesAfter=" + render(after.roles()) : "");
        audit(ActionCode.DOMAIN_PERMISSION_SET_UPDATE, cmd.actor(), after.setId(), tenantId, cmd.reason(), null,
                detail, now);
        return withCounts(after, tenantId);
    }

    @Transactional
    public void delete(OperatorContext actor, String requestedTenantId, String setId, String reason) {
        AuditReasons.require(reason);
        String tenantId = tenantGate.resolve(actor, requestedTenantId,
                ActionCode.DOMAIN_PERMISSION_SET_DELETE, Permission.DOMAIN_ACCESS_MANAGE).tenantId();
        SetView set = visibleSet(setId, tenantId);
        if (set.isPreset()) {
            throw new DomainPermissionSetReadOnlyException(
                    "System preset '" + set.systemKey() + "' is read-only and cannot be deleted");
        }
        long grantRows = setPort.countAllGrantRows(set.internalId());
        long groupRows = setPort.countAllGroupGrants(set.internalId());
        if (grantRows > 0 || groupRows > 0) {
            throw new DomainPermissionSetInUseException("Set '" + setId + "' is still granted (" + grantRows
                    + " operator grant row(s), " + groupRows + " group(s)) — revoke those first");
        }
        Instant now = Instant.now();
        setPort.deleteSet(set.internalId());
        audit(ActionCode.DOMAIN_PERMISSION_SET_DELETE, actor, set.setId(), tenantId, reason, null,
                "name=" + set.name(), now);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /**
     * The set {@code setId} if T may see it: any preset, or a tenant set owned by T. Missing / another tenant's /
     * (T = '*') a tenant set → 404 (never disclosed).
     */
    private SetView visibleSet(String setId, String tenantId) {
        SetView set = setPort.findBySetId(setId).orElseThrow(() -> notFound(setId));
        if (!set.isPreset() && !set.tenantId().equals(tenantId)) {
            throw notFound(setId);
        }
        return set;
    }

    private DomainPermissionSetResult withCounts(SetView set, String tenantId) {
        return new DomainPermissionSetResult(set, setPort.countGrantRows(set.internalId(), tenantId),
                setPort.countGroupGrants(set.internalId(), tenantId));
    }

    private static DomainPermissionSetNotFoundException notFound(String setId) {
        return new DomainPermissionSetNotFoundException("Domain permission set '" + setId + "' not found");
    }

    static String requireName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty() || name.length() > MAX_NAME) {
            throw new IllegalArgumentException("name must be 1.." + MAX_NAME + " characters after trimming");
        }
        return name;
    }

    static String checkDescription(String description) {
        if (description != null && description.length() > MAX_DESCRIPTION) {
            throw new IllegalArgumentException("description must be at most " + MAX_DESCRIPTION + " characters");
        }
        return description;
    }

    /**
     * Shape (400): non-empty list, every element a {@code {domainKey, role}} with both present. Duplicates collapse.
     * Catalog (422): every pair a catalog row. Returns the pairs in catalog order.
     */
    static List<RoleRef> requireCatalogRoles(List<RoleRef> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("roles must hold at least one {domainKey, role}");
        }
        for (RoleRef r : roles) {
            if (r == null || r.domainKey() == null || r.domainKey().isBlank()
                    || r.role() == null || r.role().isBlank()) {
                throw new IllegalArgumentException("every roles[] element needs both 'domainKey' and 'role'");
            }
        }
        for (RoleRef r : roles) {
            if (!DomainRoleCatalog.isAssignable(r.domainKey(), r.role())) {
                throw new DomainRoleNotAssignableException("Role '" + r.domainKey() + ":" + r.role()
                        + "' is not in the assignable domain-role catalog");
            }
        }
        return DomainRoleCatalog.sortAndDistinct(roles);
    }

    static String render(List<RoleRef> roles) {
        return roles.stream().map(r -> r.domainKey() + ":" + r.role()).collect(Collectors.joining(","));
    }

    private void audit(ActionCode code, OperatorContext actor, String setId, String tenantId, String reason,
                       String idempotencyKey, String detail, Instant startedAt) {
        String auditId = auditor.newAuditId();
        auditor.recordWithPermission(new AdminActionAuditor.AuditRecord(
                auditId,
                code,
                actor,
                "DOMAIN_PERMISSION_SET",
                setId,
                AuditReasons.normalize(reason),
                null,
                idempotencyKey != null ? idempotencyKey : "domain-set:" + code.name() + ":" + auditId,
                Outcome.SUCCESS,
                detail,
                startedAt,
                Instant.now(),
                tenantId), Permission.DOMAIN_ACCESS_MANAGE);
    }
}
