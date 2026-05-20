# Task ID

TASK-MONO-125

# Title

ADR-MONO-017 PROPOSED authoring — platform-console-bff Architecture (Phase 7 Aggregation & Cross-Domain Dashboards)

# Status

ready

# Owner

architecture

# Task Tags

- adr

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **prerequisite gate met**: ADR-MONO-013 § D6 **Phase 7** (`console-bff fan-out + cross-domain dashboards`) requires **5 domains live**. Satisfied 2026-05-20 — TASK-PC-FE-010 erp console section close PR #661 squash `eaa1de51` mergedAt 2026-05-20T06:34:34Z brings the non-GAP federation count to **5** (GAP + wms + scm + finance + erp). The Phase 6 § D6 deliverable is COMPLETE (ADR-MONO-013 § 6 has the "Additive note — Phase 6 COMPLETE" stanza, `grep -c "Additive note"` = 4).
- **governed by**: ADR-MONO-013 § D5 ("`rest-api` added when the console-bff (Phase 7) lands") + § D6 Phase 7 (gate + model) + § 3.3 (zero-retrofit assumption — Phase 7 design must not retroactively change the FE-007/008/009/010 per-domain credential rule). ADR-MONO-013 is the **authoritative parent**; this PROPOSED ADR is its **Phase 7 staged child** (sibling pattern: ADR-MONO-014 Operator Token Exchange + ADR-MONO-015 Dashboards Model — both child ADRs of ADR-013).
- **staged PROPOSED → ACCEPTED**: this task authors PROPOSED **only**. The ACCEPTED transition is a separate follow-up task (sibling pattern: ADR-008 TASK-MONO-071 PROPOSED + TASK-MONO-113 ACCEPTED; ADR-014 TASK-MONO-109 PROPOSED + TASK-MONO-110 ACCEPTED; ADR-015 TASK-MONO-111 PROPOSED + TASK-MONO-112 ACCEPTED; ADR-016 TASK-MONO-117 PROPOSED + TASK-MONO-118 ACCEPTED). PROPOSED records the decision direction + sketches D1-D8 options + sequences the downstream work; ACCEPTED authorizes execution on user-explicit intent (§ D6.1).
- **no implementation in this task** — pure doc-only ADR + task lifecycle. The actual `console-bff` service skeleton + integration-contract spec + dashboards composition implementation are all **future tasks** (after ACCEPTED). This PROPOSED scoping is identical to ADR-MONO-008/013/014/015/016 PROPOSED.

# Goal

Author **ADR-MONO-017 PROPOSED** — `platform-console-bff` Architecture (Phase 7 Aggregation & Cross-Domain Dashboards) — under the staged ADR pattern. ADR-MONO-013 § D6 Phase 7 gate (5/5 domains live) is satisfied; the next step is the **architecture decision** for what `console-bff` *is*, **before** any implementation. Identical pattern to ADR-MONO-014 (Phase 2 operator-auth) + ADR-MONO-015 (Phase 2 dashboards) — both PROPOSED as the precondition for their phase's implementation, not the implementation itself.

The ADR will record the open decision space the **5/5-domains-live** gate now exposes:

