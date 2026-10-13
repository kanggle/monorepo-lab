package com.example.admin.application;

/**
 * TASK-BE-631 slice B — {@code POST /api/admin/groups/{groupId}/domain-permission-sets} (admin-api.md § 도메인 업무
 * 권한 세트). The acting tenant is the GROUP's tenant (§ 행동 테넌트 row 27), so there is no {@code X-Tenant-Id}.
 */
public record GrantGroupDomainPermissionSetCommand(OperatorContext actor, String groupId, String setId,
                                                   String reason, String idempotencyKey) {}
