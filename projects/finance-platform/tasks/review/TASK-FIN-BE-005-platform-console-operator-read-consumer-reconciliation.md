# Task ID

TASK-FIN-BE-005

# Title

finance-platform — platform-console operator read-consumer spec reconciliation (ADR-MONO-013 Phase 5 prerequisite, spec-only)

# Status

review

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

- **prerequisite for**: `TASK-PC-FE-009` (platform-console, `projects/platform-console/tasks/backlog/`) — that task's `backlog → ready` move is **gated on this reconciliation being authored + merged**. This is the finance-side half declared in FE-009's Dependency Markers ("BLOCKED ON cross-project spec-first prerequisite"). Sibling precedent: `TASK-SCM-BE-015` ⊃ `TASK-PC-FE-008` (scm Phase 4 slice 2); this is the **Phase 5** analog.
- **governed by**: **ADR-MONO-013** (ACCEPTED 2026-05-16) § D6 **Phase 5** (finance console section, "built to the proven contract" — governed by ADR-MONO-008, **not re-decided here**) + § D1 (Model B — the console is the single UI; domains stay backend-only and are rendered by the console calling their existing read APIs) + § 3.3 (finance is backend-only; the console renders it — `finance-platform` correctly declares **no** `frontend-app` service_type). ADR-MONO-013 is the **authoritative monorepo-level decision** that platform-console federates finance; this task is its finance-side spec acknowledgment, **not a new decision**. finance domain governance stays **ADR-MONO-008** (untouched here).
- **no finance ADR**: this is a **(B) document/accept** of an **already-existing** finance JWT-validation capability (the `AllowedIssuersValidator` + `TenantClaimValidator` chain already validates any GAP-issued RS256 token — human or machine — and `tenant_id ∈ { finance, * }`-gates it; `X-Token-Type` (`user | client_credentials`) already distinguishes the caller). No new convention is introduced, no finance auth model changes, no new OAuth client / route / code, no competing convention is selected → per the established meta-rule (document/accept needs no ADR; only normalize/hoist or competing-convention-selection does), **no finance-platform ADR is required**. ADR-MONO-013 is the governing authority; this task only reality-aligns the finance spec narrative with it. (Identical reasoning to TASK-SCM-BE-015's "no scm ADR".)
- **spec-only**: no `apps/` code, no new OAuth client, no new gateway/service route, no auth-model change. Pure additive spec reconciliation (sibling pattern: scm spec-only closures TASK-SCM-BE-008/010/011/013/014/015).
- **finance-shape difference vs the scm precedent (recorded honestly, not silently)**: TASK-SCM-BE-015 edited **3** spec files because scm has a dedicated `gateway-public-routes.md` whose "v1 backend-only / human-flow v2-deferred" prose was the primary tension. finance has **no `gateway-public-routes.md`** (finance `gateway-service` is v1-deferred per `PROJECT.md` Service Map; the v1 auth contract narrative lives in `gap-integration.md` + the `account-api.md` auth preamble + `account-service/architecture.md` § Multi-tenancy/Security). This task therefore edits **2** files (`gap-integration.md` + `PROJECT.md`), mirroring the *subset* of SCM-BE-015's edits that finance actually has. `account-api.md` and `account-service/architecture.md` are **authoritative / canonical and left byte-unchanged** (the exact discipline SCM-BE-015 applied to `procurement-api.md` / `inventory-visibility-api.md` / `gateway-service/architecture.md`).

# Goal

Reconcile the finance-platform GAP-integration spec narrative with **ADR-MONO-013 Model B reality** so that `platform-console` (a separate, ADR-MONO-013-governed project) can be a **sanctioned external operator read consumer** of finance's existing read surface — recorded **spec-first**, before `TASK-PC-FE-009` (the finance console section) proceeds.

The apparent tension this resolves (and why FE-009 is correctly gated on it):

- finance `gap-integration.md` states *"v1 = backend only — self-service signup endpoint 사용 안 함"*; `PROJECT.md` § GAP IdP Integration / § v1 IN-OUT echo "v1 = backend only … frontend 는 통합 platform console 이 렌더". The only registered v1 OAuth client is `finance-platform-internal-services-client` (`client_credentials`); the human user-flow client (`finance-platform-user-flow-client`, `authorization_code`+PKCE) is explicitly **v2 DEFERRED**.
- `platform-console` (ADR-MONO-013 Model B) renders finance operator screens by calling finance's read API **server-side with a human operator's GAP `platform-console-web` OIDC access token** (RS256), **not** an scm/finance client-credentials token and **not** the deferred `finance-platform-user-flow-client`.
- Consuming a producer surface whose own spec narrates it as "backend-only / human-flow v2-deferred" **without the producer spec acknowledging the consumer** violates spec-first discipline (CLAUDE.md: "Specs win over tasks. If implementation requires spec or contract changes, update them first"). This is the concrete instance of the ADR-MONO-013 § 3.3 "zero retrofit unverified" risk — Phase 5 resolves it honestly, exactly as Phase 4 did for scm.

The resolution is a **clarifying acknowledgment, not a capability or auth-model change**: finance's JWT validator chain *already* admits any GAP-issued RS256 token that is `tenant_id ∈ { finance, * }`-gated (`gap-integration.md` § Token 검증 규칙 #1/#3/#4: `AllowedIssuersValidator` + `TenantClaimValidator`) and *already* distinguishes human vs machine callers (`X-Token-Type` = `user | client_credentials`, § OAuth Clients Edge Case). An operator's GAP token therefore satisfies the **existing** finance contract as written. "v1 = backend only / user-flow client v2-deferred" scopes **finance hosting its own frontend / registering its own `finance-platform-user-flow-client`** — it does **not** preclude an external, ADR-MONO-013-governed operator console consuming finance's **read** APIs with GAP's own console client. This task makes that scoping explicit in the finance spec.

# Scope

## In Scope (spec-only, additive)

- **`projects/finance-platform/specs/integration/gap-integration.md`** — add a new section **`## platform-console Operator Read Consumer (ADR-MONO-013)`** (placed after `## Token 검증 규칙`, before `## Error Responses`, mirroring the SCM-BE-015 `gap-integration.md` edit shape):
  - `platform-console` is a **sanctioned external read consumer** of the existing finance **read** surface: `GET /api/finance/accounts/{id}` (account + balances), `GET /api/finance/accounts/{id}/balances`, `GET /api/finance/accounts/{id}/transactions` (paginated). It calls these **server-side** with a human operator's **GAP `platform-console-web` OIDC access token** (RS256, `tenant_id=finance` or SUPER_ADMIN `*`, surfaces as `X-Token-Type=user`), validated by the **existing** `AllowedIssuersValidator` + `TenantClaimValidator` + JWKS chain — **no new finance OAuth client, no new gateway/service code/route, no auth-model change**. It uses **GAP's own** `platform-console-web` client (owned by GAP / ADR-MONO-013 / ADR-MONO-014), **NOT** `finance-platform-internal-services-client` and **NOT** the deferred `finance-platform-user-flow-client` (the latter stays deferred and is **unrelated** — the console uses GAP's console client, not a finance client).
  - Constraints recorded:
    - **Read-only** — the finance **write/mutation** surface (`POST /accounts`, `/kyc/upgrade`, `/holds`, `/holds/{holdId}/capture`, `/holds/{holdId}/release`, `/transfers`) is **not** console-consumed. Those are domain fund-movement / operator-domain mutations requiring `Idempotency-Key` (fintech F1) — **not** an operator-parity console surface at v1 (finance has no `admin-service`; the v2 `admin-service` reconciliation queue / KYC-hold review is a **v2-deferred** surface, ADR-MONO-008 § D3 / `PROJECT.md` v2 Service Map). Read-only, like the scm precedent excluded PO writes.
    - finance stays **single-org** — the deliberate `multi-tenant` non-declaration in `PROJECT.md` (§ Out of Scope) is **unaffected**: tenant scoping stays the GAP-claim + the existing producer-side `TenantClaimValidator` gate; the console's own `multi-tenant`/`integration-heavy`/`audit-heavy` traits are the **console's** responsibility, not finance's.
    - finance owns its contracts (consumer-only; `account-api.md` authoritative + **unchanged** — request/response/error tables canonical there).
    - **fintech producer obligations the consumer must honour (recorded as the spec-first basis FE-009 will bind to)**: the F5 money shape (`{ amount: "<string-integer-minor-units>", currency }`, per-currency scale, **never** float) is the producer's wire contract — the console must render it faithfully (no float coercion / precision loss); finance is `data_sensitivity: confidential` + F7 (PII / regulated identifiers masked producer-side) — the console must not log balances / transactions / account refs; regulated account/KYC/transaction states (`PENDING_KYC|ACTIVE|RESTRICTED|FROZEN|CLOSED`, `FAILED|REVERSED`, sanction-driven) are surfaced honestly. These are **producer-authoritative facts cross-referenced here**, not new finance requirements.
  - Cross-ref: ADR-MONO-013 (governing) + ADR-MONO-008 (finance domain governance, unchanged) + platform-console `console-integration-contract.md` § 2.4.7 (the consumer-side obligation, to be authored by TASK-PC-FE-009) + § 2.4.5/§ 2.4.6 (the FE-007/FE-008 per-domain-credential rule the console reuses for finance). Add a 참조 bullet + a 운영 체크리스트 line only if natural (documentation, no behavior checklist item).
- **`projects/finance-platform/PROJECT.md`** — in `## GAP IdP Integration`, append one clarifying sentence after the existing "`v1 = backend only. user-flow PKCE client 는 별도 V slot (v1 미발행 — 콘솔이 GAP public client 로 렌더, ADR-MONO-013).`" line: `platform-console` (ADR-MONO-013 Model B) is an **external operator read consumer** using GAP's own `platform-console-web` console client; finance itself stays backend-only (no finance frontend, no finance user-flow client; the deferred `finance-platform-user-flow-client` is unrelated). **Frontmatter (domain/traits/service_types) MUST stay byte-unchanged** — finance deliberately excludes `multi-tenant` (and `integration-heavy`); the console's traits are the console's, not finance's. Do **not** add a Service Map row (no new finance service). Optionally add the same one-sentence clarification to the `## v1 IN / OUT slice` "frontend — 통합 platform console 이 렌더" OUT bullet **only if** it reads as a dangling claim without it (keep minimal/additive).

## Out of Scope

- Any `apps/` / production code, any new OAuth client, any new gateway/service route or auth-model change (spec-only reality-alignment).
- Any finance ADR (governed by ADR-MONO-013; document/accept of an existing capability — see Dependency Markers). finance domain governance remains ADR-MONO-008, untouched.
- Mutating finance's `PROJECT.md` classification (domain/traits/service_types) — explicitly preserved byte-for-byte. Adding `multi-tenant`/`integration-heavy` would be a classification change finance deliberately excluded.
- Editing `account-api.md` or `account-service/architecture.md` (authoritative producer / canonical ADR-MONO-012 form — cross-referenced, unchanged; the exact discipline SCM-BE-015 applied to its producer + architecture specs).
- The platform-console-side binding (`console-integration-contract.md` § 2.4.7, `features/finance-ops`) — that is `TASK-PC-FE-009` (platform-console project-internal), which this task only **unblocks**.
- finance `gateway-service` (v1-deferred — the console consumes the v1-live account-service `/api/finance/**` reads directly; when the gateway is introduced it rewrites `/api/v1/finance/**`, transparent to the console contract).
- The finance write/mutation surface and the v2 `admin-service` operator surface (reconciliation queue / KYC review / limits) — v2-deferred (ADR-MONO-008 § D3); the console consumes only the v1-live account read endpoints.

# Acceptance Criteria

- [ ] `gap-integration.md` has the new `## platform-console Operator Read Consumer (ADR-MONO-013)` section, accurately stating: existing-capability reuse (no new client/code/route/auth-model change), read-only scope (the enumerated `GET` reads; write/mutation + v2 admin-service excluded), single-org preservation, the GAP `platform-console-web` token + `X-Token-Type=user` + existing `AllowedIssuersValidator`/`TenantClaimValidator` path, the console-uses-GAP's-own-client (not `finance-platform-internal-services-client`, not the deferred `finance-platform-user-flow-client`) point made explicit, the cross-referenced fintech producer obligations (F5 money shape / confidential+F7 / honest regulated-state surfacing), and the ADR-MONO-013 / ADR-MONO-008 / console-contract cross-refs.
- [ ] `PROJECT.md` § GAP IdP Integration clarifying sentence added; **frontmatter byte-unchanged** (domain=`fintech`, traits=`[transactional, regulated, audit-heavy]`, service_types=`[rest-api, event-consumer]`); no new Service Map row; (optional) the § v1 IN/OUT frontend OUT bullet clarified only if minimal/additive.
- [ ] The reconciliation is **document/accept only** — no finance auth model / code / OAuth client / route change anywhere; diff is purely additive spec prose. No finance ADR created. `account-api.md` and `account-service/architecture.md` byte-unchanged.
- [ ] Cross-references resolve (ADR-MONO-013 path; ADR-MONO-008 path; platform-console `console-integration-contract.md` path) — spec internal-link lint clean; `validate-rules` no new inconsistency; `account-service/architecture.md` canonical Identity table + `### Service Type Composition` H3 untouched (this task does not edit architecture.md).
- [ ] Scope = `projects/finance-platform/` only (2 spec files + task lifecycle/INDEX); no `apps/`, no shared-path, no platform-console file; no churn-clock effect.
- [ ] `TASK-PC-FE-009`'s finance-side prerequisite is satisfied (authored + this task merged → FE-009 may move `backlog → ready`).

# Related Specs

> Target project = `finance-platform`. Governing: monorepo `docs/adr/ADR-MONO-013-platform-console-foundation.md` (+ `ADR-MONO-008` for finance domain governance, unchanged). Follow `platform/entrypoint.md`; finance rule layers per `PROJECT.md` (domain `fintech`, traits `[transactional, regulated, audit-heavy]`).

- `docs/adr/ADR-MONO-013-platform-console-foundation.md` § D1 (Model B) / § D5 (integration contract) / § D6 (Phase 5 finance) / § 3.3 (backend-only; console renders finance) — the governing authority
- `docs/adr/ADR-MONO-008-finance-platform-bootstrap.md` — finance domain governance (unchanged; cross-ref only — Phase 5 is "built to the proven contract", not a finance re-decision)
- `projects/finance-platform/specs/integration/gap-integration.md` (edited — new consumer section; the existing § Token 검증 규칙 #1/#3/#4 + § OAuth Clients `X-Token-Type` Edge Case already admit a GAP RS256 human token, tenant-gated)
- `projects/finance-platform/PROJECT.md` (edited — § GAP IdP Integration clarifying sentence; frontmatter preserved)
- `projects/finance-platform/specs/contracts/http/account-api.md` (authoritative producer — read surface `GET /accounts/{id}` · `/balances` · `/transactions`, F5 money shape, flat error envelope; **not edited**, cross-referenced)
- `projects/finance-platform/specs/services/account-service/architecture.md` (read-only context — § Multi-tenancy `TenantClaimValidator` `tenant_id ∈ {finance,*}`, § Security RS256/`AllowedIssuersValidator`, § REST endpoints v1 GET reads; **not edited**, canonical form preserved)
- `projects/platform-console/specs/contracts/console-integration-contract.md` (consumer-side obligation — § 2.4.7 to be authored by TASK-PC-FE-009; § 2.4.5/§ 2.4.6 the FE-007/FE-008 per-domain-credential rule governing the GAP-token credential the console uses for finance)
- `projects/scm-platform/tasks/done/TASK-SCM-BE-015-platform-console-operator-read-consumer-reconciliation.md` (the Phase 4 scm precedent this mirrors — same document/accept discipline)
- `projects/platform-console/tasks/backlog/TASK-PC-FE-009-console-finance-operations-section.md` (the dependent task this unblocks)

# Related Skills

- `.claude/skills/` — design-api / architect (spec reconciliation, cross-project consumer acknowledgment under a governing ADR; no code; the "document/accept ≠ ADR" boundary call).

---

# Related Contracts

- **Changed (this task, spec-only additive)**: `gap-integration.md` (new `## platform-console Operator Read Consumer (ADR-MONO-013)` section + 참조), `PROJECT.md` (one clarifying sentence; frontmatter untouched).
- **Consumed/cross-referenced (unchanged, authoritative)**: finance `account-api.md` (`GET /accounts/{id}` · `/balances` · `/transactions`, F5 money shape, flat envelope); GAP `platform-console-web` OIDC client (owned by GAP / ADR-MONO-013 / ADR-MONO-014; not redefined here).
- **Not touched**: `account-service/architecture.md` (canonical ADR-MONO-012 form preserved), any `apps/` code, any OAuth seed migration, any finance ADR.

---

# Target Service

- `finance-platform` / `account-service` (`rest-api`) — **spec-only**. The reconciliation documents an existing JWT-validation capability (GAP RS256 token validation + `tenant_id ∈ {finance,*}` gate + `X-Token-Type`); no `account-service` (or the v1-deferred `gateway-service`) code is changed.

---

# Architecture

- ADR-MONO-013 Model B: finance stays backend-only (correctly **no** `frontend-app` service_type, § 3.3); `platform-console` (separate project) renders finance operator screens by calling finance's existing read APIs server-side. This task records that consumer relationship spec-first on the finance side.
- The finance JWT chain (`AllowedIssuersValidator` SAS + legacy issuer window + `TenantClaimValidator` `tenant_id ∈ {finance,*}` + the `X-Token-Type` human/machine split) already admits a human operator's GAP RS256 token — no architectural change, hence no finance ADR (document/accept under the governing ADR-MONO-013; finance domain governance stays ADR-MONO-008).
- finance classification (single-org; `multi-tenant`/`integration-heavy` deliberately excluded) is preserved — tenant scoping remains the GAP `tenant_id` claim enforced by the existing producer-side gate; the console's own multi-tenant/integration-heavy/audit-heavy traits are the console's responsibility, not finance's.

---

# Implementation Notes

- Pure spec edit. Sibling precedent for spec-only finance/scm closures: TASK-SCM-BE-015 (the direct Phase 4 analog) + scm-platform spec-only closures TASK-SCM-BE-008/010/011/013/014.
- Keep edits **additive**; do **not** reword existing normative auth rules (§ Token 검증 규칙, § Error Responses) — only add the clarifying consumer section + the one `PROJECT.md` sentence (+ optional minimal IN/OUT clarification). No `last_updated`-style frontmatter bump exists on these finance docs (unlike scm's `gateway-public-routes.md` which carried one) — do not invent one; keep the diff to the additive prose.
- **Do not touch finance `PROJECT.md` frontmatter** (domain/traits/service_types) — adding `multi-tenant`/`integration-heavy` would be a classification change finance deliberately excluded; the console's traits ≠ finance's.
- **Do not edit `account-service/architecture.md`** — canonical Identity table + `### Service Type Composition` H3 (ADR-MONO-012 D3) stay byte-intact; the existing-capability statements there are referenced read-only.
- **Do not edit `account-api.md`** — authoritative producer; the F5 money shape / flat error envelope / read endpoints are canonical there and only cross-referenced.
- Verification = spec internal-link resolution + `validate-rules` no new inconsistency (no Docker/build — spec-only).
- Recommend implementation model: **Opus** (cross-project contract-reconciliation judgement under a governing ADR — interpretive: must NOT over-reach into a capability/auth change or a finance classification mutation; the "document/accept ≠ ADR" boundary call is the crux; the finance-shape difference vs the scm precedent — 2 files not 3, no `last_updated` bump — must be reasoned, not blindly copied). Branch name must not contain the `master` substring.
- finance PR Separation Rule (stricter than platform-console — finance `tasks/INDEX.md`: *"Never bundle task spec authoring with implementation in the same PR."*): spec-authoring and impl must **not** share a PR. Lifecycle: this file → `ready/` (spec-authoring commit/PR) → spec edits + `ready/ → review/` (impl commit/PR) → `review/ → done/` (close chore PR).

---

# Edge Cases

- An operator's GAP `platform-console-web` token carries `tenant_id` ≠ `finance` and the operator is not SUPER_ADMIN `*` → the **existing** `TenantClaimValidator` rejects it `403 TENANT_FORBIDDEN` (unchanged behavior; the console blocks the section client-side per FE-009, but finance enforcement is the authority).
- A reader infers "platform-console = finance's deferred v2 frontend / needs `finance-platform-user-flow-client`" → the new text must explicitly state the console is **GAP's** client (`platform-console-web`), a separate ADR-MONO-013-governed project; the finance user-flow client stays deferred and unrelated.
- A reader infers finance must become `multi-tenant`/`integration-heavy` to serve the console → the text must explicitly preserve finance single-org; tenant scoping is the GAP claim + existing gate; console traits are the console's.
- A reader infers the console may call finance **write** endpoints (kyc/upgrade, holds, transfers) → the text must explicitly scope the console to **read-only**; finance write + the v2 `admin-service` operator surface are not console-consumed at v1.
- Future finance `gateway-service` (v1-deferred) or v2 `admin-service` → out of scope; the console consumes only the v1-live account-service read surface; the gateway, when introduced, only rewrites the path prefix (transparent).

# Failure Scenarios

- The reconciliation drifts into changing/relaxing an existing normative auth rule (e.g. weakening tenant enforcement, admitting the write surface) → wrong; this is document/accept only. AC pins "purely additive, no auth-model change, read-only".
- finance `PROJECT.md` frontmatter mutated (adding `multi-tenant`/`integration-heavy`) → classification finance deliberately excluded; AC pins frontmatter byte-unchanged.
- A new finance ADR is authored, or ADR-MONO-008 is edited → unnecessary and wrong: ADR-MONO-013 governs; this is (B) document/accept of an existing capability (no competing convention). AC forbids a finance ADR; ADR-MONO-008 is cross-ref only.
- `account-service/architecture.md` or `account-api.md` edited / canonical form disturbed → out of scope; AC pins them untouched.
- A `last_updated` / frontmatter date bump is invented on `gap-integration.md` (cargo-culted from the scm `gateway-public-routes.md` precedent which legitimately had one) → wrong; these finance docs carry no such field. AC/Notes pin the diff to additive prose only.
- The scm-shaped 3-file edit is force-fit onto finance (e.g. fabricating a finance `gateway-public-routes.md`) → wrong; finance's v1 gateway is deferred and has no such spec. The honest finance shape is the 2-file subset (`gap-integration.md` + `PROJECT.md`) — recorded in Dependency Markers.
- The finance spec-authoring commit and the impl commit are bundled into one PR → violates finance INDEX PR Separation Rule ("Never bundle task spec authoring with implementation in the same PR"); keep them distinct PRs.
- Implementation proceeds for `TASK-PC-FE-009` before this is merged → spec-first violation; FE-009's own Dependency Marker + AC gate it on this task being merged.

---

# Verification

- `grep`/link-check: ADR-MONO-013 + ADR-MONO-008 relative paths from each edited finance spec resolve; platform-console `console-integration-contract.md` relative path resolves.
- `validate-rules` (or the repo's rule-consistency scan) reports no new inconsistency introduced by the additions.
- `git diff` confirms: only `projects/finance-platform/specs/integration/gap-integration.md`, `projects/finance-platform/PROJECT.md` (+ task lifecycle/INDEX) changed; additive only; finance `PROJECT.md` frontmatter and `account-service/architecture.md` + `account-api.md` byte-unchanged; no `apps/`, no shared path, no platform-console file; no `last_updated` invention.
- No Docker/build required (spec-only). CI markdown/path-filter expected to SKIP code jobs (sibling precedent: SCM-BE-015 + scm BE-008/010/011/013/014).

---

# Definition of Done

- [ ] `gap-integration.md` `## platform-console Operator Read Consumer (ADR-MONO-013)` section + 참조 merged
- [ ] `PROJECT.md` clarifying sentence merged; frontmatter byte-unchanged; no Service Map row; (optional) IN/OUT clarification minimal
- [ ] Diff purely additive spec prose; no code/client/route/auth-model change; no finance ADR; ADR-MONO-008 untouched; `architecture.md` + `account-api.md` untouched; no `last_updated` invention
- [ ] Cross-refs resolve; `validate-rules` clean; scope = finance-platform only (2 files)
- [ ] `TASK-PC-FE-009` finance-side prerequisite satisfied (this merged) — recorded in FE-009 linkage when FE-009 is promoted `backlog → ready`
- [ ] Ready for review
