# Task ID

TASK-BE-292

# Title

Ecommerce contracts hygiene (decision-bearing) — empty contracts/schemas/ vs 19 inline envelope duplicates (E11) + unanchored wishlist feature (E22)

# Status

done

# Owner

backend

# Task Tags

- api
- event
- adr

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

# Goal

Close two GENUINE ecommerce contracts-layer findings that each require an
explicit **decision** (not a mechanical fix), reconciled and confirmed against
the current tree.

**E11** — `specs/contracts/schemas/` contains only a `README.md` describing an
*intended* shared-schema state that was never fulfilled, while the error/event
envelope is copy-pasted inline across contract files: **11 HTTP** error-envelope
instances (the identical `code`/`message`/`timestamp` block in all 11 HTTP
contract files, e.g. `wishlist-api.md:134`, `order-api.md:195`) + **8 event**
envelope instances (the identical `event_id`/`event_type`/`source`/`occurred_at`/
`version`/`payload` skeleton in all 8 event files, e.g. `payment-events.md:9`,
`order-events.md:9`) = **19 duplicates**.

**E22** — `specs/contracts/http/wishlist-api.md` exists but there is **no**
`specs/features/<wishlist>` feature spec and **no** `specs/use-cases/<wishlist>`
use-case spec; wishlist appears only in the contract + two `user-service`
spec mentions. The contract is **unanchored** (no requirement traceability).

After this task: the schemas/ directory either fulfills its README promise OR
the false promise is removed (decided, documented); and wishlist is either
properly anchored to feature+use-case specs OR the unanchored contract is
removed/marked (decided, documented).

Project-internal — all paths under
`projects/ecommerce-microservices-platform/specs/`.

---

# Scope

## In Scope — two decisions + the chosen execution

**WI-1 — E11 decision: hoist vs accept-and-remove-promise.**
Decide between:
- **(A) Hoist**: create the real shared schema file(s) under
  `specs/contracts/schemas/` (one HTTP error-envelope schema + one event
  envelope schema) and replace the 19 inline copies with a reference/include
  pointer to the canonical schema.
