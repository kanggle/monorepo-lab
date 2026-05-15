# ADR-MONO-012a — Cross-Project `architecture.md` Canonical Form, Corrections

**Status:** ACCEPTED
**Date:** 2026-05-15
**History:** ACCEPTED 2026-05-15 (TASK-MONO-103 — forward-pointer ADR closing the 3 option-C-1 corrections accumulated against [ADR-MONO-012](ADR-MONO-012-cross-project-architecture-md-canonical-form.md) during the D3 migration cycle MONO-094 → MONO-101).
**Decision driver:** Option C-1 (audit-only ADR correction) accumulated 3 distinct wording corrections against ADR-MONO-012 — one per implementation cycle (MONO-095 SCM, MONO-097 GAP, MONO-098 ecommerce, MONO-101 fan-platform). Per memory `project_e2e_3phase_strategy_complete.md` § "option C-1 audit-only" pattern, ADR body is **immutable after ACCEPTED** (corrections live in INDEX outcome lines until a forward ADR consolidates them). Three corrections is the threshold where a forward ADR becomes more authoritative than scattered outcome-line audit-trail.
**Supersedes:** none. ADR-MONO-012 remains the canonical decision record; this ADR is an authoritative correction layer.
**Related:** [ADR-MONO-012](ADR-MONO-012-cross-project-architecture-md-canonical-form.md) (target — body untouched per option C-1), [ADR-MONO-009](ADR-MONO-009-chrome-devtools-mcp-visual-regression.md) (PROPOSED ADR template pattern reference), [ADR-MONO-003a](ADR-MONO-003a-d4-override-scope-canonicalization.md) § D1.1 (governance polish under D4 OVERRIDE).

---

## 1. Context

ADR-MONO-012 was authored at the start of the D3 migration cycle (TASK-MONO-092 PROPOSED → MONO-094 ACCEPTED, both 2026-05-15). During execution of the migration batches (MONO-095 SCM 3, MONO-097 GAP 8, MONO-098 ecommerce 14, MONO-101 fan-platform 4 catch-up + MONO-096 HARDSTOP-10 hook fixture), three statements in the ADR body were observed to be **factually inaccurate relative to the implementation state at acceptance**. Each was captured as an INDEX outcome line audit-trail per option C-1 (ADR body untouched).

The three inaccuracies are individually minor (wording, not decision substance), but their accumulation across 3+ implementation cycles justifies promotion from scattered audit-trail to a single authoritative correction record.

## 2. Decision

The three corrections below are **authoritative** — when reading ADR-MONO-012, treat this ADR as the post-implementation correction layer. ADR-MONO-012 body remains immutable for historical-record purposes (decision-rationale at acceptance time is preserved verbatim).

### Correction 1 — Ecommerce service count: 13 → 14

**ADR-MONO-012 affected wording**: § 1.1, § 1.5 (line 59), § D2 (line 113), § 2 (line 134) all state "ecommerce 13" when referring to the ecommerce-microservices-platform service count.

**Actual count**: **14** (admin-dashboard / auth-service-deprecated / batch-worker / gateway-service / notification-service / order-service / payment-service / product-service / promotion-service / review-service / search-service / shipping-service / user-service / web-store).

**Why missed at ADR authoring**: The pre-implementation grep used to enumerate the projects undercounted the `auth-service-deprecated` directory — deprecated but still spec-resident, so it was eligible for canonical migration. MONO-098 closure verified the true count (`grep -c "^## Identity$" projects/ecommerce-microservices-platform/specs/services/*/architecture.md` = 14 post-migration).

**Effect on decision**: None — the migration was scoped by `grep`, not by the ADR's literal count. D3 ordering (SCM → GAP → ecommerce) still holds; the cost estimate ("substantial enough that user must commit cycle budget" in § D2) is correct.

### Correction 2 — HARDSTOP-10 hook scope: "WMS-only" → cross-project

**ADR-MONO-012 affected wording**: § 1.4 ("WMS service architecture.md only"), § D4 (line 117: "currently WMS-only"), § 4 (line 181: "HARDSTOP-10 hook stays WMS-only").

**Actual behavior**: The hook's detection logic is **project-agnostic**. The regex `^projects/(?<proj>[^/]+)/specs/services/(?<svc>[^/]+)/architecture\.md$` matches every project's architecture.md; the Service Type heading check `(?im)(?:^#+\s*Service\s+Type|\*\*Service\s+Type\*\*\s*[:|])` is content-pattern-based, not path-scoped. The hook fires on any project's architecture.md edit when the post-edit content lacks a recognised Service Type declaration in the catalog.

**Why authored as "WMS-only"**: At ADR authoring time, the only observed hook fires (BE-150 / BE-154 / BE-161) had been on WMS architecture.md files. The author inferred path-scoping from sample frequency rather than reading the hook source — a classic confirmation-bias trace.

