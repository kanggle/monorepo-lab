# ADR-MONO-017 — platform-console-bff Architecture (Phase 7 Aggregation & Cross-Domain Dashboards)

**Status:** ACCEPTED
**Date:** 2026-05-20
**History:** PROPOSED 2026-05-20 (TASK-MONO-125, PR #663 squash `1fbe017a` — resolves the architecture-decision dimension of ADR-MONO-013 § D6 Phase 7 ahead of any `console-bff` implementation, mirroring the ADR-MONO-014 (Phase 2 operator-auth) + ADR-MONO-015 (Phase 2 dashboards) staged-child pattern; decision direction **CHOSEN-PROPOSED** per the dispatcher reasoning recorded below, **finalised at ACCEPTED**). ACCEPTED 2026-05-20 (TASK-MONO-126 — user-explicit intent "ADR-017 ACCEPTED" via AskUserQuestion option A selection direct after `/audit-memory` 2026-05-20; option description: *"이 옵션 선택 자체가 § D6.1 user-explicit intent 'ADR-017 ACCEPTED' 로 간주되어 진행됨"*; D1-D8 CHOSEN-PROPOSED finalised **byte-unchanged** from PROPOSED; ACCEPTED authorizes the post-ACCEPTED future tasks `TASK-PC-BE-001` (`console-bff` Spring Boot skeleton) + `TASK-PC-FE-011` (MVP "Operator Overview" cross-domain dashboard) — each with dependency-correct base = this ACCEPTED on main, sibling ADR-014 → TASK-BE-298 / ADR-015 → TASK-PC-FE-005 staged-execution pattern).
**Decision driver:** ADR-MONO-013 § D6 Phase 7 ("console-bff fan-out + cross-domain dashboards") requires *"5 domains live"* as its gate. The gate was satisfied **2026-05-20** when TASK-PC-FE-010 (erp console section) close PR #661 squash `eaa1de51` brought the non-GAP federation count to **5** (GAP + wms + scm + finance + erp; Phase 6 COMPLETE per ADR-013 § History additive note count = 4). ADR-013 § D5 (line 76) prescribes that `platform-console`'s `service_types` gains `rest-api` "when the console-bff (Phase 7) lands" but DEFERS the architectural *what* (aggregation pattern / federation shape / dashboards composition / credential reuse / resilience / multi-tenant / observability / phasing). Authoring `console-bff` implementation without resolving these eight axes silently bakes architecture (HARDSTOP-09). This ADR is that decision record; the `console-bff` skeleton + any cross-domain dashboard build is **PAUSED** until this is ACCEPTED.
**Supersedes:** none. **Amends:** [ADR-MONO-013](ADR-MONO-013-platform-console-foundation.md) § D5 (line 76: parenthetical scope-pointer added — additive, no D1-D8 decision change; HARDSTOP-04 discipline preserved) + § History (one new "Additive note" blockquote referencing this PROPOSED + the 5/5-domains-live gate, identical shape to the Phase 4/5/6 backfill notes). **Reconciles:** none yet (PROPOSED scopes the architecture; full integration-contract reconciliation lands at the ACCEPTED-execution skeleton task).
**Related:** [ADR-MONO-013](ADR-MONO-013-platform-console-foundation.md) (console foundation, Model B, parent), [ADR-MONO-014](ADR-MONO-014-platform-console-operator-auth-token-exchange.md) (operator token exchange — the credential the BFF reuses for the GAP-domain leg of D4; staged-child precedent), [ADR-MONO-015](ADR-MONO-015-platform-console-dashboards-model.md) (Composed-overview pattern — the GAP-domain dashboard the Phase 7 MVP generalises across 5 domains; staged-child precedent), [ADR-MONO-006](ADR-MONO-006-observability-stack.md) (Vector + VictoriaMetrics — the D7 observability reuse base), TASK-PC-FE-007 #633 / TASK-PC-FE-008 #637 / TASK-PC-FE-009 #644 / TASK-PC-FE-010 #660 (the four per-domain credential rule confirmations the BFF D4 inherits verbatim).

---

## 1. Context

### 1.1 The gate just satisfied

- **ADR-MONO-013 § D6 Phase 7** lists *console-bff fan-out + cross-domain dashboards* with gate = "5 domains live" + model = "Opus (composition), Sonnet (shell)". The gate was implicit: "5 backend domains rendered by the console" is satisfied when GAP + wms + scm + finance + erp all have their console section landed.
- **Objective gate satisfaction (2026-05-20)**: FE-007 #633 `81395376` (wms) + FE-008 #637 `c34fc0ac` (scm) + FE-009 #644 `29b01826` (finance) + **FE-010 #660 `be9b78fa` (erp, this same date)** + close chore #661 `eaa1de51` (Phase 6 COMPLETE, 5/5 confirmed). ADR-013 § History "Additive note" count was **4** at the moment of this PROPOSED's commit, exactly tracking Phase 2 + Phase 4 + Phase 5 + Phase 6.
- **GAP-side**: the GAP `admin-service` operator surface was federated by FE-001..006 (Phase 2, governed by ADR-MONO-014 / ADR-MONO-015). GAP is the FIFTH domain in the cross-domain composition (the four non-GAP domains being wms/scm/finance/erp); the "5 domains live" gate counts GAP.

### 1.2 What § D5 prescribed vs deferred

- **Prescribed**: `platform-console`'s `service_types` adds `rest-api` *when the console-bff lands*. The BFF is **part of the `platform-console` project**, not a new project (zero-retrofit on project boundaries).
- **Deferred (this ADR resolves the DIRECTION; ACCEPTED finalises)**:
  1. **D1 — Aggregation pattern** (REST orchestrator / GraphQL gateway / gRPC / hybrid).
  2. **D2 — Federation shape** (server-side fan-out only / hybrid).
  3. **D3 — Cross-domain dashboards composition** (reuse existing per-domain reads / introduce aggregating producer endpoints).
  4. **D4 — Operator credential & tenant-scoping** (per-domain credential rule extended / unified single-token translated).
  5. **D5 — Resilience** (per-domain circuit-breaker inherited / BFF-level aggregation degrade).
  6. **D6 — Multi-tenant isolation** (`tenant_id` claim pass-through / BFF re-derivation).
  7. **D7 — Observability** (per-domain fan-out attribution / new aggregation).
  8. **D8 — Phasing** (MVP scope / full).

### 1.3 Why an ADR (HARDSTOP-09) + staged PROPOSED → ACCEPTED

Per [`platform/hardstop-rules.md`](../../platform/hardstop-rules.md) HARDSTOP-09 #2: starting a `console-bff` skeleton without resolving D1-D8 bakes architecture silently (e.g. picking GraphQL implicitly would commit the monorepo to schema stitching across 5 domain shapes). This is the exact prevention role ADR-MONO-014/015 played for Phase 2; ADR-017 is its Phase 7 analog.

**Staged pattern (sibling: ADR-008/013/014/015/016)**: the PROPOSED stage recorded the **decision direction** + **D1-D8 frame** + **downstream sequencing** + **the hard invariants the chosen direction must inherit** (the FE-007..010 per-domain credential rule MUST NOT be retroactively redefined — D4). The ACCEPTED transition was executed as **TASK-MONO-126** (this same date 2026-05-20, doc-only) on user-explicit intent (§ D6.1 of ADR-MONO-003a via AskUserQuestion option A selection direct after `/audit-memory`); D1-D8 finalised **byte-unchanged** from PROPOSED (ACCEPTED = *finalise*, not re-decide; HARDSTOP-04 + sibling ADR-014/015 ACCEPTED-flip discipline). The `console-bff` skeleton + first cross-domain dashboard implementation remain **further future tasks** (post-ACCEPTED), exactly as ADR-014's TASK-BE-298 + ADR-015's TASK-PC-FE-005 were future of their ACCEPTED.

---

## 2. Decision

### D1 — Aggregation pattern

| Option | Mechanics | Verdict |
|---|---|---|
| **B. REST orchestrator (server-side composition)** | The BFF is a Spring Boot `rest-api` service inside `apps/console-bff/` that calls the existing per-domain read APIs server-side (via the per-domain credential of D4) and **composes** the response. No GraphQL schema stitching, no gRPC, no new producer endpoints. Each cross-domain dashboard endpoint is a hand-authored composition route. | **CHOSEN (PROPOSED direction)** — smallest blast radius; zero-retrofit-preserving (D3); identical philosophy to ADR-015 Composed-overview pattern generalised across 5 domains; reuses Spring Boot + libs/java-web + libs/java-security the rest of the monorepo already runs on. **Finalised at ACCEPTED.** |
| A. GraphQL gateway (schema stitching) | BFF exposes a single GraphQL endpoint; per-domain schemas stitched together; client (`console-web`) composes via GraphQL queries. | Rejected (PROPOSED) — schema-stitch retrofit on every per-domain producer; introduces a second contract style (GraphQL alongside the existing flat-envelope REST in § 2.4.5..8); large operator-side surprise; defers domain ownership to the BFF (anti-pattern for ADR-013 Model B). May revisit if a real cross-domain query surface emerges that REST composition cannot serve. |
| C. gRPC fan-out | BFF speaks gRPC to each domain | Rejected — domains expose HTTP/JSON (architecture.md across 5 domains); gRPC would force a per-domain gateway translation layer. Wrong layer for an operator-facing BFF. |
| D. Hybrid (REST + GraphQL for dashboards only) | Composition routes are REST; cross-domain dashboards expose a GraphQL view | Rejected (PROPOSED) — adds a second contract style for a single use case; D8 MVP can be served with REST composition routes alone. |

### D2 — Federation shape

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Server-side fan-out only** | BFF is the sole cross-domain composer. `console-web` calls BFF endpoints for cross-domain views and continues to call per-domain endpoints directly for single-domain sections (existing FE-001..010 routes are NOT relocated). | **CHOSEN (PROPOSED direction)** — affirms ADR-MONO-013 § D5 ("calls are tenant-scoped; isolation enforced"); preserves per-domain section auth + envelope discipline; zero-retrofit on single-domain sections. **Finalised at ACCEPTED.** |
| B. Hybrid (client-side cross-domain composition) | `console-web` calls multiple per-domain endpoints and composes client-side | Rejected — drags per-domain tokens to the browser (HttpOnly cookie scope mismatch); blows the SSR-only invariant FE-001..010 hold; defeats the BFF's purpose. |
| C. BFF subsumes single-domain sections | `console-web` calls only the BFF; per-domain sections rerouted through it | Rejected (PROPOSED) — retrofit on 4 merged FE-007..010 PRs (the existing `features/{wms,scm,finance,erp}-ops` modules); contradicts § 3.3 "zero retrofit". A future architectural review may reconsider, but not for Phase 7. |

### D3 — Cross-domain dashboards composition

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Reuse existing per-domain read APIs verbatim** | Each composition route in BFF calls existing GET endpoints on each domain (e.g. for an "Operator Overview", reuse `GET /api/admin/accounts` count, `/api/wms/inventory/health`, `/api/scm/...`, `/api/finance/...`, `/api/erp/masterdata/...` reads). No new producer endpoints, no per-domain spec change. | **CHOSEN (PROPOSED direction)** — § 3.3 "zero retrofit" fifth confirmation (Phase 2/4/5/6/7); ADR-MONO-015 Composed-overview pattern generalised. **Finalised at ACCEPTED.** |
| B. Introduce aggregating producer endpoints per domain | Each domain adds a `/summary` or `/dashboard-card` endpoint optimised for BFF consumption | Rejected (PROPOSED) — retrofit cost on 5 domain producer specs + 5 implementations; defeats § 3.3. May revisit if per-domain read-fanout cost becomes a Phase 7 production bottleneck. |
| C. Pre-aggregate via batch / materialized view | A scheduled job composes a denormalised cross-domain table | Rejected — introduces a write/state path the BFF didn't have (read-only ethos) + staleness operator-confusing; over-engineering for MVP. |

### D4 — Operator credential & tenant-scoping (HARD INVARIANT)

> **Hard invariant inherited from FE-007/008/009/010**: the per-domain credential rule (`console-integration-contract.md` § 2.4.5/6/7/8) MUST NOT be retroactively redefined. The BFF is its **credential dispatcher**, not its rewriter.

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Per-domain credential rule extended verbatim** | BFF carries the FE-007..010 rule: for each downstream domain, GAP-domain uses the RFC 8693 exchanged operator token (§ 2.6, ADR-014), non-GAP domains (wms/scm/finance/erp) use the operator's GAP OIDC access token (`getAccessToken()`). BFF picks the credential per outbound domain call from the inbound operator's GAP session cookie. `tenant_id` claim is producer-enforced (not BFF re-derived; see D6). | **CHOSEN (PROPOSED direction)** — only option that preserves the hard invariant. **Finalised at ACCEPTED.** |
| B. Single unified BFF token | BFF mints its own token type for outbound calls; each domain trusts the BFF | Rejected — widens every domain's auth trust boundary to a 2nd issuer; defeats the FE-007..010 per-domain producer-enforcement gate; same critique as ADR-014's rejected Option A. |
| C. Operator-token-only across all domains | The non-GAP domains start trusting the GAP operator token | Rejected — non-GAP domains never owned the operator-token trust; would require 4 producer spec changes (retrofit on FE-007..010 contracts). |

### D5 — Resilience

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Per-domain circuit-breaker inherited from § 2.5** | BFF applies the existing `console-integration-contract.md` § 2.5 resilience patterns per outbound domain (circuit-breaker + retry + timeout); aggregation degrade renders "responsive domains' cards + per-failed-domain degraded card", never blanks the dashboard. Reuse `libs/java-web` resilience primitives. | **CHOSEN (PROPOSED direction)** — per-section degrade discipline carries from FE-001..010; identical operator UX. **Finalised at ACCEPTED.** |
| B. BFF-level aggregation timeout (all-or-nothing) | BFF returns 503 if any single domain fan-out fails | Rejected — operator-hostile; one slow domain blanks the whole dashboard; violates § 2.5 + the Phase 2 ADR-015 degrade discipline. |

### D6 — Multi-tenant isolation

| Option | Mechanics | Verdict |
|---|---|---|
| **A. `tenant_id` claim pass-through (producer-side authority)** | BFF forwards the operator's JWT `tenant_id` claim verbatim to each outbound domain call (via the per-D4 credential's JWT). Each producer's `TenantClaimValidator` (`tenant_id ∈ {<domain>,*}`) gates the call. BFF does not re-derive or relax tenancy. | **CHOSEN (PROPOSED direction)** — preserves the GAP / wms / scm / finance / erp producer-authoritative tenant rule; identical to the per-domain section discipline. **Finalised at ACCEPTED.** |
| B. BFF central tenant gate | BFF rejects cross-tenant calls in-process before fan-out | Rejected (PROPOSED) — duplicates per-domain enforcement (DRY violation); creates a divergence risk if domain enforcement evolves. May be revisited as a defense-in-depth optimisation, but not as the primary gate. |

