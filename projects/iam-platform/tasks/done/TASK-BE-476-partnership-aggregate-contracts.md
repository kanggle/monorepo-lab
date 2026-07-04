# TASK-BE-476 — cross-org partnership: specs/contracts (`tenant_partnership` aggregate + invite/accept + `delegated_scope` + cross-org confinement + cascade-revoke)

**Status:** done
**Area:** iam-platform / admin-service · **Scope:** `specs/services/admin-service/*` + `specs/contracts/http/admin-api.md` + `specs/contracts/events/partnership-events.md` (specs/contracts ONLY — no code)
**Type:** cross-org privilege-origination architecture — Source-of-Truth-first spec authoring (ADR-MONO-045 §3.4 step 1)
**Implemented:** branch `be-476-partnership-contracts` (pushed, PR pending — PR-on-request). specs/contracts only(코드 0): data-model(`tenant_partnership`+`tenant_partnership_participant`), rbac(`partnership.manage`+Cross-Org Partner Delegation Confinement 3번째 축), admin-api(Partnership Management 클러스터+전이 매트릭스), 신규 partnership-events.md(7 이벤트), 주변 정합(architecture/overview/retention/observability/dependencies). doc-lint: 신규 anchor·cross-ref 전부 resolve 확인.
**Depends on:** ADR-MONO-045 (ACCEPTED 2026-07-04 — the decision this task realizes as specs), ADR-MONO-024 (within-tenant delegation this extends across the org boundary — §D4-B additively amended), ADR-MONO-020 (`operator_tenant_assignment` grant substrate reused), ADR-MONO-023 (IAM↔entitlement plane separation — a partnership delegates IAM authority, not subscriptions)
**Analysis model:** Opus 4.8 · **Impl model:** Opus (cross-org privilege origination + attenuation cap + no-escalation-across-org confinement under HARDSTOP-04/09)

## Goal

ADR-MONO-045 is ACCEPTED. Its §3.4 execution roadmap **step 1** = author the **specs/contracts** for the cross-org partner-delegation model so step 2 (admin-service impl) has a Source-of-Truth to build against. This task writes NO code — it defines: (1) the `tenant_partnership` aggregate + its `tenant_partnership_participant` child (D1), (2) the invite/accept two-sided-consent contract (D2), (3) the `delegated_scope` shape (D3), (4) the cross-org confinement rule extending `AdminGrantScopeEvaluator` / the assume-tenant effective-scope (D3/D5), and (5) the relationship-scoped cascade-revoke semantics (D6). Every hard invariant from ADR-045 §3.1 is encoded in the specs so the impl cannot silently drift.

## 설계 판정 — the partnership is a MANAGED ENVELOPE, never an admin-scope widening

The load-bearing spec decision (from ADR-045 D3/D5): a cross-org partnership widens a partner operator's **domain-operating** reach (assume-tenant into the host, capped at `delegated_scope ∩ participant-scope ∩ host-holds`), and **never** its **administration** reach. A partner is a *scoped guest*, never a co-admin — so the D2 `effectiveAdminScope` (who may call `/api/admin/**` for which tenant) is **byte-unchanged** by partnerships: a cross-org actor has an EMPTY admin scope in the host. This keeps the third confinement axis (cross-org) cleanly separate from the two existing axes (D2 administration, BE-467 account-data) and makes "never A's `TENANT_ADMIN`/`SUPER_ADMIN`" structural, not a check that could be forgotten.

## Scope

**IN (specs/contracts only):**

