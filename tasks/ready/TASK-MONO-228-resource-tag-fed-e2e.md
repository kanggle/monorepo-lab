# Task ID

TASK-MONO-228

# Title

ADR-MONO-029 § D6 step 4 — iam admin `RESOURCE_TAG` access-condition federation-e2e proof (the deterministic one). Adds a federation-hardening-e2e spec proving the 3rd / final access-condition type (built by MONO-227 evaluator + BE-353 enforcement) bites end-to-end on the live stack: with the shared admin-service configured `ADMIN_ACCESS_RESOURCE_TAG_FORBIDDEN=protected`, a role mutation on a `protected`-tagged operator is gated (403 `ACCESS_CONDITION_UNMET`) while the SAME mutation on an untagged operator proceeds (200). The discriminant is the TARGET resource's tag (per-resource) so the gate is net-zero for every other operator — the deterministic federation proof the global-clock `TIME_WINDOW` (ADR-028) could not provide. Composes AND-only with the already-configured `SOURCE_IP`. Completes the closed condition enum end-to-end.

# Status

ready

# Owner

backend

# Task Tags

- e2e
- federation-hardening
- adr
- access-conditions
- security

---

# Dependency Markers

- **proves**: ADR-MONO-029 D2-A (aspect + `ResourceTagResolver`) + D3 (operators tagged `protected`, deny-if-present) — the runtime capstone of the § D6 roadmap (step 4, the deterministic federation proof).
- **depends on**: TASK-MONO-227 (`ResourceTagCondition` evaluator) + TASK-BE-353 (the iam enforcement: V0034 tags model + `OperatorResourceTagResolver` + aspect wiring + `ADMIN_ACCESS_RESOURCE_TAG_FORBIDDEN`).
- **builds on (harness)**: TASK-MONO-221 (the dedicated-tenant + admin-surface federation-e2e pattern + the `ip-pilot-corp` tenant the seed reuses).

# Goal

Make the `RESOURCE_TAG` access condition executable on the full federation stack and demonstrate the property ADR-029 promised: because the input is per-resource (the target operator's tag), the gate can be proven end-to-end deterministically AND net-zero (only the one tagged operator is affected), closing the gap ADR-028 § D6 recorded for the global-clock `TIME_WINDOW`.

# Scope

- `tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml` — add `ADMIN_ACCESS_RESOURCE_TAG_FORBIDDEN: "protected"` to `admin-service` (alongside the MONO-221 `SOURCE_IP` allowlist; they compose AND-only).
- `tests/federation-hardening-e2e/fixtures/seed.sql` § 17 — two throwaway target operators scoped to the dedicated `ip-pilot-corp` tenant: `rt-protected-target` (`tags='protected'`) + `rt-untagged-target` (no tags). No auth_db credential (objects only).
- NEW `tests/federation-hardening-e2e/specs/iam-admin-resource-tag-condition.spec.ts` — the proof (SUPER_ADMIN storageState; outbox warm-up gate + transient-5xx retry per MONO-207/210/221).
- `tests/federation-hardening-e2e/README.md` — post-MVP spec note.

**Spec design (serial; SUPER_ADMIN storageState; idempotent role mutation):**
- **gated**: `PATCH /api/admin/operators/rt-protected-target/roles` (set `[SUPPORT_READONLY]`) → 403 `ACCESS_CONDITION_UNMET`.
- **unaffected**: the SAME mutation on `rt-untagged-target` → 200.

# Acceptance Criteria

- **AC-1 (gated)** A role mutation on the `protected`-tagged operator → 403 with body `code = ACCESS_CONDITION_UNMET` (the RESOURCE_TAG gate fires; SOURCE_IP passes — the request's private docker remote addr is in the allowlist — so the 403 is specifically RESOURCE_TAG composed AND-only).
- **AC-2 (unaffected)** The SAME mutation on the untagged operator → 200 — the target's tag is the only discriminant.
- **AC-3 (suite-level net-zero)** Every pre-existing federation spec stays GREEN — enabling `RESOURCE_TAG` gates only the one tagged operator (per-resource), so no other operator's mutation is perturbed (unlike a global condition).
- **AC-4** GREEN on the federation-hardening-e2e workflow (nightly / `gh workflow run federation-hardening-e2e.yml`), all specs (the new spec parallel-safe with the existing cohort).

# Related Specs

- `docs/adr/ADR-MONO-029-resource-tag-access-condition.md` § D6 step 4 + § D2-A + § D3
- `docs/adr/ADR-MONO-018-platform-console-phase-8-federation-hardening.md` (harness location/scope)
- `platform/access-conditions.md` (the access-condition contract — § 1 `RESOURCE_TAG` implemented + the resolver-seam note)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` (the operator role mutation surface the spec drives)

# Edge Cases

- `rt-protected-target` / `rt-untagged-target` are referenced by NO other spec; the role mutation is idempotent (re-sets the seeded `SUPPORT_READONLY`), so no teardown is needed and the suite-default `retries: 2` is safe.
- **Per-resource net-zero**: `ADMIN_ACCESS_RESOURCE_TAG_FORBIDDEN=protected` gates ONLY operators carrying `protected`; no other seeded operator is tagged, so the existing specs' operator mutations (e.g. MONO-210's grant-menu on `deleg-target-umbrella`) resolve to an empty tag set → allowed → unaffected. This is the property TIME_WINDOW (global server clock) lacked.
- The `tags` column is V0034 (admin-service Flyway); the seed runs at Phase 1.5 (after Flyway), so it may set `tags='protected'`.
- SOURCE_IP is also configured (MONO-221); these requests carry no `X-Forwarded-For` → private remote addr → SOURCE_IP satisfied → the gated 403 is specifically RESOURCE_TAG (proving the 3-way AND-only composition live).
- The gate is orthogonal to RBAC (runs after grant) → it bites even SUPER_ADMIN ('*').

# Failure Scenarios

- If the gate read tags from the request, the proof would be meaningless — tags come from the trusted `admin_operators.tags` column (BE-353), seeded server-side.
- If `RESOURCE_TAG` were global (not per-resource), enabling it would gate every operator mutation and break the suite (the TIME_WINDOW hazard) — AC-3 (the whole workflow GREEN) confirms it is per-resource net-zero.
- If the resolver mis-classified the operator role path, the gate would not fire (or fire on the wrong endpoint) — AC-1/AC-2 (403 on tagged, 200 on untagged via the same path) guard it.
- If a future production admin migration reused V0034, the column would collide — V0034 is the next free admin-service version (after V0033).