### D7 — Observability

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Per-domain fan-out attribution (existing stack)** | BFF emits metrics per outbound domain (`bff_fanout_latency{domain="…"}`, `bff_fanout_errors_total{domain="…",code="…"}`, `bff_aggregation_degrade_count{dashboard="…",degraded_domain="…"}`) + per-domain tracing span. Reuse Vector + VictoriaMetrics (ADR-MONO-006). No new observability stack. | **CHOSEN (PROPOSED direction)** — operators get per-domain attribution when a dashboard degrades; identical observability ethos to the existing stack. **Finalised at ACCEPTED.** |
| B. BFF-level aggregate metrics only | Single `bff_request_latency` / `bff_errors_total` without per-domain attribution | Rejected — operator can't diagnose which domain caused a degrade; defeats per-section degrade UX feedback loop. |

### D8 — Phasing

| Option | Mechanics | Verdict |
|---|---|---|
| **MVP = 1 "Operator Overview" cross-domain dashboard** | First Phase 7 deliverable is a single composed dashboard generalising the GAP-domain Composed-overview pattern (ADR-MONO-015 D1) across 5 domains. Subsequent Phase 7 dashboards (e.g. domain health, throughput) are separate future tasks. | **CHOSEN (PROPOSED direction)** — minimal viable scope to validate D1-D7 against real fan-out; ADR-015 pattern continuity. **Finalised at ACCEPTED.** |
| Full Phase 7 (all dashboards in one task) | Build the entire Phase 7 dashboard suite at once | Rejected (PROPOSED) — over-scopes the validation cycle; Phase 7 § D6 gate is satisfied, but the architectural risk is highest on the first dashboard; iterate. |

