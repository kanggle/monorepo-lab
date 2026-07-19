# Task ID

TASK-PC-FE-250

# Title

ADR-046 step 3 — 운영자 그룹 console screen (`features/operator-groups/`): CRUD + membership + group-level grants + no-escalation gating, replacing the `/operator-groups` stub

# Status

review

# Owner

frontend

# Task Tags

- frontend
- console
- iam

---

# Dependency Markers

- **prerequisite (선행)**: `TASK-BE-520` (backend `/api/admin/groups` API) — MUST be merged first; this screen consumes it. `TASK-BE-519` (contracts). `TASK-MONO-428` (ADR-046 ACCEPTED).
- **replaces (대체)**: the `/operator-groups` stub route created by `TASK-PC-FE-225` (`data-testid="operator-groups-stub"`).

---

# Goal

Replace the `/operator-groups` placeholder with a real 「운영자 그룹」 management screen that consumes BE-520's `/api/admin/groups` API through the console-bff, mirroring the established `features/tenants/` (CRUD shell) + `features/operators/` (role/tenant-assignment + grantable-roles no-escalation gating) patterns.

---

# Scope

## In Scope

- `projects/platform-console/apps/console-web/src/features/operator-groups/`:
  - `api/` — `operator-groups-api.ts`, `operator-groups-client.ts`, `operator-groups-state.ts` (server-side `getOperatorGroupsState()` loader with the standard resilience waterfall: unauthorized → no-tenant → permission-error/forbidden → degraded → happy), `types.ts`.
  - `components/` — `OperatorGroupsScreen`, `GroupsTable`, `GroupDetail` (members + grants panels), `GroupForm` (create/edit), `GroupMemberDialog`, `GroupGrantDialog`, confirm dialogs — mirroring `features/tenants/` + `features/operators/`.
  - `hooks/use-operator-groups.ts`, `index.ts`.
- BFF proxy route(s) under `app/api/groups/` (or the established console-bff pattern) — the single backend entry point (no browser-direct IAM call, per architecture.md § Forbidden Dependencies). Error-mapping for 401/403/404/409/422/503 → the console's typed states.
- `app/(console)/operator-groups/page.tsx` — replace the static stub with the server component that fetches `getOperatorGroupsState()` and renders `OperatorGroupsScreen`; remove the `operator-groups-stub` marker.
- **No-escalation gating (D4)** — group-grant UI offers only roles/tenants the current operator can grant (reuse the grantable-roles convention from `features/operators/`).
- `console-nav-config.ts` — drop the "TASK-PC-FE-225 stub" comment on the `/operator-groups` leaf (it is no longer a stub).
- Tests — vitest component/render tests for `OperatorGroupsScreen` (happy + degraded + permission-error), matching the coverage other IAM screens carry; a nav-render assertion that `/operator-groups` renders the real screen (retires the PC-FE-249 "only genuine stub" note for this route).

## Out of Scope

- Backend (BE-520). Inheritance UI (D2-B follow-up). Consumer-account grouping (D1-C).

---

# Acceptance Criteria

- [ ] **AC-1**: `/operator-groups` renders a real data-driven screen (groups list → detail with members + grants), no `operator-groups-stub` marker remains.
- [ ] **AC-2**: Full CRUD + membership add/remove + group-grant add/remove wired to the console-bff proxy → BE-520 `/api/admin/groups`; no browser-direct IAM call.
- [ ] **AC-3**: `getOperatorGroupsState()` handles the standard resilience waterfall (401 redirect, NO_ACTIVE_TENANT tenant gate, 403 inline permission error, 503/timeout degraded) — mirrors `getOrgHierarchyState()` / operators.
- [ ] **AC-4**: No-escalation gating — the grant UI exposes only grantable roles/tenants (grantable-roles convention); a non-grantable option is not offered.
- [ ] **AC-5**: `console-nav-config.ts` stale stub comment removed; vitest render tests for the screen (happy/degraded/permission-error) pass.
- [ ] **AC-6**: CI GREEN — `pnpm lint` + `tsc` + `vitest` (front-end unit lane authoritative for CI RED that tsc/vitest alone miss).

---

# Related Specs

- `projects/iam-platform/specs/services/admin-service/contracts/http/admin-api.md` (group endpoints, BE-519) — the contract this screen consumes
- `docs/adr/ADR-MONO-046-operator-group-model.md` § 4 step 3
- `projects/platform-console/docs/conventions/frontend-ui.md` (console UI conventions — canonical home)

# Related Contracts

- `/api/admin/groups` (+ `/{id}/members`, `/{id}/grants`) — BE-520 / BE-519 `admin-api.md`

---

# Edge Cases

- **Degraded card false-positive** — do not text-marker the render; the resilience waterfall must distinguish a real 503 (degraded card) from a permission error (inline) from happy. (Sibling lesson: console live-sweep text markers over-match degraded copy.)
- **No-escalation UI vs backend** — the UI gate is convenience; BE-520 is the real enforcement (403/422). The screen must handle a backend 403 on a grant the UI thought was allowed (races with a privilege revoke).
- **Worktree pnpm not populated on Windows** — FE verification runs against the main checkout's `node_modules` (worktree pnpm is not populated); verify lint/tsc/vitest via the main checkout junction.

---

# Failure Scenarios

- Screen renders but the BFF proxy is missing → browser-direct IAM call violates architecture.md § Forbidden Dependencies. Guard: AC-2.
- Text-marker render check → false "menu broken" (the known sweep false-positive). Guard: use typed states, AC-3.
- Grant UI offers non-grantable roles → user hits a backend 403 they can't understand. Guard: AC-4 grantable-roles gating.
- CI RED from a lint-only rule that tsc/vitest miss. Guard: AC-6 pnpm lint in the local verify.
