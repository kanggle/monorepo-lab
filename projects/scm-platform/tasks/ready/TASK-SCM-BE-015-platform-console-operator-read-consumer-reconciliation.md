# Task ID

TASK-SCM-BE-015

# Title

scm-platform — platform-console operator read-consumer spec reconciliation (ADR-MONO-013 Phase 4 prerequisite, spec-only)

# Status

ready

# Owner

backend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- api

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

- **prerequisite for**: `TASK-PC-FE-008` (platform-console, `projects/platform-console/tasks/backlog/`) — that task's `backlog → ready` move is **gated on this reconciliation being authored + merged**. This is the scm-side half declared in FE-008's Dependency Markers ("BLOCKED ON cross-project spec-first prerequisite").
- **governed by**: **ADR-MONO-013** (ACCEPTED 2026-05-16) § D6 **Phase 4** (wms/scm console sections) + § D1 (Model B — the console is the single UI; domains stay backend-only and are rendered by the console calling their existing gateway APIs). ADR-MONO-013 is the **authoritative monorepo-level decision** that platform-console federates scm; this task is its scm-side spec acknowledgment, **not a new decision**.
- **no scm ADR**: this is a **(B) document/accept** of an **already-existing** scm gateway capability (the `TenantClaimValidator` + `X-Token-Type` machinery already validates any GAP-issued RS256 token, human or machine, tenant-gated). No new convention is introduced, no scm auth model changes, no competing convention is selected → per the established meta-rule (document/accept needs no ADR; only normalize/hoist or competing-convention-selection does), **no scm-platform ADR is required**. ADR-MONO-013 is the governing authority; this task only reality-aligns the scm spec narrative with it.
- **spec-only**: no `apps/` code, no new OAuth client, no new gateway route, no auth-model change. Pure additive spec reconciliation (sibling pattern: TASK-SCM-BE-008/010/011/013/014 spec-only closures).

# Goal

Reconcile the scm-platform gateway spec narrative with **ADR-MONO-013 Model B reality** so that `platform-console` (a separate, ADR-MONO-013-governed project) can be a **sanctioned external operator read consumer** of scm's existing read surface — recorded **spec-first**, before `TASK-PC-FE-008` (the scm console section) proceeds.

The apparent tension this resolves (and why FE-008 was correctly gated on it):

- scm `gateway-public-routes.md` states *"scm v1 = backend only … Human-user (PKCE / authorization_code) flow is deferred to v2 when a frontend ships"*; `PROJECT.md`/`gap-integration.md` echo "v1 = backend only".
- `platform-console` (ADR-MONO-013 Model B) renders scm operator screens by calling scm's gateway **server-side with a human operator's GAP `platform-console-web` OIDC access token**.
- Consuming a producer surface whose own spec narrates it as "backend-only / human-flow v2-deferred" **without the producer spec acknowledging the consumer** violates spec-first discipline (CLAUDE.md: "Specs win over tasks. If implementation requires spec or contract changes, update them first").

The resolution is a **clarifying acknowledgment, not a capability or auth-model change**: scm's gateway *already* admits any GAP-issued RS256 token that is tenant-gated (`gap-integration.md` § Token 검증 #4 `TenantClaimValidator` → `tenant_id ∈ { scm, * }`) and *already* distinguishes human vs machine callers (`X-Token-Type` = `user | client_credentials`, Edge Case E1/E3). An operator's GAP token therefore satisfies the **existing** gateway contract as written. "v1 = backend only / human-flow deferred to v2" scopes **scm hosting its own frontend / registering its own `scm-platform-user-flow-client`** — it does **not** preclude an external, ADR-MONO-013-governed operator console consuming scm's read APIs. This task makes that scoping explicit in the scm spec.

# Scope

## In Scope (spec-only, additive)