- **(B) Accept inline + remove false promise**: delete the empty-promise
  `schemas/README.md` (or rewrite it to state "envelopes are defined inline per
  contract by deliberate choice") so the directory no longer advertises an
  unfulfilled shared-schema model.

Decision basis: how the other portfolio projects handle envelope reuse, the
`refactor-spec` no-meaning-change principle, and whether contract tooling
consumes `schemas/`. Record the decision + rationale in the impl PR (and, if
it rises to a cross-cutting convention, propose an ADR rather than deciding
unilaterally — escalate per ADR governance).

**WI-2 — E22 decision: anchor vs retire.**
Determine whether wishlist is an intended v1 ecommerce capability:
- **(A) Anchor**: author the missing `specs/features/wishlist*` feature spec
  and `specs/use-cases/wishlist*` use-case spec so `wishlist-api.md` has
  upstream requirement traceability (consistent with how other ecommerce
  features are anchored).
- **(B) Retire/mark**: if wishlist is a stale/aspirational contract with no
  product intent, mark `wishlist-api.md` as deferred/not-v1 (or remove it)
  consistent with the project's freeze policy.

Cross-check `specs/services/user-service/architecture.md:80` and
`user-service/dependencies.md:18,21` (which already model `wishlist_items` +
product-service enrichment) — those mentions inform whether wishlist is real.

## Out of Scope

- Any `apps/` production code / test (spec-only — even WI-1(A) hoist is a spec
  refactor, not a Java change).
- Changing any envelope *field* (E11 is dedup of identical blocks — the schema
  content must remain semantically identical; no field add/remove/rename).
- The ecommerce standalone-v1 freeze policy itself (TASK-MONO-028) — WI-2(B)
  applies it, does not amend it.
- The G6/E6 deprecated-auth cleanup (separate task TASK-BE-291).

---

# Acceptance Criteria

- [ ] WI-1: a decision (A or B) is recorded with rationale. If (A): exactly one
      canonical HTTP error-envelope schema + one event envelope schema exist
      under `schemas/`, and all 11 HTTP + 8 event contracts reference it (0
      remaining inline duplicate definitions — verified by grep). If (B):
      `schemas/` no longer advertises an unfulfilled model (README removed or
      rewritten to state the inline-by-choice convention).
- [ ] WI-1: zero envelope-field semantic change (a field-level diff of any
      contract's effective envelope = empty).
- [ ] WI-2: a decision (A or B) is recorded with rationale. If (A):
      `specs/features/wishlist*` + `specs/use-cases/wishlist*` exist and
      `wishlist-api.md` traces to them. If (B): `wishlist-api.md` carries an
      explicit deferred/not-v1 marker (or is removed) consistent with the
      freeze policy, and no spec references it as v1-live.
- [ ] No new broken links / orphans (`validate-rules` clean).
- [ ] No `apps/` diff.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 —
> read `projects/ecommerce-microservices-platform/PROJECT.md` and load
> `rules/common.md` + declared domain/trait files. Unknown tags = Hard Stop.

- `specs/contracts/schemas/README.md` — WI-1 the unfulfilled-promise artifact.
- `specs/contracts/http/*.md` (11 files, e.g. `wishlist-api.md:134`,
  `order-api.md:195`) — WI-1 HTTP envelope duplicates.
- `specs/contracts/events/*.md` (8 files, e.g. `payment-events.md:9`,
  `order-events.md:9`) — WI-1 event envelope duplicates.
- `specs/contracts/http/wishlist-api.md` — WI-2 unanchored contract.
- `specs/services/user-service/architecture.md` (L80),
  `user-service/dependencies.md` (L18,21) — WI-2 wishlist reality check.
- `tasks/done/` ecommerce freeze-policy precedent (TASK-MONO-028) — WI-2(B)
  input.

# Related Skills

- `.claude/skills/refactor-spec/SKILL.md` — primary (dedup w/o meaning change;
  anchor authoring).
- `.claude/skills/design-api/SKILL.md` — if WI-1(A) formalizes a shared schema
  contract.
- `.claude/skills/validate-rules/SKILL.md` — post-check.

---

# Related Contracts

- All 11 `specs/contracts/http/*.md` + 8 `specs/contracts/events/*.md`
  (WI-1 touches the envelope section of each — reference, not redefinition).
- `specs/contracts/http/wishlist-api.md` (WI-2).

---

# Target Service

- N/A (cross-service contracts layer + feature/use-case specs)

---

# Architecture

No service architecture change. WI-1(A) introduces a shared-schema reference
convention within `specs/contracts/`; WI-1(B) removes a misleading promise.
WI-2 is requirement-traceability hygiene.

---

# Implementation Notes

1. **Decision-bearing** — do not pre-commit to (A)/(B) in this task file; the
   implementer must record the chosen option + rationale in the impl PR. If a
   decision establishes a portfolio-wide convention (likely for E11 envelope
   strategy), raise an ADR instead of deciding only for ecommerce — escalate
   per ADR governance (ADR-MONO precedent), do not silently set a monorepo
   convention from one project.
2. E11 must be **semantically inert**: the dedup cannot change any envelope
   field. Diff effective envelopes before/after = empty.
3. WI-1 and WI-2 are independent; may bundle one spec PR or split — case-by-case
   (`feedback_pr_bundling`).
4. "(writing) → ready" stage — this spec PR adds the task to `ready/` +
   ecommerce INDEX only.

---

# Edge Cases

- WI-1: a contract whose envelope has a *legitimate* extra field beyond the
  common skeleton → it is NOT a pure duplicate; keep its delta, reference only
  the common base. Identify such cases before bulk-replacing.
- WI-1(A): if no include/transclusion mechanism exists for markdown contracts
  in this repo, "reference" = a canonical section + explicit "envelope: see
  schemas/…" pointer (not a broken `$ref`).
- WI-2: wishlist may be partially real (user-service models it) but lack the
  feature/use-case layer → (A) anchor is then correct, not (B) retire.

# Failure Scenarios

- WI-1 dedup silently drops a field present in one contract's envelope →
  contract regression; the semantic-inertness check must catch it.
- WI-1(B) removes the README but leaves 19 inline copies undocumented as
  intentional → the inconsistency persists with no rationale; (B) requires the
  "inline by choice" statement.
- WI-2(B) marks wishlist deferred while user-service still models
  `wishlist_items` as live → new internal contradiction; reconcile both or
  choose (A).

---

# Test Requirements

- Spec-only. Verification:
  - WI-1: grep inline-envelope-definition count == (0 if A / unchanged+documented
    if B); envelope field diff == empty.
  - WI-2: feature+use-case existence (A) or deferred marker + no v1-live ref (B).
  - `validate-rules` clean; no `apps/` diff.

---

# Definition of Done

- [ ] WI-1 decision recorded + executed (hoist→0 inline / accept→promise removed),
      semantically inert
- [ ] WI-2 decision recorded + executed (anchored / retired), no contradiction
      with user-service model
- [ ] ADR raised if a portfolio-wide convention was set (not decided unilaterally)
- [ ] `validate-rules` clean; no `apps/` diff
- [ ] Branch: `task/be-292-contracts-hygiene` (substring `master` 금지)
- [ ] Spec PR adds this file to `ready/` + ecommerce INDEX ready list only
- [ ] Ready for review
