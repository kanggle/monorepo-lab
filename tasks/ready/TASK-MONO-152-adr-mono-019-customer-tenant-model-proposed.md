# Task ID

TASK-MONO-152

# Title

ADR-MONO-019 PROPOSED — platform-console Real Customer-Tenant Model (tenant ↔ domain subscription, decoupling tenant from product/domain)

# Status

ready

# Owner

monorepo (root tasks/ — shared `docs/adr/` cross-cutting architecture decision spanning GAP registry + console catalog + 5 domain gates)

# Task Tags

- adr
- architecture

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **origin**: user request "task/ADR 로 만들기" 2026-05-30 — after the AWS-IAM-Identity-Center-style multi-tenancy design summary, the user chose to make the offered redesign a **committed ADR/task** (not a chat-only explanation, not a hand-off prompt).
- **prerequisite for**: the post-ACCEPTED 4-step execution roadmap (ADR-MONO-019 § 3.3) — ACCEPTED transition task → step 1 model+catalog (GAP) → step 2 real customers (GAP) → step 3 per-domain dual-accept gate (5 domains + console-bff, Opus) → step 4 cleanup. **All PAUSED until ADR-MONO-019 ACCEPTED.**
- **orthogonal to**: ADR-005 (GAP) / TASK-BE-317 (service-to-service workload identity ① + ④). This task is the **tenant-model** axis (② central-IdP entitlement + ⑤ least-privilege). Shares no files; either may land first.
- **model**: 분석=Opus 4.7 / 구현=Opus 4.7 (single-session direct authoring) / 구현 권장=Opus 4.7 (cross-cutting architecture decision under HARDSTOP-04/09 — staged-child ADR governance precision).

---

# Goal

Author **ADR-MONO-019** (Status PROPOSED) resolving the six architecture decisions (D1-D6) for decoupling the **customer/tenant** axis from the **product/domain** axis in `platform-console`'s data-driven catalog — the production form of the "tenant == domain slug" demo simplification that ADR-MONO-013 § D5 left implicit. **Decision record + impact scope + migration roadmap only — no implementation in this task.**

The ACCEPTED transition and every execution step (subscription model, real-customer seed, per-domain isolation-gate dual-accept, cleanup) are **separate post-ACCEPTED tasks** (D6/D8 + § 3.3 roadmap), PAUSED until ACCEPTED.

---

# Background

The console tenant switcher currently lists **domain names** (`gap`/`wms`/`scm`/`erp`/`finance`), not customer names, because:

1. `ProductCatalog` binds each domain product to a `tenantSlug` equal to the domain name (`new Entry("wms", …, "wms", "/wms")`).
2. The demo DB seeds tenant rows whose ids **are** the domain names, so `ConsoleRegistryUseCase.selectableTenants()` resolves a domain product's `tenants[]` to `[<domain-name>]`.
3. Each domain's `TenantClaimValidator` enforces `tenant_id ∈ {<domain-slug>, *}` — a single fixed expected value, so a domain is effectively single-tenant.

This is a legitimate, ADR-MONO-013-recorded demo simplification — but NOT the production SaaS shape (customer subscribes to N products N:M; operator assigned to M customers; service trusts an IdP-issued tenant-scoped credential and isolates by row). Surfaced by the 5-identity-pattern assessment + the user's "왜 테넌트 선택창에 도메인 이름이 나오나" investigation. **Separate axis** from ADR-005 (GAP) / TASK-BE-317 (service-to-service workload identity).

