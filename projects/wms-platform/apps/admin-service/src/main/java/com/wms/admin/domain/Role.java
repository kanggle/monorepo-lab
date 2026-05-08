package com.wms.admin.domain;

import com.wms.admin.domain.error.RoleBuiltinImmutableException;
import com.wms.admin.domain.error.StateTransitionInvalidException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Named permission bundle aggregate. {@code permissionsJson} is held as the
 * canonical JSON string in the domain — schema validation (array-of-strings,
 * known permission values) lives in the application service.
 *
 * <p>State machine: {@code ACTIVE ↔ INACTIVE} only.
 *
 * <p>Built-in roles ({@code WMS_VIEWER} / {@code WMS_OPERATOR} /
 * {@code WMS_ADMIN} / {@code WMS_SUPERADMIN}) carry {@code isBuiltin=true}.
 * Their {@code roleCode} / {@code name} / {@code description} are immutable
 * via API; only {@code permissionsJson} may be PATCHed and they cannot be
 * deactivated.
 */
public final class Role {

    /** Built-in role codes — protected from deactivate / delete. */
    public static final List<String> BUILTIN_CODES = List.of(
            "WMS_VIEWER", "WMS_OPERATOR", "WMS_ADMIN", "WMS_SUPERADMIN");

    private final UUID id;
    private final String roleCode;
    private final String name;
    private final String description;
    private final String permissionsJson;
    private final RoleStatus status;
    private final boolean isBuiltin;
    private final long version;
    private final Instant createdAt;
    private final String createdBy;
    private final Instant updatedAt;
    private final String updatedBy;

    public Role(UUID id, String roleCode, String name, String description,
                String permissionsJson, RoleStatus status, boolean isBuiltin,
                long version, Instant createdAt, String createdBy,
                Instant updatedAt, String updatedBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.roleCode = Objects.requireNonNull(roleCode, "roleCode");
        this.name = Objects.requireNonNull(name, "name");
        this.description = description;
        this.permissionsJson = Objects.requireNonNull(permissionsJson, "permissionsJson");
        this.status = Objects.requireNonNull(status, "status");
        this.isBuiltin = isBuiltin;
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.createdBy = createdBy;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.updatedBy = updatedBy;
    }

    public static Role create(UUID id, String roleCode, String name, String description,
                              String permissionsJson, Instant now, String actor) {
        return new Role(id, roleCode, name, description, permissionsJson,
                RoleStatus.ACTIVE, false, 0L,
                now, actor, now, actor);
    }

    public Role updateMetadata(String newName, String newDescription, String newPermissionsJson,
                               Instant now, String actor) {
        // Built-in roles: only permissionsJson may change. Caller (application service)
        // checks that name / description are unchanged or null before calling.
        return new Role(id, roleCode,
                newName != null ? newName : name,
                newDescription != null ? newDescription : description,
                newPermissionsJson != null ? newPermissionsJson : permissionsJson,
                status, isBuiltin, version,
                createdAt, createdBy, now, actor);
    }

    public Role deactivate(Instant now, String actor) {
        if (isBuiltin) {
            throw new RoleBuiltinImmutableException(roleCode);
        }
        if (status != RoleStatus.ACTIVE) {
            throw new StateTransitionInvalidException(
                    "role " + roleCode + " is " + status + ", cannot deactivate");
        }
        return new Role(id, roleCode, name, description, permissionsJson,
                RoleStatus.INACTIVE, isBuiltin, version,
                createdAt, createdBy, now, actor);
    }

    public Role reactivate(Instant now, String actor) {
        if (status != RoleStatus.INACTIVE) {
            throw new StateTransitionInvalidException(
                    "role " + roleCode + " is " + status + ", cannot reactivate");
        }
        return new Role(id, roleCode, name, description, permissionsJson,
                RoleStatus.ACTIVE, isBuiltin, version,
                createdAt, createdBy, now, actor);
    }

    public UUID id() { return id; }
    public String roleCode() { return roleCode; }
    public String name() { return name; }
    public String description() { return description; }
    public String permissionsJson() { return permissionsJson; }
    public RoleStatus status() { return status; }
    public boolean isBuiltin() { return isBuiltin; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public String createdBy() { return createdBy; }
    public Instant updatedAt() { return updatedAt; }
    public String updatedBy() { return updatedBy; }
}