---

## 3. Consequences

### 3.1 Hard invariants this ADR carries

- **§ 3.3 "zero retrofit" — fifth confirmation**: D3 + D4 + D6 jointly preserve it. No per-domain producer spec or implementation change is required for Phase 7 MVP.
- **Per-domain credential rule (§ 2.4.5/6/7/8) — byte-unchanged**: D4 records it as a hard invariant the BFF dispatches by, never rewrites.
- **ADR-MONO-013 D1-D8 — byte-unchanged**: the two ADR-013 amendments in this PR are additive only (§ D5 parenthetical clarification + § History additive blockquote, HARDSTOP-04 discipline).
- **Producer-side tenant gate (§ 2.4.* `tenant_id ∈ {<domain>,*}`) — authoritative**: D6 records BFF as pass-through, never re-derivation.

### 3.2 What this ADR does NOT do (deferred to ACCEPTED + the post-ACCEPTED skeleton task)

- It does NOT add `rest-api` to `platform-console`'s `service_types`. That happens at the `console-bff` skeleton task (post-ACCEPTED).
- It does NOT author `apps/console-bff/` or any code; no Spring Boot module, no Dockerfile, no docker-compose change, no Traefik label.
- It does NOT extend `console-integration-contract.md` with BFF endpoint definitions; that is the skeleton task's spec-first authoring scope.
- It does NOT implement the MVP "Operator Overview" dashboard; that is a separate Phase 7 task (post-skeleton).