1. **D1 — Aggregation pattern** — REST orchestrator vs GraphQL gateway vs gRPC vs hybrid; the per-domain credential reuse implication of each (per-domain GAP-OIDC bearer is BFF-side stitched, not client-shimmed).
2. **D2 — Federation shape** — server-side fan-out (affirms ADR-MONO-013 § D5) vs client-side composition (rejected by ADR-013); the BFF's role as composer.
3. **D3 — Cross-domain dashboards composition** — reuse existing per-domain read APIs (zero-retrofit affirmation) vs introduce new aggregating endpoints on each domain (retrofit risk).
4. **D4 — Operator credential / tenant scoping** — per-domain credential rule (§ 2.4.5/6/7/8) extended to BFF, or BFF carries a single SSO token and translates per-domain (operator-token vs GAP-OIDC).
5. **D5 — Resilience / per-domain circuit-breaker** — pattern reuse from § 2.5 / FE-001..010 (per-section degrade, finance flat envelope, no fabricated 429, …) vs BFF-level aggregation degrade strategy.
6. **D6 — Multi-tenant isolation in BFF** — `tenant_id` pass-through (preferred — affirms producer-side gate authority) vs BFF re-derivation (retrofit on every domain — rejected).
7. **D7 — Observability** — BFF fan-out latency + per-domain error attribution + tracing (reuse existing observability stack vs new aggregation).
8. **D8 — Phasing** — MVP scope (which 2-3 cross-domain dashboards land first) vs full Phase 7 (all 5 domains' aggregations).

The ADR-MONO-017 PROPOSED is the spec-first artifact that prevents an implementation-time HARDSTOP-09 (e.g. a `console-bff` skeleton task picking GraphQL silently and committing the monorepo to it). It is the same prevention discipline that ADR-014/015 applied to Phase 2.

# Scope

## In Scope (PROPOSED only, doc-only)

- **Author `docs/adr/ADR-MONO-017-platform-console-bff-architecture.md`** — `Status: PROPOSED`, full D1–D8 decision frame, ADR-014/015 staged pattern verbatim (Decision driver + Supersedes/Amends/Reconciles header; § 1 Context + 1.3 Staged PROPOSED→ACCEPTED narrative; § 2 Decision with D1–D8 tables sketching options + "CHOSEN (PROPOSED direction)" rows where the user-explicit answer below preselects, "TBD at ACCEPTED" otherwise; § 3 Consequences; § 4 Alternatives Considered; § 5 Relationship to ADR-013 / 014 / 015; § 6 Status Transition History with PROPOSED row created in this PR; § 7 Provenance). Substance constraints: **PROPOSED records direction + frame + sequence**, not the implementation; competing options must be honestly named (FE-007/008/009/010 per-domain credential rule may NOT be retroactively redefined — D4 must record that constraint as a hard invariant the chosen direction inherits).
- **Amend ADR-MONO-013** — additive only, two amendments:
  1. § D5 (line 76) — append a clarifying parenthetical *"(scoped by ADR-MONO-017 PROPOSED 2026-05-20)"* to the existing *"`rest-api` added when the console-bff (Phase 7) lands"* statement. No D1-D8 ADR-013 change.
  2. § History — add a new additive blockquote (5th occurrence of *"Additive note"*) referencing ADR-MONO-017 PROPOSED and the 5/5-domains-live gate, identical shape to the Phase 4/5/6 backfill notes (HARDSTOP-04 additive discipline; D1-D8 byte-unchanged).
- **ADR-MONO-003a § 3 audit row append** (one-off, per ADR-MONO-003a § D1.1 sanctioned categories — this is a child-ADR creation of ADR-013, falling under the same "new ADR creation" category as ADR-014/015 entries already there).
- **Task lifecycle** — author this task to `tasks/ready/` + register in root `tasks/INDEX.md` ready list (spec PR); spec edits + lifecycle `ready → review` (impl PR); close chore `review → done`. Root strict PR Separation Rule.

## Out of Scope

- ACCEPTED transition (separate follow-up task `TASK-MONO-126` or similar, identical to MONO-118 / MONO-110 / MONO-112 / MONO-113 pattern).
- The actual `console-bff` service skeleton (a future post-ACCEPTED task — `platform-console` project-internal, will introduce `apps/console-bff/` + a new `architecture.md` + a new spec contract).
- Implementing any cross-domain dashboards (post-ACCEPTED future task).
- `projects/platform-console/PROJECT.md` `service_types` mutation (adding `rest-api` — this happens when the `console-bff` skeleton task lands, not on PROPOSED).
- Any code anywhere; any changes outside `docs/adr/` + root `tasks/`.

# Acceptance Criteria

1. `docs/adr/ADR-MONO-017-platform-console-bff-architecture.md` exists with `Status: PROPOSED`, ADR-014/015 staged frame verbatim, D1–D8 honest option enumeration, the PROPOSED-direction CHOSEN rows correctly preselected per the user-confirmable defaults below (see Implementation Notes), and the HARDSTOP-04 invariant (no ADR-013 D1-D8 changed) preserved.
2. ADR-MONO-013 amended additively (2 amendments only): § D5 L76 parenthetical + § History new additive blockquote. `grep -c "Additive note" docs/adr/ADR-MONO-013-...md` = **5** (FE-006 + Phase 4 + Phase 5 + Phase 6 + new Phase 7 PROPOSED). § D1–D8 + § 6 table rows + § 7 byte-identical (`git diff` confirms additive-only).
3. ADR-MONO-003a § 3 audit row appended (one-off for new child ADR creation, identical category to existing ADR-014/015 rows; § D1 NOT mutated — append-only); rows 1..N byte-unchanged.
4. Task file in `tasks/ready/` with all Required Sections; root `tasks/INDEX.md` `## ready` lists this task (one-line entry); other INDEX sections byte-stable.
5. No code touched (no `apps/`, no `libs/`, no platform-console code, no projects/code); no `console-bff` service file created.
6. PROPOSED narrative is **honest** — D4 (operator credential) records the FE-007/008/009/010 per-domain credential rule as a hard invariant the chosen direction inherits, never retroactively redefined. D2 (federation shape) affirms ADR-MONO-013 § D5 server-side fan-out (not re-decides). D3 (dashboards composition) flags the zero-retrofit constraint (§ 3.3 — Phase 7 is its fifth confirmation).
7. PROPOSED → ACCEPTED transition is **explicitly deferred** to a separate follow-up task in § 1.3 (sibling pattern to ADR-014 § 1.3 / ADR-015 § 1.3).

# Related Specs

> Target = monorepo root + `docs/adr/`. Governing: ADR-MONO-013 (parent) + ADR-MONO-003a (ADR governance) + `platform/hardstop-rules.md` HARDSTOP-04 / HARDSTOP-09.

- `docs/adr/ADR-MONO-013-platform-console-foundation.md` § D5 (BFF service-types ext) + § D6 Phase 7 (gate + model) + § 3.3 (zero-retrofit assumption) — parent
- `docs/adr/ADR-MONO-014-platform-console-operator-auth-token-exchange.md` — staged-PROPOSED frame precedent (sibling Phase-2 ADR)
- `docs/adr/ADR-MONO-015-platform-console-dashboards-model.md` — staged-PROPOSED frame precedent (sibling Phase-2 ADR; Composed-overview pattern that Phase 7 generalises across domains)
- `docs/adr/ADR-MONO-003a-meta-policy.md` § D1.1 / § D2.1 / § 3 (audit row append rule)
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.5 / § 2.4.6 / § 2.4.7 / § 2.4.8 (the per-domain credential rule + flat-envelope + read-only + no-fabricated-429 discipline that Phase 7 must inherit, not re-derive)
- `projects/platform-console/specs/services/console-web/architecture.md` (Layered-by-Feature pattern Phase 7 extends)
- `projects/global-account-platform/specs/contracts/http/console-registry-api.md` (operator token surface; BFF must respect)
- `projects/{wms,scm,finance,erp}-platform/specs/...` — the 4 non-GAP domain read surfaces the BFF will aggregate (cross-ref only, not changed)
- `platform/hardstop-rules.md` — HARDSTOP-04 (additive note discipline on parent ADR) + HARDSTOP-09 (architecture decision precedes code)

# Related Contracts

- **None changed by this task** (PROPOSED is pure doc).
- **Cross-referenced** (post-ACCEPTED future scope): `console-integration-contract.md` (new § for BFF endpoints), `console-bff/architecture.md` (new file at ACCEPTED), per-domain consumer wiring (no per-domain producer change — zero-retrofit).

---

# Target Service

- N/A (PROPOSED authoring is doc-only, monorepo-level — no service touched).
- Future scope: `platform-console` will gain a `console-bff` service (`rest-api`) at the ACCEPTED execution; that introduction is **not** in this task.

---

# Implementation Notes

- **PROPOSED CHOSEN direction (user-confirmable, but pre-sketched to keep the PR concrete)** — recorded here so the dispatcher can author the ADR D1-D8 CHOSEN rows correctly and the user can confirm/adjust:
  - **D1 (Aggregation pattern)**: **B. REST orchestrator** — composes existing per-domain read APIs server-side, no GraphQL gateway, no new producer endpoints. Smallest blast radius, zero-retrofit-preserving, identical philosophy to ADR-015 Composed-overview pattern generalised across domains. (A. GraphQL = larger scope, schema-stitch retrofit cost; C. gRPC = wrong layer for BFF.)
  - **D2 (Federation shape)**: **A. Server-side fan-out only** — affirms ADR-MONO-013 § D5; the BFF is the composer; the console-web (`frontend-app`) calls BFF endpoints, never per-domain endpoints directly (except the existing per-domain console-web direct path for single-domain sections — those stay; the BFF *adds* the cross-domain aggregation surface, doesn't replace per-domain sections).
  - **D3 (Dashboards composition)**: **A. Reuse existing per-domain read APIs verbatim** — § 3.3 "zero retrofit" affirmed for the fifth time; no per-domain producer change.
  - **D4 (Operator credential)**: **A. Per-domain credential rule extended verbatim** — the BFF carries the same per-domain credential rule (§ 2.4.5/6/7/8): GAP-domain uses operator-token (§ 2.6 RFC 8693 exchange), non-GAP domains use the GAP OIDC access token. BFF is the credential dispatcher; the rule is not re-derived.
  - **D5 (Resilience)**: **A. Per-domain circuit-breaker inherited from § 2.5** — per-section/per-domain degrade discipline carries from FE-007/008/009/010; BFF aggregation degrade means "the domains that responded are rendered; the failed domain shows a per-section degraded card", never "the whole dashboard blanks".
  - **D6 (Multi-tenant)**: **A. `tenant_id` claim pass-through** — BFF forwards the operator's JWT `tenant_id` claim verbatim to each domain; producer-side `TenantClaimValidator` gates each call (no BFF re-derivation; preserves the GAP/wms/scm/finance/erp producer-authoritative rule).
  - **D7 (Observability)**: **A. Per-domain fan-out attribution** — BFF metrics (fan-out latency, per-domain error rate, aggregation degrade count) + tracing span per domain; reuse existing observability stack (ADR-MONO-006 vector + VictoriaMetrics).
  - **D8 (Phasing)**: **MVP = 1 cross-domain dashboard** ("Operator Overview" — the GAP `admin-web` Phase 2 composed-overview ADR-015 generalisation to 5 domains). Full Phase 7 = future tasks.

  These CHOSEN rows are the **conservative defaults** consistent with ADR-MONO-013 § 3.3 + the FE-007..010 contract. The PROPOSED ADR will record them as "CHOSEN (PROPOSED direction; finalised at ACCEPTED)", same as ADR-014/015 PROPOSED rows.

- The PROPOSED ADR is doc-only and 200~300 lines (ADR-014 was 175L; ADR-015 was 250L). Single PR per stage; root strict PR Separation Rule applies (spec author / impl PR / close chore).
- HARDSTOP-04 discipline: ADR-013 D1-D8 byte-unchanged; the two ADR-013 amendments are additive only (§ D5 parenthetical clarification + § History additive blockquote).
- HARDSTOP-09: this PROPOSED is the **prevention** of a Phase 7 implementation-time HARDSTOP-09 — exactly the same role ADR-014/015 played for Phase 2.
- Recommended impl model: **Opus** (cross-cutting architecture decision authoring; staged-ADR governance precision; PROPOSED CHOSEN-direction reasoning must honor § 3.3 zero-retrofit and the per-domain credential rule). Dispatcher authors directly (ADR governance — not agent-dispatched, identical to ADR-008/013/014/015/016 PROPOSED).
- Branch name must NOT contain the `master` substring.

---

# Edge Cases

1. A reader interprets PROPOSED CHOSEN rows as binding → the ADR header + § 1.3 must explicitly state "CHOSEN (PROPOSED direction; finalised at ACCEPTED)" identical to ADR-014/015 PROPOSED wording.
2. A reader interprets D4 as re-opening the per-domain credential rule → the D4 prose must record the rule as a **hard invariant** the chosen direction inherits, never retroactively redefined (FE-007..010 byte-unchanged).
3. A future implementer reads ADR-017 PROPOSED and starts the `console-bff` skeleton → forbidden; § 1.3 + Consequences must explicitly state that the skeleton is a **separate future post-ACCEPTED task**, identical to ADR-014's `TASK-BE-298` deferral.
4. ADR-MONO-013 § D5 is mis-read as already PRESCRIBING the BFF design → no; § D5 only says `rest-api` is added when console-bff lands. ADR-017 PROPOSED scopes the *what* (D1-D8) that § D5 deferred.

# Failure Scenarios

- ADR-MONO-013 D1-D8 mutated (HARDSTOP-04 violation) → reject; the two ADR-013 amendments are additive only (§ D5 parenthetical + § History blockquote).
- PROPOSED ADR omits the FE-007..010 per-domain credential rule as a hard invariant in D4 → reject; D4 must record the inherited rule.
- PROPOSED ADR commits to a CHOSEN direction without sketching alternatives — the alternatives must be honestly named and reasoned (ADR-014/015 verbatim discipline).
- The `console-bff` service skeleton is created in this PR → out of scope; this is a doc-only PROPOSED-authoring task.
- `projects/platform-console/PROJECT.md` `service_types` mutated (adding `rest-api`) → out of scope; that happens at the skeleton task, not on PROPOSED.
- The ACCEPTED transition is performed in this task → no; PROPOSED ONLY, ACCEPTED is a separate follow-up task (sibling precedent ADR-008/014/015/016 staged pattern).
- Cross-project change leaks (per-domain producer specs touched) → reject; zero-retrofit is the ADR-017 PROPOSED's CORE invariant.

---

# Test Requirements

- `git diff` confirms: only `docs/adr/ADR-MONO-017-...md` (new) + `docs/adr/ADR-MONO-013-...md` (additive amendments) + `docs/adr/ADR-MONO-003a-...md` (§ 3 row append) + root `tasks/INDEX.md` + task lifecycle file changed.
- `grep -c "Additive note" docs/adr/ADR-MONO-013-...md` = **5**.
- ADR-MONO-013 § 1–5 + § 7 byte-identical; § D1–D8 + § 6 existing rows byte-identical; § History gets exactly one new additive blockquote.
- ADR-MONO-003a § D1 byte-unchanged; § 3 gets exactly one new row.
- No code/build/test impact (doc-only); CI markdown fast-lane expected (sibling: MONO-117/MONO-123 markdown-only).

---

# Definition of Done

- [ ] `docs/adr/ADR-MONO-017-platform-console-bff-architecture.md` authored (Status PROPOSED, full D1-D8 frame, ADR-014/015 staged shape verbatim)
- [ ] ADR-MONO-013 additive amendments landed (§ D5 parenthetical + § History blockquote); `grep -c "Additive note"` = 5; D1-D8 byte-identical
- [ ] ADR-MONO-003a § 3 audit row append (one-off; § D1 byte-unchanged)
- [ ] Task lifecycle: ready → review → done (3-PR sequence per root strict PR Separation Rule)
- [ ] Cross-references resolve; no code/projects/build/test touched
- [ ] PROPOSED → ACCEPTED transition is **explicitly deferred** to a separate future task
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus** (분석=Opus 4.7 / 구현 권장=Opus 4.7) — staged-ADR governance precision; D1-D8 PROPOSED-direction reasoning under HARDSTOP-04/09 discipline; identical agent-not-dispatched discipline as ADR-008/013/014/015/016 PROPOSED.
- **분량**: medium-large — ADR-017 ~250 line + ADR-013 2 additive amendments + ADR-003a § 3 row + task lifecycle/INDEX.
- **dependency**: 선행 = Phase 6 COMPLETE (FE-010 #661 `eaa1de51` merged 2026-05-20T06:34:34Z) — 5/5 domains live gate satisfied. 후속 = ACCEPTED transition task (separate; sibling MONO-118/110/112/113 pattern).
- **PR Separation**: root `tasks/INDEX.md` strict — spec PR / impl PR / close chore distinct.
