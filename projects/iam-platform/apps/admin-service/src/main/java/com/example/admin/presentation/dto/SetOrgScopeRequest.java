package com.example.admin.presentation.dto;

import java.util.List;

/**
 * TASK-BE-339 — request body for
 * {@code PUT /api/admin/operators/{operatorId}/assignments/{tenantId}/org-scope}.
 *
 * <p>{@code orgScope} value semantics (preserved end-to-end):
 * <ul>
 *   <li>{@code null} (or key absent) → clear the column (⟺ {@code ["*"]}
 *       net-zero whole tenant)</li>
 *   <li>{@code []} → persist an explicit empty list (zero-scope; distinct from
 *       null)</li>
 *   <li>{@code [ids]} → normalize (trim, reject blank, dedupe, cap) then
 *       persist</li>
 * </ul>
 *
 * <p>Jackson maps both an absent key and an explicit {@code null} to a
 * {@code null} field, and {@code []} to an empty (non-null) list — which is
 * exactly the clear-vs-zero-scope distinction this surface requires.
 */
public record SetOrgScopeRequest(List<String> orgScope) {}