### 3.3 Future-self

ACCEPTED execution chain (sketch, finalized at ACCEPTED):

1. **TASK-MONO-126** (or next available) — ADR-MONO-017 PROPOSED → ACCEPTED transition (doc-only, user-explicit-intent gated; sibling: MONO-118 / MONO-110 / MONO-112 / MONO-113).
2. **TASK-PC-BE-001** (platform-console project-internal, post-ACCEPTED) — `console-bff` Spring Boot skeleton (Hexagonal per `platform/service-types/rest-api.md`) + `console-bff/architecture.md` (ADR-MONO-012 D3 canonical form) + `PROJECT.md` `service_types += rest-api` (the ADR-013 § D5 prescription executes here, not on PROPOSED) + Traefik `console-bff.local` label + docker-compose wiring.
3. **TASK-PC-FE-011** (post-skeleton) — first cross-domain dashboard MVP (D8 "Operator Overview"); `console-integration-contract.md` new § 2.4.9 (BFF composition routes) + `features/operator-overview/` + `<OperatorOverviewScreen>` calling the BFF MVP endpoint.
4. Subsequent Phase 7 dashboards (domain health, throughput) — separate tasks.

Phase 8 (federation hardening) follows Phase 7 (§ D6 row 8).

---

## 4. Alternatives Considered

