# Task ID

TASK-ERP-BE-002

# Title

erp-platform — platform-console operator read-consumer spec reconciliation (ADR-MONO-013 Phase 6 prerequisite, spec-only)

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

- **prerequisite for**: `TASK-PC-FE-010` (platform-console, `projects/platform-console/tasks/backlog/`) — that task's `backlog → ready` move is **gated on this reconciliation being authored + merged**. This is the erp-side half declared in FE-010's Dependency Markers ("BLOCKED ON cross-project spec-first prerequisite"). Sibling precedent: `TASK-FIN-BE-005` ⊃ `TASK-PC-FE-009` (finance Phase 5); this is the **Phase 6** analog.
- **governed by**: **ADR-MONO-013** (ACCEPTED 2026-05-16) § D6 **Phase 6** (erp console section, "governed by future erp ADR" — that future ADR is **ADR-MONO-016** (ACCEPTED 2026-05-19), **not re-decided here**) + § D1 (Model B — the console is the single UI; domains stay backend-only and are rendered by the console calling their existing read APIs) + § 3.3 (erp is backend-only; the console renders it — `erp-platform` correctly declares **no** `frontend-app` service_type, matching ADR-MONO-016 § D3.1 "platform-console parity-slice as binding UI decision"). ADR-MONO-013 is the **authoritative monorepo-level decision** that platform-console federates erp; this task is its erp-side spec acknowledgment, **not a new decision**. erp domain governance stays **ADR-MONO-016** (untouched here).
- **no erp ADR**: this is a **(B) document/accept** of an **already-existing** erp JWT-validation capability (the GAP RS256 + JWKS chain in `gap-integration.md` § Token 검증 규칙 #1/#3/#4 already validates any GAP-issued RS256 token — human or machine — and the `tenant_id ∈ { erp, * }` gate already restricts it; the `X-Token-Type` (`user | client_credentials`) header distinguishes the caller). No new convention is introduced, no erp auth model changes, no new OAuth client / route / code, no competing convention is selected → per the established meta-rule (document/accept needs no ADR; only normalize/hoist or competing-convention-selection does), **no erp-platform ADR is required**. ADR-MONO-013 is the governing authority; this task only reality-aligns the erp spec narrative with it. (Identical reasoning to TASK-SCM-BE-015 / TASK-FIN-BE-005 "no scm/finance ADR".)
- **spec-only**: no `apps/` code, no new OAuth client, no new gateway/service route, no auth-model change. Pure additive spec reconciliation (sibling pattern: scm spec-only closures, finance FIN-BE-005).
- **erp-shape difference vs the scm/finance precedents (recorded honestly, not silently)**: TASK-SCM-BE-015 edited **3** spec files because scm has a dedicated `gateway-public-routes.md`. TASK-FIN-BE-005 edited **2** spec files because finance has **no `gateway-public-routes.md`** (finance `gateway-service` is v1-deferred). erp is identical to finance on this axis — erp's gateway is v1-IN per `PROJECT.md` § v1 IN slice (*"`gateway-service` 엣지 라우팅 (masterdata-service 활성화와 함께)"*) but **the gateway architecture spec/route catalog has not been authored** in this monorepo's `projects/erp-platform/specs/services/` (only `masterdata-service/architecture.md` exists; `gateway-service` is v1-IN by `PROJECT.md` declaration but architecturally undefined in specs). This task therefore edits **2** files (`gap-integration.md` + `PROJECT.md`), mirroring the **finance FIN-BE-005 shape exactly**, not the scm 3-file shape. `masterdata-api.md` and `masterdata-service/architecture.md` are **authoritative / canonical and left byte-unchanged** (the exact discipline FIN-BE-005 applied to `account-api.md` / `account-service/architecture.md`).

# Goal

Reconcile the erp-platform GAP-integration spec narrative with **ADR-MONO-013 Model B reality** so that `platform-console` (a separate, ADR-MONO-013-governed project) can be a **sanctioned external operator read consumer** of erp's existing read surface — recorded **spec-first**, before `TASK-PC-FE-010` (the erp console section) proceeds.

The apparent tension this resolves (and why FE-010 will be gated on it):

- erp `gap-integration.md` § Token 검증 규칙 narrates *"internal-only 경계 — 외부(비-내부망·비-SSO) 트래픽은 게이트웨이/네트워크 정책에서 거부 (도메인 룰 E7)"*; `PROJECT.md` § GAP IdP Integration echoes *"v1 = backend only … 콘솔이 GAP public client 로 렌더, ADR-MONO-013"* and § v1 OUT *"frontend — 통합 platform console 이 렌더 (ADR-MONO-013 § 3.3, `frontend-app` service_type 없음)"*. The only registered v1 OAuth client is `erp-platform-internal-services-client` (`client_credentials`).
- `platform-console` (ADR-MONO-013 Model B) renders erp operator screens by calling erp's read API **server-side with a human operator's GAP `platform-console-web` OIDC access token** (RS256), **not** an erp client-credentials token.
- Consuming a producer surface whose own spec narrates it as "backend-only / internal-only-boundary" **without the producer spec acknowledging the consumer** violates spec-first discipline (CLAUDE.md: "Specs win over tasks. If implementation requires spec or contract changes, update them first"). This is the concrete instance of the ADR-MONO-013 § 3.3 "zero retrofit unverified" risk for the **first internal-system-primary domain** — Phase 6 resolves it honestly, exactly as Phase 4 (scm) and Phase 5 (finance) did.

The resolution is a **clarifying acknowledgment, not a capability or auth-model change**: erp's JWT validator chain *already* admits any GAP-issued RS256 token that is `tenant_id ∈ { erp, * }`-gated (`gap-integration.md` § Token 검증 규칙 #1/#3/#4) and *already* distinguishes human vs machine callers (`X-Token-Type` = `user | client_credentials`, established at GAP V0018). An operator's GAP token therefore satisfies the **existing** erp contract as written. The *"internal-only 경계"* rule (E7) scopes **non-SSO / non-GAP traffic** (e.g. raw public internet hitting masterdata-service directly) — it does **not** preclude an ADR-MONO-013-governed operator console (which **is** GAP-SSO-authenticated and arrives via the internal Traefik routing) consuming erp's **read** APIs. This task makes that scoping explicit in the erp spec, exactly the way FIN-BE-005 made the analogous "v1 = backend only" / user-flow-client-deferred scoping explicit on the finance side.

# Scope

## In Scope (spec-only, additive)

- **`projects/erp-platform/specs/integration/gap-integration.md`** — add a new section **`## platform-console Operator Read Consumer (ADR-MONO-013)`** (placed after `## Token 검증 규칙`, before `## Error Responses`, mirroring the SCM-BE-015 / FIN-BE-005 `gap-integration.md` edit shape):
  - `platform-console` is a **sanctioned external read consumer** of the existing erp **read** surface: the 10 v1-live GET endpoints documented in `masterdata-api.md` — for each of the 5 masters (`departments` / `employees` / `job-grades` / `cost-centers` / `business-partners`), the **list** (`GET /api/erp/masterdata/<master>`) and **detail** (`GET /api/erp/masterdata/<master>/{id}`) endpoints, both supporting the `?asOf=<ISO-8601>` point-in-time read query (architecture.md effective-period `[from, to)` semantics). It calls these **server-side** with a human operator's **GAP `platform-console-web` OIDC access token** (RS256, `tenant_id=erp` or SUPER_ADMIN `*`, surfaces as `X-Token-Type=user`), validated by the **existing** GAP-JWKS + issuer + tenant-claim chain (§ Token 검증 규칙 #1/#3/#4) — **no new erp OAuth client, no new gateway/service code/route, no auth-model change**. It uses **GAP's own** `platform-console-web` client (owned by GAP / ADR-MONO-013 / ADR-MONO-014), **NOT** `erp-platform-internal-services-client` (the latter stays the only registered v1 erp OAuth client and is **unrelated** — the console uses GAP's console client, not an erp client).
  - The *"internal-only 경계"* rule (E7 / § Token 검증 규칙 #6) is **clarified, not weakened**: the boundary applies to *non-GAP-SSO* traffic (raw public internet, untrusted networks) — an ADR-MONO-013-governed operator console authenticated via GAP and routed through internal Traefik is **within** the internal SSO boundary, not external bypass.
  - Constraints recorded:
    - **Read-only** — the erp **write/mutation** surface (the 16 non-GET endpoints in `masterdata-api.md`: 5×`POST` create, 5×`PATCH`, 5×`POST /retire`, 1×`POST .../move-parent` for departments) is **not** console-consumed at v1. Mutation requires `Idempotency-Key`, role-scoped authorization (E6 fail-CLOSED), and append-only audit (E8); those are operator-domain mutations whose console parity (the analog of GAP `admin-web`'s lock/unlock/etc.) is **v2-deferred** alongside `approval-service` and `admin-service` (ADR-MONO-016 § D3 v2 Service Map / `PROJECT.md` § v1 OUT). Read-only, like the scm/finance precedents.
    - erp stays **single-org** — the deliberate `multi-tenant` non-declaration in `PROJECT.md` frontmatter (`traits: [internal-system, transactional, audit-heavy]`) is **unaffected**: tenant scoping stays the GAP-claim + the existing producer-side `tenant_id ∈ {erp,*}` gate (#4 + E7); the console's own `multi-tenant`/`integration-heavy`/`audit-heavy` traits are the **console's** responsibility, not erp's.
    - erp owns its contracts (consumer-only; `masterdata-api.md` authoritative + **unchanged** — request/response/error tables canonical there; the `?asOf=` semantics and flat error envelope `{ code, message, details?, timestamp }` are producer-canonical).
    - **erp internal-system producer obligations the consumer must honour (recorded as the spec-first basis FE-010 will bind to)**: confidential `data_sensitivity` — the console must not log raw employee PII (names, contact details), business-partner financial details, or cost-center sensitive attributes; the architecture E1 (reference integrity), E2 (effective dating half-open `[from, to)`), E3 (point-in-time read via `?asOf=`), and E8 (append-only audit) are producer-authoritative invariants — the console renders them faithfully (active vs retired status rendered honestly; `effective_to` populated rows shown distinctly; `?asOf=<past>` correctly returns the state-at-that-time, not current state). These are **producer-authoritative facts cross-referenced here**, not new erp requirements.
  - Cross-ref: ADR-MONO-013 (governing) + ADR-MONO-016 (erp domain governance, unchanged) + platform-console `console-integration-contract.md` § 2.4.8 (the consumer-side obligation, to be authored by TASK-PC-FE-010) + § 2.4.5/§ 2.4.6/§ 2.4.7 (the FE-007/FE-008/FE-009 per-domain-credential rule the console reuses for erp). Add a 참조 bullet only if natural (documentation, no behavior checklist item).
- **`projects/erp-platform/PROJECT.md`** — in `## GAP IdP Integration`, append one clarifying bullet after the existing *"v1 = backend only. user-flow PKCE client 는 별도 V slot (v1 미발행 — 콘솔이 GAP public client 로 렌더, ADR-MONO-013)."* line: `platform-console` (ADR-MONO-013 Model B) is an **external operator read consumer** using GAP's own `platform-console-web` console client; erp itself stays backend-only (no erp frontend, no erp user-flow client; only `erp-platform-internal-services-client` registered, console uses GAP's client). **Frontmatter (domain/traits/service_types/data_sensitivity) MUST stay byte-unchanged** — erp deliberately excludes `multi-tenant` (and `integration-heavy`); the console's traits are the console's, not erp's. Do **not** add a Service Map row (no new erp service). The existing § v1 OUT bullet *"frontend — 통합 platform console 이 렌더 (ADR-MONO-013 § 3.3, `frontend-app` service_type 없음)"* already aligns and is **byte-unchanged**.

## Out of Scope

- Any `apps/` / production code, any new OAuth client, any new gateway/service route or auth-model change (spec-only reality-alignment).
- Any erp ADR (governed by ADR-MONO-013; document/accept of an existing capability — see Dependency Markers). erp domain governance remains ADR-MONO-016, untouched. ADR-MONO-016 is not edited.
- Mutating erp's `PROJECT.md` frontmatter (domain/traits/service_types/data_sensitivity) — explicitly preserved byte-for-byte. Adding `multi-tenant`/`integration-heavy` would be a classification change erp deliberately excluded.
- Editing `masterdata-api.md` or `masterdata-service/architecture.md` (authoritative producer / canonical ADR-MONO-012 form — cross-referenced, unchanged; the exact discipline SCM-BE-015/FIN-BE-005 applied to their producer + architecture specs).
- The platform-console-side binding (`console-integration-contract.md` § 2.4.8, `features/erp-ops`) — that is `TASK-PC-FE-010` (platform-console project-internal), which this task only **unblocks**.
- erp `gateway-service` architecture spec (declared v1-IN in `PROJECT.md` but not yet authored under `specs/services/gateway-service/`) — out of scope here; when authored separately it can cross-reference this section, but this task does not pre-author or speculate on it.
- The erp write/mutation surface and the v2 `approval-service` / `read-model-service` / future `admin-service` — v2-deferred (ADR-MONO-016 § D3); the console consumes only the v1-live masterdata read endpoints.

# Acceptance Criteria

- [ ] `gap-integration.md` has the new `## platform-console Operator Read Consumer (ADR-MONO-013)` section, accurately stating: existing-capability reuse (no new client/code/route/auth-model change), read-only scope (the enumerated 10 GET reads with `?asOf=`; write/mutation + v2 services excluded), single-org preservation, the GAP `platform-console-web` token + `X-Token-Type=user` + existing `tenant_id ∈ {erp,*}` path, the *"internal-only 경계"* clarification (boundary = non-GAP-SSO traffic, console is within SSO boundary), the console-uses-GAP's-own-client (not `erp-platform-internal-services-client`) point made explicit, the cross-referenced erp internal-system producer obligations (confidential + audit-heavy producer invariants E1/E2/E3/E8), and the ADR-MONO-013 / ADR-MONO-016 / console-contract cross-refs.
- [ ] `PROJECT.md` § GAP IdP Integration clarifying bullet added; **frontmatter byte-unchanged** (domain=`erp`, traits=`[internal-system, transactional, audit-heavy]`, service_types=`[rest-api]`, data_sensitivity=`confidential`); no new Service Map row; § v1 OUT frontend bullet byte-unchanged.
- [ ] The reconciliation is **document/accept only** — no erp auth model / code / OAuth client / route change anywhere; diff is purely additive spec prose. No erp ADR created. `masterdata-api.md` and `masterdata-service/architecture.md` byte-unchanged. ADR-MONO-016 byte-unchanged.
- [ ] Cross-references resolve (ADR-MONO-013 path; ADR-MONO-016 path; platform-console `console-integration-contract.md` path) — spec internal-link lint clean; `masterdata-service/architecture.md` canonical Identity table + `### Service Type Composition` H3 untouched (this task does not edit architecture.md).
- [ ] Scope = `projects/erp-platform/` only (2 spec files + task lifecycle/INDEX); no `apps/`, no shared-path, no platform-console file; no churn-clock effect.
- [ ] `TASK-PC-FE-010`'s erp-side prerequisite is satisfied (authored + this task merged → FE-010 may move `backlog → ready`).

# Related Specs

> Target project = `erp-platform`. Governing: monorepo `docs/adr/ADR-MONO-013-platform-console-foundation.md` (+ `ADR-MONO-016` for erp domain governance, unchanged). Follow `platform/entrypoint.md`; erp rule layers per `PROJECT.md` (domain `erp`, traits `[internal-system, transactional, audit-heavy]`).

- `docs/adr/ADR-MONO-013-platform-console-foundation.md` § D1 (Model B) / § D5 (integration contract) / § D6 (Phase 6 erp) / § 3.3 (backend-only; console renders erp) — the governing authority
- `docs/adr/ADR-MONO-016-erp-platform-bootstrap.md` — erp domain governance (unchanged; cross-ref only — Phase 6 is "console section governed by future erp ADR"; that ADR is ADR-MONO-016 and is **not** re-decided here)
- `projects/erp-platform/specs/integration/gap-integration.md` (edited — new consumer section; the existing § Token 검증 규칙 #1/#3/#4 + #6 internal-only 경계 + § OAuth Clients `X-Token-Type` edge case already admit a GAP RS256 human token, tenant-gated)
- `projects/erp-platform/PROJECT.md` (edited — § GAP IdP Integration clarifying bullet; frontmatter preserved)
- `projects/erp-platform/specs/contracts/http/masterdata-api.md` (authoritative producer — read surface 10 GET endpoints × `?asOf=` query, flat error envelope `{code, message, details?, timestamp}`; **not edited**, cross-referenced)
- `projects/erp-platform/specs/services/masterdata-service/architecture.md` (read-only context — § Identity table `Service Type=rest-api`, § Security RS256/GAP JWKS, § Multi-tenancy `tenant_id ∈ {erp,*}`, E1/E2/E3/E8 invariants; **not edited**, canonical ADR-MONO-012 form preserved)
- `projects/platform-console/specs/contracts/console-integration-contract.md` (consumer-side obligation — § 2.4.8 to be authored by TASK-PC-FE-010; § 2.4.5/§ 2.4.6/§ 2.4.7 the FE-007/FE-008/FE-009 per-domain-credential rule governing the GAP-token credential the console uses for erp)
- `projects/finance-platform/tasks/done/TASK-FIN-BE-005-platform-console-operator-read-consumer-reconciliation.md` (the Phase 5 finance precedent this mirrors — same document/accept discipline, identical 2-file shape)
- `projects/scm-platform/tasks/done/TASK-SCM-BE-015-platform-console-operator-read-consumer-reconciliation.md` (the Phase 4 scm precedent — 3-file shape because scm has `gateway-public-routes.md`; erp follows the finance 2-file subset honestly, not the scm shape)
- `projects/platform-console/tasks/backlog/TASK-PC-FE-010-console-erp-operations-section.md` (the dependent task this unblocks — authored separately as part of Phase 6 step 2)

# Related Skills

- `.claude/skills/` — design-api / architect (spec reconciliation, cross-project consumer acknowledgment under a governing ADR; no code; the "document/accept ≠ ADR" boundary call).

---

# Related Contracts

- **Changed (this task, spec-only additive)**: `gap-integration.md` (new `## platform-console Operator Read Consumer (ADR-MONO-013)` section + 참조), `PROJECT.md` (one clarifying bullet; frontmatter untouched).
- **Consumed/cross-referenced (unchanged, authoritative)**: erp `masterdata-api.md` (10 GET reads at `/api/erp/masterdata/*`, `?asOf=` point-in-time query, flat envelope); GAP `platform-console-web` OIDC client (owned by GAP / ADR-MONO-013 / ADR-MONO-014; not redefined here).
- **Not touched**: `masterdata-service/architecture.md` (canonical ADR-MONO-012 form preserved), any `apps/` code, any OAuth seed migration, any erp ADR, ADR-MONO-016.

---

# Target Service

- `erp-platform` / `masterdata-service` (`rest-api`) — **spec-only**. The reconciliation documents an existing JWT-validation capability (GAP RS256 token validation + `tenant_id ∈ {erp,*}` gate + `X-Token-Type` distinction + internal-network boundary); no `masterdata-service` (or the v1-IN-but-architecturally-pending `gateway-service`) code is changed.

---

# Architecture

- ADR-MONO-013 Model B: erp stays backend-only (correctly **no** `frontend-app` service_type, § 3.3; ADR-MONO-016 § D3.1 binds this — platform-console parity-slice is the UI decision); `platform-console` (separate project) renders erp operator screens by calling erp's existing read APIs server-side. This task records that consumer relationship spec-first on the erp side.
- The erp JWT chain (GAP RS256 + JWKS + issuer SAS/legacy + `tenant_id ∈ {erp,*}` + `X-Token-Type` user/machine split + #6 internal-only network boundary) already admits a human operator's GAP RS256 token — no architectural change, hence no erp ADR (document/accept under the governing ADR-MONO-013; erp domain governance stays ADR-MONO-016). The *"internal-only 경계"* clarification is documentary precision, not a normative change.
- erp classification (single-org; `multi-tenant`/`integration-heavy` deliberately excluded) is preserved — tenant scoping remains the GAP `tenant_id` claim enforced by the existing producer-side gate; the console's own multi-tenant/integration-heavy/audit-heavy traits are the console's responsibility, not erp's.

---

# Implementation Notes

- Pure spec edit. Sibling precedent for spec-only finance/scm closures: **TASK-FIN-BE-005** (the direct Phase 5 analog — identical 2-file shape) + TASK-SCM-BE-015 (the Phase 4 precedent — 3-file shape because scm has `gateway-public-routes.md`).
- Keep edits **additive**; do **not** reword existing normative auth rules (§ Token 검증 규칙 #1–#6, § Error Responses) — only add the clarifying consumer section + the one `PROJECT.md` bullet. The *"internal-only 경계"* clarification belongs in the new section, **not** in #6 itself (#6 stays byte-identical; the new section cross-references and contextualises it).
- **Do not touch erp `PROJECT.md` frontmatter** (domain/traits/service_types/data_sensitivity) — adding `multi-tenant`/`integration-heavy` would be a classification change erp deliberately excluded; the console's traits ≠ erp's.
- **Do not edit `masterdata-service/architecture.md`** — canonical Identity table + `### Service Type Composition` H3 (ADR-MONO-012 D3) stay byte-intact; the existing-capability statements there are referenced read-only.
- **Do not edit `masterdata-api.md`** — authoritative producer; the 10 GET endpoints / `?asOf=` query / flat error envelope are canonical there and only cross-referenced. The 16 mutation endpoints stay canonical-but-out-of-console-scope.
- **Do not edit ADR-MONO-016** — erp domain governance is untouched; this is reality-alignment under ADR-MONO-013, not an ADR-016 amendment.
- Verification = spec internal-link resolution + `git diff` confirms exactly 2 erp spec files changed (+ task lifecycle/INDEX). No Docker/build (spec-only).
- Recommend implementation model: **Opus** (cross-project contract-reconciliation judgement under a governing ADR — interpretive: must NOT over-reach into a capability/auth change or an erp classification mutation; the "document/accept ≠ ADR" boundary call is the crux; the erp-shape choice — 2-file finance subset, NOT scm 3-file — must be reasoned, not blindly copied; the *"internal-only 경계"* clarification phrasing is the most delicate prose, because over-broad text would weaken E7). Branch name must not contain the `master` substring.
- erp PR Separation Rule (`projects/erp-platform/tasks/INDEX.md` Move Rules § PR Separation Rule — identical to root/finance/scm: *"Each lifecycle transition lands in its own PR. Never bundle task spec authoring with implementation in the same PR."*): spec-authoring and impl must **not** share a PR. Lifecycle: this file → `ready/` (spec-authoring commit/PR) → spec edits + `ready/ → review/` (impl commit/PR) → `review/ → done/` (close chore PR).

---

# Edge Cases

- An operator's GAP `platform-console-web` token carries `tenant_id` ≠ `erp` and the operator is not SUPER_ADMIN `*` → the **existing** `tenant_id` validator (#4) rejects it `403 TENANT_FORBIDDEN` (unchanged behavior; the console blocks the section client-side per FE-010, but erp enforcement is the authority).
- A reader infers "platform-console = erp's deferred v2 frontend / needs an `erp-platform-user-flow-client`" → the new text must explicitly state the console is **GAP's** client (`platform-console-web`), a separate ADR-MONO-013-governed project; no erp user-flow client is registered (or planned at v1).
- A reader infers erp must become `multi-tenant`/`integration-heavy` to serve the console → the text must explicitly preserve erp single-org; tenant scoping is the GAP claim + existing gate; console traits are the console's.
- A reader infers the console may call erp **write** endpoints (5×create / 5×patch / 5×retire / 1×move-parent) → the text must explicitly scope the console to **read-only**; erp write + the v2 `approval-service`/`read-model-service`/future `admin-service` are not console-consumed at v1.
- A reader infers the *"internal-only 경계"* (#6 / E7) **forbids** the console → the new section must clarify that #6 scopes *non-GAP-SSO* traffic (raw public internet, untrusted networks); a GAP-SSO-authenticated console routed through internal Traefik is **within** the SSO boundary, not external bypass. (The new section CLARIFIES #6, it does NOT weaken it; #6 stays byte-identical.)
- Future erp `gateway-service` architecture spec (declared v1-IN in `PROJECT.md` but not yet authored under `specs/services/gateway-service/`) → out of scope; when authored, it can cross-reference this section; until then, the console consumes the v1-live `masterdata-service` reads directly (architecturally identical to finance Phase 5 where finance gateway is v1-deferred).
- `?asOf=<past-instant>` queried by the console → the producer's E3 point-in-time read returns the state at that instant (effective-period `[from, to)` half-open semantics); the console renders that state honestly (not current state).

# Failure Scenarios

- The reconciliation drifts into changing/relaxing an existing normative auth rule (e.g. weakening tenant enforcement, admitting the write surface, weakening the *"internal-only 경계"* #6) → wrong; this is document/accept only. AC pins "purely additive, no auth-model change, read-only, #6 byte-identical".
- erp `PROJECT.md` frontmatter mutated (adding `multi-tenant`/`integration-heavy`) → classification erp deliberately excluded; AC pins frontmatter byte-unchanged.
- A new erp ADR is authored, or ADR-MONO-016 is edited → unnecessary and wrong: ADR-MONO-013 governs; this is (B) document/accept of an existing capability (no competing convention). AC forbids an erp ADR; ADR-MONO-016 is cross-ref only.
- `masterdata-service/architecture.md` or `masterdata-api.md` edited / canonical form disturbed → out of scope; AC pins them untouched.
- The scm-shaped 3-file edit is force-fit onto erp (e.g. authoring an erp `gateway-public-routes.md` that doesn't yet exist) → wrong; erp's gateway-service architecture has not been authored yet (separate future task). The honest erp shape is the **finance FIN-BE-005 2-file subset** (`gap-integration.md` + `PROJECT.md`), recorded in Dependency Markers.
- The erp spec-authoring commit and the impl commit are bundled into one PR → violates erp INDEX PR Separation Rule ("Never bundle task spec authoring with implementation in the same PR"); keep them distinct PRs.
- Implementation proceeds for `TASK-PC-FE-010` before this is merged → spec-first violation; FE-010's own Dependency Marker + AC gate it on this task being merged.

---

# Verification

- `grep`/link-check: ADR-MONO-013 + ADR-MONO-016 relative paths from each edited erp spec resolve; platform-console `console-integration-contract.md` relative path resolves.
- `git diff` confirms: only `projects/erp-platform/specs/integration/gap-integration.md`, `projects/erp-platform/PROJECT.md` (+ task lifecycle/INDEX) changed; additive only; erp `PROJECT.md` frontmatter and `masterdata-service/architecture.md` + `masterdata-api.md` + `ADR-MONO-016` byte-unchanged; no `apps/`, no shared path, no platform-console file; § Token 검증 규칙 #1–#6 byte-identical (especially #6 *"internal-only 경계"* preservation).
- No Docker/build required (spec-only). CI markdown/path-filter expected to SKIP code jobs (sibling precedent: FIN-BE-005 + SCM-BE-015 + scm BE-008/010/011/013/014).

---

# Definition of Done

- [ ] `gap-integration.md` `## platform-console Operator Read Consumer (ADR-MONO-013)` section + (optional) 참조 merged
- [ ] `PROJECT.md` clarifying bullet merged; frontmatter byte-unchanged; no Service Map row; § v1 OUT bullet byte-unchanged
- [ ] Diff purely additive spec prose; no code/client/route/auth-model change; no erp ADR; ADR-MONO-016 untouched; `architecture.md` + `masterdata-api.md` untouched; § Token 검증 규칙 #1–#6 byte-identical
- [ ] Cross-refs resolve; scope = erp-platform only (2 files)
- [ ] `TASK-PC-FE-010` erp-side prerequisite satisfied (this merged) — recorded in FE-010 linkage when FE-010 is promoted `backlog → ready`
- [ ] Ready for review