**Effect on decision**: § D4 ("hook propagation별 task 분리") was based on the incorrect premise that propagation requires hook source modification. The actual implication is the opposite: the hook **already fires cross-project**, so D3 migration must produce canonical-form-compatible files in every project to avoid post-migration hook regressions. MONO-096 added the canonical-form negative-case fixture to guard exactly this case — what § D4 framed as future work was already enforced by the existing hook.

### Correction 3 — `### Service Type Composition` H3 presence: "required when dual" → always present

**ADR-MONO-012 affected wording**: § D1 specifies the H3 is "required when the service has dual Service Type composition (e.g. rest-api + event-consumer)". § 1.6 (line 214) reinforces: "fan currently has Identity table but not `### Service Type Composition` (all single-type). May or may not need backfill depending on D1 interpretation ('required when dual' → fan skip)."

**Actual practice**: The H3 is **always present** in the WMS-form canonical baseline — single-type services carry a short H3 body declaring the single type, dual-type services carry a longer body splitting primary/secondary surfaces. Operationally, this is required because the HARDSTOP-10 hook's heading regex (`^#+\s*Service\s+Type`) needs *some* `Service Type`-prefixed heading to find the catalog-value tail; the Identity-table row alone is line-start `|` and does not match.

**Why authored as "required when dual"**: WMS's `inventory-service` was the original canonical-form sample, and its H3 was authored to *explain* the dual-type composition. The "required when dual" framing read backwards from that case rather than from the hook's detection contract.

**Effect on decision**: MONO-101 was originally listed in ADR-MONO-012 § 6 as "may or may not need backfill" — implementation experience (and re-reading the hook source) showed it **does** need backfill. MONO-101 closure (4 fan-platform services backfilled) ratifies the corrected interpretation. Future single-type services must include a short Composition H3.

### Authoritative Composition H3 form (single-type)

```markdown
### Service Type Composition

`<svc>` is a single-type `<service-type>` service per `platform/service-types/INDEX.md`. <1-sentence note on the primary surface — HTTP, Kafka outbox, batch schedule, etc.>
```

### Authoritative Composition H3 form (dual-type, WMS inventory pattern)

```markdown
### Service Type Composition

`<svc>` is a dual-type service: **primary** `<service-type-1>` (<note>), **secondary** `<service-type-2>` (<note>). Inbound surface — `<topics or endpoints>`. Outbound surface — `<topics or endpoints>`.
```

## 3. Status of ADR-MONO-012 body

**Untouched.** The body preserves the decision-rationale at acceptance time. Readers consult ADR-MONO-012a for any factual claim about service counts, hook scope, or Composition H3 presence rules. INDEX outcome lines from MONO-094 → MONO-101 retain the per-cycle audit-trail (no rewrite).

## 4. Consequences

- **Positive**: Future contributors reading ADR-MONO-012 + ADR-MONO-012a together get both the decision-time rationale and the post-implementation truth. New `architecture.md` authoring follows the "always present" Composition H3 form — already enforced by hook + MONO-098/101 precedent.
- **Negative**: One more ADR to consult. Mitigation: ADR-MONO-012 header should link to ADR-MONO-012a as "Related" — but this would mutate the ADR-MONO-012 body. Per option C-1, the link is one-way (ADR-MONO-012a links back; INDEX.md surfaces both).
- **Pattern**: This is the first option-C-1 → forward-ADR promotion in the monorepo. Future repeats (e.g. ADR-MONO-010/011 also have option-C-1 accumulation per memory `project_e2e_3phase_strategy_complete.md`) may follow the same pattern when their corrections reach 3+ distinct items.

## 5. Verification

- `grep -c "^## Identity$" projects/ecommerce-microservices-platform/specs/services/*/architecture.md` = 14 ✓
- Hook source `.claude/hooks/hardstop-detect.ps1` L260-330 — detection logic project-agnostic (no `wms-platform`-scoped guard) ✓
- `grep -c "^### Service Type Composition$" projects/{ecommerce-microservices-platform,fan-platform,global-account-platform,scm-platform,wms-platform}/specs/services/*/architecture.md` = 32 (= all single-type + dual-type, "always present" 충족 post-MONO-101) ✓

## 6. Related memory

- `project_e2e_3phase_strategy_complete.md` § "option C-1 audit-only" pattern definition (the per-cycle audit-trail mechanism that this ADR consolidates).
- `project_adr_mono_012_d3_cycle_complete.md` § 메타 학습 (Correction 3 의 evidence — D1 "always present" practice).
- `project_mono_085_dead_reference_batch.md` (the broader audit-driver-rerun closure cycle in which the D3 migration ran).

## 7. Subsequent cycles

If a fourth correction surfaces against ADR-MONO-012, append it to § 2 of this ADR (the correction layer expands; no ADR-MONO-012b). The forward-ADR is itself mutable for adding corrections, but each addition must cite the implementation cycle that surfaced it.