- `specs/services/admin-service/data-model.md` — `### tenant_partnership` + `### tenant_partnership_participant` DDL blocks (fields, indexes, PK, 불변식 blockquotes, state enum); `## Migration Strategy` entries (`V00NN__create_partnership_tables.sql`, `V00NN__seed_partnership_manage_permission.sql`); `## Data Classification Summary` rows.
- `specs/services/admin-service/rbac.md` — `partnership.manage` permission key; Seed Roles + Seed Matrix rows (granted to `TENANT_ADMIN`, D2); new `### Cross-Org Partner Delegation Confinement (ADR-MONO-045 D3/D5)` subsection — the `delegated_scope ∩ participant ∩ host-holds` intersection pseudocode + ≤-own-across-org + no-transitive-re-delegation (confused-deputy default deny) + cascade-revoke + NET-ZERO + target-surface + extend the 축-구분 blockquote to **three** axes + explicit "admin scope UNCHANGED by partnership" statement.
- `specs/contracts/http/admin-api.md` — new `## Partnership Management (ADR-MONO-045)` cluster: state transition matrix (PENDING→ACTIVE→SUSPENDED/TERMINATED) + `POST /partnerships` (invite, D2) + `POST /partnerships/{id}:accept` (D2) + `POST /partnerships/{id}:suspend` / `:reactivate` / `:terminate` (D6, cascade) + `GET /partnerships` (list, both sides) + `POST|DELETE /partnerships/{id}/participants/{operatorId}` (D4). Placed between § Subscription Management and ## Common Error Format.
- `specs/contracts/events/partnership-events.md` (NEW) — `partnership.invited` / `accepted` / `suspended` / `reactivated` / `terminated` / `participant_added` / `participant_removed`; `terminated` carries the one-shot cascade note (D6).
- Peripheral consistency: `architecture.md` (outbox `partnership.*` topics + a Cross-Org Partner Delegation subsection), `overview.md` (owned state + related specs), `retention.md`, `observability.md`, `dependencies.md`.

**OUT (의도적 비범위 — deferred to ADR-045 §3.4 steps 2–4):**

- Any code / Flyway SQL / evaluator implementation (step 2, TASK-BE-xxx).
- The partner-console UI (step 3).
- N-way consortia (D1-C), `SUPER_ADMIN` broker gate (D2-C), partner-activity billing, ABAC per-resource cross-org data scoping, invite rate-limiting (ADR-045 §3.4 step 4 — all additive follow-ups).
- Any change to `SUPER_ADMIN`, ADR-024 within-tenant roles/confinement, ADR-020 assignment mechanics, or the assume-tenant token *contract* beyond documenting the partnership-derived effective-scope source (M1 single-tenant token preserved).

## Acceptance Criteria

- [ ] **AC-1** `data-model.md` declares `tenant_partnership` (`host_tenant_id`, `partner_tenant_id`, `status`, `delegated_scope` JSON, audit cols) with a `host≠partner` + `UNIQUE(host_tenant_id, partner_tenant_id)` 불변식, and `tenant_partnership_participant` (`partnership_id`, `operator_id`, optional participant-scope) — following the `admin_operator_roles` block shape (5-col table + indexes + PK + 불변식 blockquote).
- [ ] **AC-2** `delegated_scope` shape is specified as a bounded `{domains}×{roles}` set, with the explicit cap: never `TENANT_ADMIN`/`TENANT_BILLING_ADMIN`/`SUPER_ADMIN`/platform-scope, and never more than the host itself holds (≤-own extended across the org boundary).
- [ ] **AC-3** `rbac.md` adds `partnership.manage` (Permission Keys + Seed Roles + Seed Matrix, granted `TENANT_ADMIN`) and a `### Cross-Org Partner Delegation Confinement (ADR-MONO-045 D3/D5)` subsection: intersection pseudocode + ≤-own-across-org + no-transitive-re-delegation + cascade-revoke (request-time, fail-closed) + NET-ZERO + target surface. The subsection states that partnerships do NOT widen `effectiveAdminScope` (admin plane byte-unchanged) — the 축-구분 blockquote is extended to three axes.
- [ ] **AC-4** `admin-api.md` documents invite/accept/suspend/reactivate/terminate + participant add/remove, each with the house per-endpoint template (Auth/permission/roles, headers incl. `X-Operator-Reason`, request/response, errors table incl. `403 PARTNERSHIP_SCOPE_DENIED`/`409 …`, side effects incl. action codes + events), plus the state transition matrix.
- [ ] **AC-5** `partnership-events.md` follows `tenant-events.md` verbatim (standard envelope defer, per-event Topic/Schema version/Payload/필드 노트/Consumers, Consumer Rules); `partnership.terminated` documents the single one-shot cascade event (NOT N per-operator events).
- [ ] **AC-6** Two-sided consent (D2) is unambiguous in the contract: host `TENANT_ADMIN` invites with a bounded scope → `PENDING`; partner `TENANT_ADMIN` accepts → `ACTIVE`; either side may `:terminate`. Neither side can bind the other unilaterally.
- [ ] **AC-7** Doc-lint / cross-ref integrity: every new ADR/spec/contract link path resolves (5×`../` to root ADRs from admin-service specs; relative spec↔spec), every new column/rule/endpoint carries its `(ADR-MONO-045 Dx)` / `(TASK-BE-476)` tag; no collision with existing content (partnership/delegated_scope were greenfield).

