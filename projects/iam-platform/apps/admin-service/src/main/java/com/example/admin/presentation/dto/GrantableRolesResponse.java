package com.example.admin.presentation.dto;

import java.util.List;

/**
 * TASK-BE-388 (ADR-MONO-024 D3 read mirror) — response for
 * {@code GET /api/admin/operators/grantable-roles}: the seed-role names the
 * calling operator may grant, in stable seed (role-id) order.
 *
 * <p>Read hint only — the authoritative enforcement remains the producer
 * {@code RoleGrantGuard} (403 {@code ROLE_GRANT_FORBIDDEN}) on
 * {@code POST /operators} and {@code PATCH .../roles}. Distinct from
 * {@code OperatorSummaryResponse} on purpose (that DTO is shared by
 * {@code /me} / list / create and must not carry a grant-menu projection).
 */
public record GrantableRolesResponse(List<String> roles) {
}
