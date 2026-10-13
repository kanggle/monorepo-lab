package com.example.admin.application;

import java.time.Instant;

/** TASK-BE-631 — the 201 of a DIRECT set grant (admin-api.md § POST …/operators/{operatorId}/domain-permission-sets). */
public record GrantDomainPermissionSetResult(String operatorId, String tenantId, String setId, String source,
                                             Instant grantedAt, String grantedBy, boolean selfGrant) {}
