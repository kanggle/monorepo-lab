# TASK-PC-FE-157 — Operator ↔ Tenant Assignment console UI (delegation onboarding)

- **Status**: done
- **Domain**: saas-console (platform-console)
- **Type**: feature (frontend)
- **분석=Opus 4.8 / 구현 권장=Opus 4.8 (RBAC-sensitive mutation surface)**

## Goal

Close the console gap that blocks the ADR-MONO-024 delegation chain from being
exercised **by clicks**: a SUPER_ADMIN (or a tenant-scoped TENANT_ADMIN) can
onboard employee/partner operator accounts into a tenant directly from the
운영자 관리 screen. The producer endpoints already exist (TASK-BE-347:
`POST/DELETE /api/admin/operators/{operatorId}/assignments/{tenantId}`); the
console only exposes org-scope editing of an EXISTING assignment (PC-FE-050),
never assignment creation/removal, and its role selector omits `TENANT_ADMIN`,
so a SUPER_ADMIN cannot even appoint a tenant admin through the UI.

## Scope

In-scope (all inside `projects/platform-console/apps/console-web/`):

1. **Selectable roles** — add `TENANT_ADMIN` + `TENANT_BILLING_ADMIN` to
   `KNOWN_OPERATOR_ROLES` (the create-form + edit-roles selectors). The list
   view already tolerates unknown roles; this only widens the *offer* set.
   No-escalation is producer-enforced (`ROLE_GRANT_FORBIDDEN`) — mapped inline.
2. **Assign (POST)** — a "테넌트 배정" form on 운영자 관리: enter an operatorId,
   reason-capture + confirm, POST to the **active tenant**. Targets the active
   tenant (`X-Tenant-Id`) — the natural "grant my employee access to my tenant"
   case; a SUPER_ADMIN switches the active tenant to assign elsewhere.
3. **Unassign (DELETE)** — per-row "배정 해제" action, reason-capture + confirm,
   DELETE for the active tenant. `404 ASSIGNMENT_NOT_FOUND` (home-tenant-only
   operator, no explicit assignment) maps inline (no crash).
4. **Proxy route** — `app/api/operators/[operatorId]/assignments/[tenantId]/route.ts`
   POST + DELETE (mirrors the existing org-scope PUT-only sibling). Reason in
   body → `X-Operator-Reason` server-side; operator token + active tenant
   attached server-side.
5. **activeTenant plumbing** — `OperatorsPage` passes the active tenant slug to
   `OperatorsScreen` (display + path building).
6. **Consumer contract** — extend `console-integration-contract.md` § 2.4.3 with
   the two new consumer surfaces (assign / unassign header matrix).

Out-of-scope: assign-to-arbitrary-tenant picker (SUPER_ADMIN cross-tenant via
active-tenant switch is sufficient for v1); TENANT_ADMIN demo seed (a SUPER_ADMIN
can now appoint one through this very UI); permission_set_id narrowing.

## Acceptance Criteria

- AC-1: 운영자 관리 offers `TENANT_ADMIN`/`TENANT_BILLING_ADMIN` in create +
  edit-roles selectors; granting either is confirm+reason gated.
- AC-2: The assign form POSTs `.../assignments/{activeTenant}` with a non-empty
  `X-Operator-Reason`; success invalidates the operators list + assignments.
- AC-3: A per-row 배정 해제 DELETEs `.../assignments/{activeTenant}` with reason;
  success invalidates the list.
- AC-4: Producer errors (403 PERMISSION_DENIED / 403 TENANT_SCOPE_DENIED /
  404 OPERATOR_NOT_FOUND / 404 ASSIGNMENT_NOT_FOUND / 409 ASSIGNMENT_ALREADY_EXISTS
  / 400 REASON_REQUIRED) map inline in the confirm dialog — never a crash /
  re-login loop; 401 → forced re-login; 503/timeout → section degrade.
- AC-5: Reason is percent-encoded on the wire (Korean-safe) — reuses the
  existing `callGapOperators` header matrix (no new header logic).
- AC-6: `pnpm lint` + `tsc --noEmit` + `vitest` all green; new unit tests cover
  the assign/unassign api fns + the proxy route + the role-selector widening.

## Related Specs

- `platform-console/specs/contracts/console-integration-contract.md` § 2.4.3
- `iam/specs/services/admin-service/rbac.md` (TENANT_ADMIN, no-escalation, D2)

## Related Contracts

- `iam/specs/contracts/http/admin-api.md`
  §§ `POST /api/admin/operators/{operatorId}/assignments/{tenantId}`,
     `DELETE /api/admin/operators/{operatorId}/assignments/{tenantId}`
  (producer — already merged, TASK-BE-347; consumed as-is)

## Edge Cases

- Home-tenant-only operator → DELETE 404 ASSIGNMENT_NOT_FOUND → inline message
  ("현재 테넌트에 명시 배정 행이 없습니다").
- Target operator not in the active-tenant list (assign brings in an operator
  from outside the current list scope) → the assign form takes a free-text
  operatorId, not a row action.
- TENANT_ADMIN granting TENANT_BILLING_ADMIN (lacks subscription.manage) →
  producer 403 ROLE_GRANT_FORBIDDEN → inline.

## Failure Scenarios

- Empty reason → api-layer fail-safe `400 REASON_REQUIRED` before any fetch.
- No active tenant → `400 NO_ACTIVE_TENANT` gate (existing).
- Assign/unassign one-click without confirm/reason → impossible (confirm dialog
  gates every mutation, mirroring create/roles/status).
