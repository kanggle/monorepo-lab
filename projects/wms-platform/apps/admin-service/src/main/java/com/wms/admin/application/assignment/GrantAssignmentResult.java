package com.wms.admin.application.assignment;

import com.wms.admin.domain.UserRoleAssignment;

/**
 * Wraps the granted assignment + a flag indicating whether the row is fresh
 * (HTTP 201) or an existing-active match (HTTP 200, idempotent grant).
 * Per {@code admin-service-api.md § 4.1}.
 */
public record GrantAssignmentResult(UserRoleAssignment assignment, boolean created) {
}