- **`projects/scm-platform/specs/contracts/http/gateway-public-routes.md`** — in `## Authentication contract`, after the existing "scm v1 = backend only … deferred to v2" paragraph, add a subsection **`### platform-console operator read consumer (ADR-MONO-013 Model B)`**:
  - Scope-clarify the "backend-only / human-flow v2-deferred" statement = scm builds **no own frontend** and registers **no own `scm-platform-user-flow-client`**; it does **not** restrict authorized external API consumers.
  - `platform-console` is a **sanctioned external read consumer** of the existing **read** surface: `GET /api/v1/procurement/po`, `GET /api/v1/procurement/po/{poId}`, and the `inventory-visibility` reads (`/snapshot`, `/sku/{sku}`, `/staleness`, `/nodes`). It calls these **server-side** with a human operator's **GAP `platform-console-web` OIDC access token** (RS256, `tenant_id=scm` or SUPER_ADMIN `*`, surfaces as `X-Token-Type=user`), validated by the **existing** `TenantClaimValidator` + JWKS chain — **no new scm OAuth client, no new gateway code/route, no auth-model change**.
  - Constraints recorded: **read-only** (PO write `/submit|/confirm|/cancel` + procurement webhooks are **not** console-consumed — buyer/machine paths); scm remains **single-org** (the deliberate `multi-tenant` non-declaration in `PROJECT.md` is **unaffected** — tenant scoping stays the GAP-claim + existing producer-side gate); scm owns its contracts (consumer-only; `procurement-api.md`/`inventory-visibility-api.md` authoritative + unchanged).
  - Cross-ref: ADR-MONO-013 (governing), platform-console `console-integration-contract.md` § 2.4.6 (the consumer-side obligation, authored by TASK-PC-FE-008) + § 2.4.5 (FE-007 per-domain-credential rule the console reuses). Note the deferred `scm-platform-user-flow-client` (V0013 table) stays deferred and is **unrelated** (the console uses GAP's console client, not an scm client).
  - Bump frontmatter `last_updated: 2026-05-11 → 2026-05-19`.
- **`projects/scm-platform/specs/integration/gap-integration.md`** — add a short section **`## platform-console Operator Read Consumer (ADR-MONO-013)`** mirroring the OAuth-Clients/Edge-Case style: the console uses GAP's **own** `platform-console-web` OIDC token (NOT `scm-platform-internal-services-client`, NOT the deferred `scm-platform-user-flow-client`), `X-Token-Type=user`, read-only, tenant via the **existing** `TenantClaimValidator`; add a 참조 bullet to ADR-MONO-013 + the platform-console contract. (Add a 운영 체크리스트 line only if natural — no behavior checklist item, this is documentation.)
- **`projects/scm-platform/PROJECT.md`** — in `## GAP IdP Integration`, append one clarifying sentence to the "v1 = backend only. user-flow PKCE client 는 frontend 도입 시 별도 V slot." line: `platform-console` (ADR-MONO-013 Model B) is an **external operator read consumer** using GAP's console client; scm itself stays backend-only (no scm frontend, no scm user-flow client). **Frontmatter (domain/traits/service_types) MUST stay byte-unchanged** — scm deliberately excludes `multi-tenant`/`audit-heavy`; the console's traits are the console's, not scm's. Do **not** add a Service Map row (no new scm service).

## Out of Scope

- Any `apps/` / production code, any new OAuth client, any new gateway route or auth-model change (spec-only reality-alignment).
- Any scm ADR (governed by ADR-MONO-013; document/accept of an existing capability — see Dependency Markers).
- Mutating scm's `PROJECT.md` classification (domain/traits/service_types) — explicitly preserved byte-for-byte.
- The platform-console-side binding (`console-integration-contract.md` § 2.4.6, `features/scm-ops`) — that is `TASK-PC-FE-008` (platform-console project-internal), which this task only **unblocks**.
- scm `procurement-api.md` / `inventory-visibility-api.md` content (authoritative producers, unchanged — cross-referenced only).
- scm `admin-service` (v2-deferred — the console consumes only the v1-live procurement + inventory-visibility reads).

# Acceptance Criteria

- [ ] `gateway-public-routes.md` has the new `### platform-console operator read consumer (ADR-MONO-013 Model B)` subsection under `## Authentication contract`, accurately stating: existing-capability reuse (no new client/code/route/auth-model change), read-only scope, single-org preservation, the GAP `platform-console-web` token + `X-Token-Type=user` + existing `TenantClaimValidator` path, and the ADR-MONO-013 / console-contract cross-refs. Frontmatter `last_updated` = `2026-05-19`.
- [ ] `gap-integration.md` has the parallel `## platform-console Operator Read Consumer (ADR-MONO-013)` section + 참조 bullet; the console-uses-GAP's-own-client (not an scm client) point is explicit.
- [ ] `PROJECT.md` § GAP IdP Integration clarifying sentence added; **frontmatter byte-unchanged** (domain=`scm`, traits=`[transactional, integration-heavy, batch-heavy]`, service_types unchanged); no new Service Map row.
- [ ] The reconciliation is **document/accept only** — no scm auth model / code / OAuth client / route change anywhere; diff is purely additive spec prose (+ the one `last_updated` bump). No scm ADR created.
- [ ] Cross-references resolve (ADR-MONO-013 path; platform-console `console-integration-contract.md` path) — spec internal-link lint clean; `validate-rules` no new inconsistency; gateway-service `architecture.md` canonical Identity table + `### Service Type Composition` H3 untouched (this task does not edit architecture.md).
- [ ] Scope = `projects/scm-platform/` only (3 spec files + task lifecycle); no `apps/`, no shared-path, no platform-console file; no churn-clock effect.
- [ ] `TASK-PC-FE-008`'s scm-side prerequisite is satisfied (authored + this task merged → FE-008 may move `backlog → ready` once it **and** TASK-PC-FE-007 are merged).

# Related Specs

> Target project = `scm-platform`. Governing: monorepo `docs/adr/ADR-MONO-013-platform-console-foundation.md`. Follow `platform/entrypoint.md`; scm rule layers per `PROJECT.md` (domain `scm`, traits `[transactional, integration-heavy, batch-heavy]`).

- `docs/adr/ADR-MONO-013-platform-console-foundation.md` § D1 (Model B) / § D5 (integration contract) / § D6 (Phase 4 wms/scm) — the governing authority
- `projects/scm-platform/specs/contracts/http/gateway-public-routes.md` (edited — Authentication contract)
- `projects/scm-platform/specs/integration/gap-integration.md` (edited — token validation rules already admit GAP RS256 + `X-Token-Type` human/machine split: § Token 검증 #4, Edge Case E1/E3)
- `projects/scm-platform/specs/services/gateway-service/architecture.md` (read-only context — `TenantClaimValidator` `tenant_id ∈ {scm,*}`, `JwtHeaderEnrichmentFilter` `X-Token-Type`; **not edited**, canonical form preserved)
- `projects/scm-platform/PROJECT.md` (edited — § GAP IdP Integration clarifying sentence; frontmatter preserved)
- `projects/platform-console/specs/contracts/console-integration-contract.md` (consumer-side obligation — § 2.4.6 to be authored by TASK-PC-FE-008; § 2.4.5 FE-007 per-domain-credential rule, governing the GAP-token credential the console uses for scm)
- `projects/platform-console/tasks/backlog/TASK-PC-FE-008-console-scm-operations-section.md` (the dependent task this unblocks)

# Related Skills

- `.claude/skills/` — design-api / architect (spec reconciliation, cross-project consumer acknowledgment under a governing ADR; no code).

---

# Related Contracts

- **Changed (this task, spec-only additive)**: `gateway-public-routes.md` (new Authentication-contract subsection + `last_updated` bump), `gap-integration.md` (new consumer section + 참조), `PROJECT.md` (one clarifying sentence; frontmatter untouched).
- **Consumed/cross-referenced (unchanged, authoritative)**: scm `procurement-api.md` (PO read), `inventory-visibility-api.md` (read endpoints); GAP `platform-console-web` OIDC client (owned by GAP/ADR-MONO-013/014; not redefined here).
- **Not touched**: `gateway-service/architecture.md` (canonical form preserved), any `apps/` code, any OAuth seed migration.

---

# Target Service

- `scm-platform` / `gateway-service` (`rest-api`, edge gateway) — **spec-only**. The reconciliation documents an existing gateway capability (GAP RS256 token validation + tenant gate + `X-Token-Type`); no `gateway-service` code is changed.

---

# Architecture

- ADR-MONO-013 Model B: scm stays backend-only; `platform-console` (separate project) renders scm operator screens by calling scm's existing gateway read APIs server-side. This task records that consumer relationship spec-first on the scm side.
- The scm gateway's existing JWT chain (`AllowedIssuersValidator` + `TenantClaimValidator` `tenant_id ∈ {scm,*}` + `JwtHeaderEnrichmentFilter` `X-Token-Type`) already admits a human operator's GAP RS256 token — no architectural change, hence no scm ADR (document/accept under the governing ADR-MONO-013).
- scm classification (single-org; `multi-tenant`/`audit-heavy` deliberately excluded) is preserved — tenant scoping remains the GAP `tenant_id` claim enforced by the existing producer-side gate; the console's own multi-tenant/audit-heavy traits are the console's responsibility, not scm's.

---

# Implementation Notes

- Pure spec edit. Sibling precedent for spec-only scm closures: TASK-SCM-BE-008/010/011/013/014 (each ready→review→done, no `apps/`).
- Keep edits **additive**; the only non-additive change permitted is the `gateway-public-routes.md` frontmatter `last_updated` date bump. Do **not** reword existing normative auth rules — only add the clarifying consumer subsection/section.
- **Do not touch scm `PROJECT.md` frontmatter** (domain/traits/service_types) — adding `multi-tenant`/`audit-heavy` would be a classification change scm deliberately excluded; the console's traits ≠ scm's.
- **Do not edit `gateway-service/architecture.md`** — canonical Identity table + `### Service Type Composition` H3 (ADR-MONO-012 D3) stay byte-intact; the existing-capability statements there are referenced read-only.
- Verification = spec internal-link resolution + `validate-rules` no new inconsistency (no Docker/build — spec-only). scm `gateway-public-routes.md` frontmatter must stay well-formed (`status: live`, bumped `last_updated`, `owners`).
- Recommend implementation model: **Opus** (cross-project contract-reconciliation judgement under a governing ADR — interpretive: must NOT over-reach into a capability/auth change or an scm classification mutation; the "document/accept ≠ ADR" boundary call is the crux). Branch name must not contain the `master` substring.
- scm PR Separation Rule (stricter than platform-console): spec-authoring and impl must **not** share a PR. Lifecycle here: this file → `ready/` (spec-authoring commit) → spec edits + `ready/ → review/` (impl commit), separable into distinct PRs by the merger.

---

# Edge Cases

- An operator's GAP `platform-console-web` token carries `tenant_id` ≠ `scm` and the operator is not SUPER_ADMIN `*` → the **existing** `TenantClaimValidator` rejects it `403 TENANT_FORBIDDEN` (unchanged behavior; the console blocks the section client-side per FE-008, but scm enforcement is the authority).
- A reader infers "platform-console = scm's deferred v2 frontend / needs `scm-platform-user-flow-client`" → the new text must explicitly state the console is **GAP's** client (`platform-console-web`), separate project, ADR-MONO-013-governed; the scm user-flow client stays deferred and unrelated.
- A reader infers scm must become `multi-tenant`/`audit-heavy` to serve the console → the text must explicitly preserve scm single-org; tenant scoping is the GAP claim + existing gate; console traits are the console's.
- Future scm `admin-service` (v2) → out of scope; the console consumes only v1-live procurement + inventory-visibility reads (no scm admin-service dependency introduced).

# Failure Scenarios

- The reconciliation drifts into changing/relaxing an existing normative auth rule (e.g. weakening tenant enforcement) → wrong; this is document/accept only. AC pins "purely additive, no auth-model change".
- scm `PROJECT.md` frontmatter mutated (adding `multi-tenant`/`audit-heavy`) → classification scm deliberately excluded; AC pins frontmatter byte-unchanged.
- A new scm ADR is authored → unnecessary and wrong: ADR-MONO-013 governs; this is (B) document/accept of an existing capability (no competing convention). AC forbids an scm ADR.
- `gateway-service/architecture.md` edited / canonical form disturbed → out of scope; AC pins it untouched.
- The scm spec-authoring commit and the impl commit are bundled into one PR → violates scm INDEX PR Separation Rule; keep them separable commits (the merger may split into spec PR + impl PR).
- Implementation proceeds for `TASK-PC-FE-008` before this is merged → spec-first violation; FE-008's own Dependency Marker + AC gate it on this task being merged.

---

# Verification

- `grep`/link-check: ADR-MONO-013 relative path from each edited scm spec resolves; platform-console `console-integration-contract.md` relative path resolves.
- `validate-rules` (or the repo's rule-consistency scan) reports no new inconsistency introduced by the additions.
- `git diff` confirms: only `projects/scm-platform/specs/contracts/http/gateway-public-routes.md`, `projects/scm-platform/specs/integration/gap-integration.md`, `projects/scm-platform/PROJECT.md` (+ task lifecycle/INDEX) changed; additive only; scm `PROJECT.md` frontmatter and `gateway-service/architecture.md` byte-unchanged; no `apps/`, no shared path, no platform-console file.
- No Docker/build required (spec-only). CI markdown/path-filter expected to SKIP code jobs (sibling precedent: BE-008/010/011/013/014).

---

# Definition of Done

- [ ] `gateway-public-routes.md` consumer subsection + `last_updated` bump merged
- [ ] `gap-integration.md` consumer section + 참조 merged
- [ ] `PROJECT.md` clarifying sentence merged; frontmatter byte-unchanged
- [ ] Diff purely additive spec prose (+ 1 date bump); no code/client/route/auth-model change; no scm ADR; `architecture.md` untouched
- [ ] Cross-refs resolve; `validate-rules` clean; scope = scm-platform only
- [ ] `TASK-PC-FE-008` scm-side prerequisite satisfied (this merged) — recorded in FE-008 linkage when FE-008 is promoted
- [ ] Ready for review
