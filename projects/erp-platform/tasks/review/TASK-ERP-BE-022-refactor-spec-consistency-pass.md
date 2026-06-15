# TASK-ERP-BE-022 — refactor-spec consistency pass over erp-platform specs

**Status:** review

**Type:** TASK-ERP-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (doc-only, meaning-preserving)

---

## Goal

Run a `/refactor-spec` (structure / consistency / clarity / dead-reference / naming) audit over all 13 erp-platform spec files and apply the **meaning-preserving** fixes found. This is structural polish only — no requirement, contract, API/event schema, status code, or decision changes. Follows the TASK-ERP-BE-021 roles-only alignment (a meaning-changing drift fix); this pass is the pure-form complement.

## Scope

**Audited (13 files):** `specs/services/{masterdata,approval,notification,read-model}-service/architecture.md`, `specs/contracts/http/{masterdata,approval,notification,read-model}-api.md`, `specs/contracts/events/{erp-masterdata-events,erp-approval-events,notification-subscriptions,read-model-subscriptions}.md`, `specs/integration/iam-integration.md`. Baseline = scm/finance sibling specs + `platform/` naming/glossary authorities.

**Applied fix (1):**
- `specs/services/read-model-service/architecture.md` — add the standard intro paragraph ("This document declares the internal architecture of `erp-platform/apps/read-model-service`. All implementation tasks targeting this service must follow this declaration, `platform/architecture-decision-rule.md`, and the rule files indexed by `PROJECT.md`'s declared `domain` (`erp`) and `traits` …") between the title and `## Identity`. read-model-service was the **lone** erp service file omitting this boilerplate that all three siblings (masterdata/approval/notification) and the scm/finance siblings carry. Pure cross-reference boilerplate (architecture-decision-rule.md + PROJECT.md traits) — no service-specific meaning added.

**Out of scope (deliberately not changed):**
- The read-model-service **Provenance blockquote** — siblings carry one, but synthesizing it requires authoring task/PR provenance facts (meaning-bearing). Left to a future authoring task if desired (not a refactor-spec item).
- read-model-service's `## Service Type Compliance` `### rest-api`/`### event-consumer` subsection style and `adapter/inbound|outbound/` layer tree — these describe the actual (dual-type) design and must NOT be normalized to the single-type siblings' prose.
- Per-service self-contained repetition (entitlement-trust dual-accept, Multi-tenancy, Idempotency blocks) — intentional standalone-spec design cross-referencing the authoritative `ADR-MONO-019 § D5` / `rules/domains/erp.md`, **not** an SSOT violation.

## Acceptance Criteria

- **AC-1 (audit clean)** — dead-reference / consistency (list markers, heading levels, table style) / naming / duplication categories each return **zero** actionable findings across the 13 files (verified by full read + relative-link resolution against the filesystem).
- **AC-2 (structure)** — read-model-service/architecture.md now opens with the standard intro paragraph, matching all sibling service files.
- **AC-3 (meaning-preserving)** — no requirement, contract, API/event schema, status code, or decision changed. `git diff` is confined to the single added boilerplate paragraph in one file (+ this task lifecycle).
- **AC-4 (no dead refs introduced)** — the added paragraph references `platform/architecture-decision-rule.md` as inline code (same form the siblings use); no new markdown link, no dead reference.

## Related Specs

- `projects/erp-platform/specs/services/read-model-service/architecture.md` (the edited file)
- `projects/erp-platform/specs/services/{masterdata,approval,notification}-service/architecture.md` (intro-paragraph baseline)

## Related Contracts

- None changed. (`platform/architecture-decision-rule.md` referenced as the authority the intro cross-refs — unchanged.)

## Edge Cases

- **Trait list in the intro** — the intro lists the project-level traits (`internal-system, transactional, audit-heavy`) per `PROJECT.md`; read-model's Identity table separately clarifies it is "not an `audit-heavy` write surface". Both stand: the intro references PROJECT.md's declared traits (correct), the Identity nuance is service-specific (preserved).
- **Provenance omission** — intentionally not synthesized (would author new facts). The intro paragraph alone closes the structure-consistency gap without meaning risk.

## Failure Scenarios

- **F1 — over-normalization** — "fixing" read-model's dual-type heading style or layer tree to match single-type siblings would misdescribe the actual design. Guarded by Out-of-scope + AC-3.
- **F2 — fabricated provenance** — authoring a Provenance block to match siblings would invent task/PR facts. Explicitly excluded.