## Related Specs

- `docs/adr/ADR-MONO-045-cross-org-partner-delegation.md` — the ACCEPTED decision (D1–D8) this task realizes; §3.4 step 1.
- `docs/adr/ADR-MONO-024-tenant-admin-delegation.md` — the within-tenant delegation model extended across the org boundary (§D4-B additively amended); `AdminGrantScopeEvaluator` / `effectiveAdminScope` / grant-menu ≤-own reused verbatim as the pattern.
- `specs/services/admin-service/rbac.md` — the confinement evaluator the cross-org cap extends (§ Permission Evaluation Algorithm).
- `rules/traits/multi-tenant.md` M1–M7 — row isolation the partnership must never weaken (single-`tenant_id` assumed token = M1).

## Related Contracts

- `specs/contracts/http/admin-api.md` § Subscription Management + § assign/unassign — the structural templates for the new partnership cluster.
- `specs/contracts/events/tenant-events.md` — the event-contract template.
- `specs/contracts/http/internal/auth-to-admin.md` (`/internal/operator-assignments/check`) — the assume-tenant effective-scope surface a partnership-derived host tenant additively feeds (documented, not re-contracted here; impl is step 2).

## Edge Cases

- **half-state (invited, not accepted)**: `PENDING` partnership derives ZERO access (no participant derivation until `ACTIVE`) — a B-operator cannot assume into A while the invite is unaccepted.
- **transitive re-delegation attempt**: B tries to re-delegate A's `delegated_scope` to a third org C → default deny (confused-deputy), mirroring ADR-024 within-tenant sub-delegation confinement; documented as a hard invariant, not an endpoint.
- **participant beyond delegated_scope**: B's admin tries to assign a participant a role outside `delegated_scope` → capped by intersection (the excess role simply does not derive); documented in the confinement rule.
- **host delegates a role it lacks**: invite carrying a role not in the host's own holdings → rejected at invite time (≤-own-across-org), `400`/`422`.
- **self-partnership**: `host_tenant_id == partner_tenant_id` → rejected (`host≠partner` 불변식).
- **duplicate**: a second ACTIVE/PENDING partnership for the same `(host, partner)` → `409 PARTNERSHIP_ALREADY_EXISTS`.

## Failure Scenarios

- **cross-org actor reaching admin plane**: a B-operator attempts `/api/admin/operators` in A → denied, because partnerships never widen `effectiveAdminScope` (empty admin scope cross-org); the confinement rule makes this structural.
- **stale access after termination**: partnership `:terminate` → the participant derivation is gone → next assume-tenant issuance / next admin request denies (request-time eval, bounded by the perm-cache TTL); no per-operator sweep needed.
- **partner suspends its own employee**: B offboards a participant via B's normal operator lifecycle → that operator's A-access disappears (participant derivation gone) with no A-side action — the offboarding-defect fix.
- **spec drift into code**: any invariant (attenuation cap, two-sided consent, no-transitive-re-delegation, cascade) missing from the specs would let step-2 impl bake it silently → this task's AC-3/AC-6 make each explicit.
