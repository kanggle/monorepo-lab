# Task ID

TASK-SCM-BE-014

# Title

SCM inventory-visibility-service architecture.md missing standard sections (S20) — Failure Modes / Testing Strategy / Observability + others absent vs sibling procurement-service

# Status

done

# Owner

backend

# Task Tags

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

Close a GENUINE SCM finding from the 2026-05-15 portfolio audit, reconciled
against the current tree (stale SCM items S3 [HTTP code drift, fixed by
TASK-SCM-BE-010] and S10 [event envelope, both already use `source`+
`schemaVersion`] verified already-closed — NOT in scope).

`specs/services/inventory-visibility-service/architecture.md` (145 lines) is
missing multiple standard service-architecture sections that the sibling
`specs/services/procurement-service/architecture.md` already has. After this
task, `inventory-visibility-service/architecture.md` carries the standard
section set appropriate to its Service Type and traits, structurally
consistent with `procurement-service` and preserving the ADR-MONO-012
canonical `architecture.md` form.

Project-internal — `projects/scm-platform/specs/`.

---

# Scope

## In Scope

Author the missing sections in
`specs/services/inventory-visibility-service/architecture.md`. Verified
present today: `## Identity`, `## Responsibilities`, `## Architecture Style
Rationale`, `## Layer Structure`, `## Service Type Compliance`, `## batch-heavy
First Code`, `## Security`, `## Dependencies`.

Verified **absent** (present in `procurement-service/architecture.md`):
`## Observability`, `## Failure Modes`, `## Testing Strategy`, `## Mandatory
Rule mapping`, `## Trait Rule mapping`, `## Saga / Long-running Flow`,
`## Outbox + audit_log invariants`, `## Idempotency`, `## Multi-tenancy`,
`## References`.

For each absent section: author it **accurately for inventory-visibility-service**
(its real Service Type, its `batch-heavy` nature, its actual dependencies and
event subscriptions) — NOT by copying procurement-service prose. A section that
is genuinely **not applicable** to this service (e.g. if it owns no saga, or is
single-tenant) must still appear with an explicit, justified "N/A — <reason>"
declaration (the portfolio convention: a section is present even when its
content is a reasoned zero-state, per the WMS external-integrations.md
zero-state precedent). Determine the Service Type from this service's
`## Identity` / `## Service Type Compliance` and read the matching
`platform/service-types/<type>.md` to scope each section correctly.

## Out of Scope

- S3 (IDEMPOTENCY_KEY_MISMATCH / optimistic-lock HTTP code drift) — STALE,
  resolved by TASK-SCM-BE-010 (both specs now 422 / 409 CONCURRENT_MODIFICATION).
- S10 (SCM event-contract envelope disagreement) — STALE, both
  `scm-procurement-events.md` and `inventory-visibility-subscriptions.md` use
  identical `source` + `schemaVersion`.
- `procurement-service/architecture.md` — it is the **reference**, not an edit
  target.
- Any `apps/` production code / test (spec-only).
- Restructuring the 8 sections that already exist (only *add* the missing ones;
  do not rewrite present content beyond what consistency requires).

---

# Acceptance Criteria

- [ ] `specs/services/inventory-visibility-service/architecture.md` contains all
      10 currently-absent sections (Observability, Failure Modes, Testing
      Strategy, Mandatory Rule mapping, Trait Rule mapping, Saga / Long-running
      Flow, Outbox + audit_log invariants, Idempotency, Multi-tenancy,
      References), each either substantively authored or an explicit justified
      "N/A — <reason>".
- [ ] Section content is accurate for inventory-visibility-service's actual
      Service Type / `batch-heavy` trait / dependencies (not procurement-service
      copy-paste — a reviewer can tell the prose is service-specific).
- [ ] Structural parity with `procurement-service/architecture.md` section
      ordering where applicable; ADR-MONO-012 canonical form preserved
      (`### Service Type Composition` H3 + Identity table intact).
- [ ] `validate-rules` clean (no new broken links / orphans from new References).
- [ ] No `apps/` diff.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 —
> read `projects/scm-platform/PROJECT.md` (domain=scm, traits include
> `batch-heavy`) and load `rules/common.md` + `rules/domains/scm.md` + the
> declared trait files. Read the `platform/service-types/<type>.md` matching
> inventory-visibility-service's declared Service Type.

- `specs/services/inventory-visibility-service/architecture.md` — edit target.
- `specs/services/procurement-service/architecture.md` (sections at L538/556/
  597/615/638 …) — reference structure (do not edit).
- `specs/services/inventory-visibility-service/` siblings (overview,
  dependencies, any subscriptions spec) — source of accurate per-section
  content.
- `specs/contracts/events/inventory-visibility-subscriptions.md` — input for
  Saga/Outbox/Idempotency sections.
- `rules/traits/batch-heavy.md` — Trait Rule mapping input.

# Related Skills

- `.claude/skills/refactor-spec/SKILL.md` — primary.
- `.claude/skills/validate-rules/SKILL.md` — post-check.

---

# Related Contracts

- `specs/contracts/events/inventory-visibility-subscriptions.md` — referenced
  by the new Saga/Outbox/Idempotency sections; no envelope change.

---

# Target Service

- `inventory-visibility-service`

---

# Architecture

No architecture-style change — this *completes the architecture document* to
the standard section set. Preserve ADR-MONO-012 canonical form.

---

# Implementation Notes

1. Author each section from this service's real specs/deps — copy-paste from
   procurement-service is a failure mode (different Service Type / role).
2. "N/A — <reason>" is acceptable and expected for sections that do not apply
   (zero-state convention) — but the reason must be specific, not boilerplate.
3. Spec-only; "(writing) → ready" stage — this spec PR adds the task to
   `ready/` + SCM INDEX only.

---

# Edge Cases

- inventory-visibility-service is `batch-heavy` + likely a read/projection
  service — `## Saga / Long-running Flow` and `## Outbox + audit_log invariants`
  may legitimately be "N/A" or minimal; state why rather than fabricating a
  saga.
- `## Multi-tenancy` — if SCM is single-tenant for this service, declare it
  explicitly rather than omitting the section.

# Failure Scenarios

- Copying procurement-service's saga/outbox prose → asserts flows this service
  does not have; reviewer rejects on accuracy.
- Adding `## References` links to non-existent files → new broken links;
  `validate-rules` must pass.
- Reordering/altering the canonical Identity/Service-Type-Composition block →
  ADR-MONO-012 form regression.

---

# Test Requirements

- Spec-only. Verification: all 10 sections present (authored or justified N/A),
  service-specific accuracy on read, canonical form preserved,
  `validate-rules` clean, no `apps/` diff.

---

# Definition of Done

- [ ] All 10 missing sections added (substantive or justified N/A), accurate
      for this service
- [ ] Structural parity w/ procurement-service; ADR-MONO-012 form preserved
- [ ] `validate-rules` clean; no `apps/` diff
- [ ] Branch: `task/scm-be-014-inventory-visibility-arch-sections`
      (substring `master` 금지)
- [ ] Spec PR adds this file to `ready/` + SCM INDEX ready list only
- [ ] Ready for review