Authoring any execution step without resolving D1-D6 bakes the tenant model silently (HARDSTOP-09 #2) and silently supersedes the ADR-MONO-013 demo-simplification assumption (HARDSTOP-04). Hence an ADR + staged PROPOSED → ACCEPTED, mirroring ADR-MONO-014/015/017/018.

---

# Scope

## In Scope (this task)

1. Author `docs/adr/ADR-MONO-019-platform-console-customer-tenant-model.md` (Status PROPOSED) with the six decisions:
   - **D1** — reuse the account-service `tenants` registry as the customer-tenant entity (semantic + seed; no new entity table).
   - **D2** — new `tenant_domain_subscription` N:M table in GAP account-service (entitlement authority).
   - **D3** — keep single-value `admin_operators.tenant_id` (+ `'*'`) for the MVP; multi-assignment join table deferred (least-privilege; D3-C rejected).
   - **D4** — subscription-driven `selectableTenants`; registry envelope shape byte-stable; zero `console-web` change.
   - **D5** — domain isolation gate evolves `tenant_id == slug` → entitlement-trust; highest-risk; dual-accept window.
   - **D6** — 4-step zero-regression migration (step 0 ACCEPTED → 1 backward-compatible model+catalog → 2 real customers → 3 per-domain dual-accept gate (Opus) → 4 cleanup).
2. Additive `ADR-MONO-013` § History "Additive note" blockquote recording the demo-simplification supersession (D1-D8 byte-unchanged — HARDSTOP-04; blockquote count 7 → 8).
3. Register this task in root `tasks/INDEX.md`.

## Out of Scope (post-ACCEPTED execution tasks — § 3.3 roadmap)

- The `tenant_domain_subscription` table / migration / seed (step 1).
- Real customer-tenant seed + operator assignments (step 2).
- Any domain `TenantClaimValidator` change or isolation IT (step 3).
- `ConsoleRegistryUseCase` / `ProductCatalog` rewrite + `console-integration-contract.md` changes (steps 1/4).
- `operator_tenant_assignment` N:M extension (step 4 / D3-B).
- The PROPOSED → ACCEPTED transition (separate doc-only task, D8).

---

# Acceptance Criteria

- [ ] **AC-1**: `docs/adr/ADR-MONO-019-platform-console-customer-tenant-model.md` exists, Status **PROPOSED**, with D1-D6 decision tables (each first row CHOSEN), § Consequences (hard invariants + what-it-does-NOT-do + § 3.3 roadmap), § Alternatives, § Relationship table, § Status Transition History, § Provenance.
- [ ] **AC-2**: Each D-axis records the chosen option **and** why each alternative was rejected (decision-record completeness).
- [ ] **AC-3**: The ADR records the **4-step zero-regression migration roadmap** with per-step model recommendation and the step-3 dual-accept window (BE-317 discipline).
- [ ] **AC-4**: The ADR amends ADR-MONO-013 **additively only** (a § History "Additive note" blockquote; D1-D8 bodies byte-unchanged — HARDSTOP-04 verifiable by `git diff origin/main -- docs/adr/ADR-MONO-013-*.md`).
- [ ] **AC-5**: The ADR states the orthogonality to ADR-005 (GAP) / TASK-BE-317 and the realization of ADR-MONO-018 § 3.3 ("new axis → new ADR").
- [ ] **AC-6**: multi-tenant M1-M7 explicitly recorded as **preserved** (the new gate widens the allowed-set under GAP's entitlement authority; no isolation layer dropped).
- [ ] **AC-7 (scope-lock)**: No implementation — no table, no migration, no seed, no validator change, no catalog code change, no contract rewrite. `git diff origin/main` touches only `docs/adr/ADR-MONO-019-*.md`, the ADR-013 additive note, this task file, and `tasks/INDEX.md`.
- [ ] **AC-8**: This task registered in root `tasks/INDEX.md`.

---

# Related Specs

- `docs/adr/ADR-MONO-013-platform-console-foundation.md` § D5 / § 1.2 (data-driven catalog demo simplification — superseded by ADR-019's production model).
- `docs/adr/ADR-MONO-017-platform-console-bff-architecture.md` § D6 (tenant pass-through — preserved).
- `docs/adr/ADR-MONO-018-platform-console-phase-8-federation-hardening.md` § D5 + § 3.3 (isolation regression cohort extended; "new axis → new ADR" anticipation realized).
- `projects/global-account-platform/docs/adr/ADR-001-oidc-adoption.md` + `ADR-002-admin-rbac.md` (central IdP + operator tenant scope).
- `rules/traits/multi-tenant.md` M1-M7 (isolation invariants).

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.2 (registry envelope — shape preserved, values change domain-slug → customer-id) + § 2.4.x (per-domain tenant model notes — updated at step 4, not now).
- `projects/global-account-platform/specs/contracts/http/console-registry-api.md` (catalog producer — unchanged at PROPOSED).

---

# Edge Cases

- **Backward-compat seed (step 1)**: the `tenant_domain_subscription` seed must make subscription ≡ the old `tenantSlug==domain` binding so step 1 is net-zero catalog change — verified by an IT asserting the registry response is byte-identical pre/post.
- **Intermediate catalog-visible-but-not-callable (step 2 before step 3)**: seeding real customers before the domain gate dual-accepts makes them catalog-visible but domain-rejected — must be either gated behind step 3 or shipped as a documented intermediate.
- **Dual-accept window (step 3)**: each domain must accept BOTH the legacy slug AND GAP-entitled customer ids during cutover; step 4 removes the legacy branch only after all 5 domains are on entitlement-trust.

---

# Failure Scenarios

- **Big-bang temptation**: flipping catalog + 5 domain gates + seed in one PR → transiently broken main across 5 domains (BE-303 CI-RED-at-merge lesson). Mitigated by D6 4-step phasing.
- **Entitlement-authority drift**: a domain caching its own subscriber list (D5-B) → stale lockout/admit. Mitigated by D5-A (GAP-issuance authority, domain isolates by row).
- **Least-privilege erosion**: deriving operator access from subscription (D3-C) → operator gains cross-customer reach. Mitigated by D3-A (explicit operator↔tenant grant).
- **Implicit supersession**: changing the catalog without recording the ADR-MONO-013 demo-simplification supersession → HARDSTOP-04 violation. Mitigated by AC-4 additive § History note.

---

# Verification

- 2026-05-30, `task/mono-152-customer-tenant-model-adr` branch (off origin/main; NOT on the BE-317 branch — orthogonal axis).
- ADR + ADR-013 additive note + INDEX registration applied. `git diff origin/main --stat` = `docs/adr/ADR-MONO-019-*.md` (new) + `docs/adr/ADR-MONO-013-*.md` (additive note) + this task file + `tasks/INDEX.md`.
- CI `changes` fast-lane: docs-only (`docs/adr/**.md` + `tasks/**.md`) = non-code path-filter → GREEN expected.
- BE-303 3-dim merge verification at close chore.
- 분석=Opus 4.7 / 구현=Opus 4.7.