The D1-D8 tables above each enumerate alternatives. The cross-cutting alternatives that span multiple decisions:

- **Make `console-bff` a separate `platform-console-bff` project** (not part of `platform-console`). Rejected — ADR-013 § D5 explicitly scopes the BFF to `platform-console`'s service-types; a separate project would have its own PROJECT.md / domain / traits / governance ADR, which violates ADR-013 D1 (Model B is **one** console).
- **Defer Phase 7 entirely until a real cross-domain workflow appears**. Rejected — the § D6 gate is now satisfied and § D5 prescribes the BFF; deferring leaves the gate ungated indefinitely, opening the door to ad-hoc cross-domain client-side composition leaking into `console-web` (which would violate D2 Option B's rejection retroactively).
- **Pre-build a generic aggregation engine instead of hand-authored routes**. Rejected for MVP — generic aggregation (e.g. GraphQL stitching) would land Option D1.A's costs as the FIRST thing rather than the LAST; iterate via hand-authored routes and re-evaluate after 3+ cross-domain dashboards.

---

## 5. Relationship to ADR-MONO-013 / 014 / 015

| | ADR-MONO-013 | ADR-MONO-014 | ADR-MONO-015 | **ADR-MONO-017 (this)** |
|---|---|---|---|---|
| Role | Console foundation (Model B, phases 0-8) | Operator-auth bridge (Phase 2 PREREQ) | Dashboards model (Phase 2 dashboards PREREQ) | **Phase 7 BFF architecture (PREREQ for `console-bff` skeleton)** |
| Phase | All | 2 | 2 | **7** |
| Pattern | Parent ADR (PROPOSED→ACCEPTED in 2 stages) | Staged child (PROPOSED→ACCEPTED) | Staged child (PROPOSED→ACCEPTED) | **Staged child (PROPOSED here; ACCEPTED follow-up task)** |
| Scope | Multi-phase roadmap | 1 decision axis (token exchange B vs A/C/D) | 1 decision axis (composition B vs Grafana A vs defer C) | **8 decision axes (D1-D8 above)** |

This ADR amends ADR-MONO-013 § D5 (additive parenthetical scope-pointer) + § History (additive note) and is a prerequisite for ADR-MONO-013 Phase 7 § D6 deliverable execution.

---

## 6. Status Transition History

Append-only.

| Date | Transition | Decision direction | User intent quote | PR(s) |
|---|---|---|---|---|
| 2026-05-20 | created PROPOSED | D1 = REST orchestrator; D2 = server-side fan-out only; D3 = reuse per-domain reads verbatim; D4 = per-domain credential rule extended (HARD INVARIANT); D5 = per-domain circuit-breaker inherited; D6 = `tenant_id` pass-through; D7 = per-domain fan-out attribution; D8 = MVP = 1 "Operator Overview" | "Priority 3 — ADR-MONO-013 Phase 7 console-bff PROPOSED authoring (Recommended)" (AskUserQuestion direct selection 2026-05-20) | this PR (TASK-MONO-125) |
| 2026-05-20 | PROPOSED → ACCEPTED | D1-D8 byte-unchanged (finalised) | "ADR-017 ACCEPTED" via AskUserQuestion option A selection 2026-05-20 (post `/audit-memory`, option description: *"이 옵션 선택 자체가 § D6.1 user-explicit intent 'ADR-017 ACCEPTED' 로 간주되어 진행됨"*) | this PR (TASK-MONO-126) |

ACCEPTED execution (post-ACCEPTED): `TASK-PC-BE-001` (console-bff skeleton) + `TASK-PC-FE-011` (MVP Operator Overview dashboard) — both **future** tasks; Phase 7 `console-bff` skeleton stays unstarted until ACCEPTED.

---

## 7. Provenance

- HARDSTOP-09 #2 (`platform/hardstop-rules.md`) — mandate for an ADR + PAUSE-until-ACCEPTED on an undocumented cross-service architecture decision (8 axes here).
- HARDSTOP-04 (`platform/hardstop-rules.md`) — the two ADR-MONO-013 amendments in this PR are additive only; D1-D8 byte-unchanged.
- ADR-MONO-003a § D1.1 / § D2.1 — staged-PROPOSED + new child-ADR audit-row sanctioning (one-off § 3 row, § D1 untouched).
- ADR-MONO-013 § D5 / § D6 Phase 7 / § 3.3 — the parent prescriptions this ADR honors.
- ADR-MONO-014 / ADR-MONO-015 — staged-child PROPOSED-then-ACCEPTED frame this ADR mirrors verbatim.
- Phase 7 gate satisfaction (5/5 backend domains live) objectively recorded at 2026-05-20T06:34:34Z (TASK-PC-FE-010 close PR #661 mergeCommit `eaa1de51`).

분석=Opus 4.7 / 구현=Opus 4.7 (staged-ADR governance precision; D1-D8 PROPOSED-direction reasoning under HARDSTOP-04/09 discipline; dispatcher-direct per ADR-008/013/014/015/016 PROPOSED).
