package com.example.admin.presentation.dto;

import com.example.admin.application.DomainPermissionSetResult;
import com.example.admin.application.GrantDomainPermissionSetResult;
import com.example.admin.application.OperatorDomainAccessResult;
import com.example.admin.domain.rbac.DomainRoleCatalog;
import com.example.admin.domain.rbac.DomainRoleCatalog.RoleRef;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * TASK-BE-631 — the request / response shapes of admin-api.md § 도메인 업무 권한 세트. Kept in one holder because they
 * share the {@code {domainKey, role}} element. Every response record is {@code ALWAYS}-inclusion where the contract
 * shows a key with a {@code null} value ({@code tenantId} / {@code systemKey} of a set, {@code grantedBy} of a
 * MIGRATION source), so the wire shape does not depend on the profile's global inclusion setting (the test profile
 * sets {@code non_null}).
 */
public final class DomainPermissionSetDtos {

    private DomainPermissionSetDtos() {
    }

    /** A {@code {domainKey, role}} pair — request and response. */
    public record RoleRefDto(String domainKey, String role) {
        public static RoleRefDto from(RoleRef r) {
            return new RoleRefDto(r.domainKey(), r.role());
        }

        public RoleRef toRef() {
            return new RoleRef(domainKey, role);
        }

        /** {@code null} stays {@code null} (the field was not sent); elements may be {@code null} (→ 400 later). */
        public static List<RoleRef> toRefs(List<RoleRefDto> dtos) {
            if (dtos == null) {
                return null;
            }
            List<RoleRef> refs = new ArrayList<>(dtos.size());
            for (RoleRefDto d : dtos) {
                refs.add(d == null ? null : d.toRef());
            }
            return refs;
        }
    }

    /** {@code POST /api/admin/domain-permission-sets} body. */
    public record CreateDomainPermissionSetRequest(String name, String description, List<RoleRefDto> roles) {}

    /** {@code PATCH /api/admin/domain-permission-sets/{setId}} body — every field optional, at least one. */
    public record UpdateDomainPermissionSetRequest(String name, String description, List<RoleRefDto> roles) {}

    /** {@code POST /api/admin/operators/{operatorId}/domain-permission-sets} body. */
    public record GrantDomainPermissionSetRequest(String setId) {}

    /** «Domain role wire shape». */
    public record DomainRoleResponse(String domainKey, String role, String access, boolean fineGrained,
                                     String label) {
        public static DomainRoleResponse from(DomainRoleCatalog.Entry e) {
            return new DomainRoleResponse(e.domainKey(), e.role(), e.access(), e.fineGrained(), e.label());
        }
    }

    /** {@code {"items": [...]}} — the catalog and the set list share this envelope. */
    public record ItemsResponse<T>(List<T> items) {}

    /** «Domain permission set wire shape». */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DomainPermissionSetResponse(String setId, String tenantId, String systemKey, boolean readOnly,
                                              String name, String description, List<RoleRefDto> roles,
                                              List<String> domains, long grantCount, long groupCount,
                                              Instant createdAt, Instant updatedAt) {
        public static DomainPermissionSetResponse from(DomainPermissionSetResult r) {
            var s = r.set();
            return new DomainPermissionSetResponse(s.setId(), s.tenantId(), s.systemKey(), s.isPreset(), s.name(),
                    s.description(), s.roles().stream().map(RoleRefDto::from).toList(),
                    DomainRoleCatalog.domainsOf(s.roles()), r.grantCount(), r.groupCount(), s.createdAt(),
                    s.updatedAt());
        }
    }

    /** {@code GET …/operators/{operatorId}/domain-access} · {@code GET /me/domain-access}. */
    public record DomainAccessResponse(String operatorId, String tenantId, List<RoleRefDto> roles,
                                       List<SetAccessResponse> sets) {
        public static DomainAccessResponse from(OperatorDomainAccessResult r) {
            List<SetAccessResponse> sets = new ArrayList<>();
            for (OperatorDomainAccessResult.SetAccess s : r.sets()) {
                sets.add(new SetAccessResponse(s.setId(), s.name(), s.systemKey(),
                        s.sources().stream().map(SourceResponse::from).toList()));
            }
            return new DomainAccessResponse(r.operatorId(), r.tenantId(),
                    r.roles().stream().map(RoleRefDto::from).toList(), sets);
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SetAccessResponse(String setId, String name, String systemKey, List<SourceResponse> sources) {}

    /** {@code groupId} / {@code groupName} only on a GROUP source; {@code grantedBy} is {@code null} for MIGRATION. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SourceResponse(String source,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) String groupId,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) String groupName,
                                 String tenantId, Instant grantedAt, String grantedBy) {
        static SourceResponse from(OperatorDomainAccessResult.Source s) {
            return new SourceResponse(s.source(), s.groupId(), s.groupName(), s.tenantId(), s.grantedAt(),
                    s.grantedBy());
        }
    }

    /** The 201 of a DIRECT grant. */
    public record GrantDomainPermissionSetResponse(String operatorId, String tenantId, String setId, String source,
                                                   Instant grantedAt, String grantedBy, boolean selfGrant) {
        public static GrantDomainPermissionSetResponse from(GrantDomainPermissionSetResult r) {
            return new GrantDomainPermissionSetResponse(r.operatorId(), r.tenantId(), r.setId(), r.source(),
                    r.grantedAt(), r.grantedBy(), r.selfGrant());
        }
    }
}
