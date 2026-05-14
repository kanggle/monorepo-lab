# ADR-MONO-012 — Cross-Project `architecture.md` Canonical Form

**Status:** PROPOSED
**Date:** 2026-05-15
**History:** PROPOSED 2026-05-15 (TASK-MONO-092 — refactor-spec Tier 3 reconsider; cross-project format divergence surface).
**Decision driver:** `/refactor-spec all --dry-run` (8-task cycle 2026-05-14 → 2026-05-15) Tier 3 backlog 의 cross-project structural divergence — 5 projects use 5 distinct `architecture.md` formats. ADR-level decision needed because canonical form selection introduces a new structural convention (out of refactor-spec mechanical scope per § Constraints "no new rules or decisions").
**Supersedes:** none.
**Related:** [ADR-MONO-009](ADR-MONO-009-chrome-devtools-mcp-visual-regression.md) (PROPOSED ADR template pattern — pre-author with indefinite-PROPOSED tolerated outcome), [ADR-MONO-003a](ADR-MONO-003a-d4-override-scope-canonicalization.md) § D1.1 (project-internal spec polish IN-scope of D4 OVERRIDE), CLAUDE.md § Hard Stop Rules HARDSTOP-10 (Service Type declaration enforce — current hook validates WMS form), `.claude/commands/refactor-spec.md` § Operational Patterns Tier 3 audit-only path (out-of-mechanical-scope reconsider channel).

---

## 1. Context

### 1.1 Current state — 5 projects × 5 architecture.md formats

The `/refactor-spec all --dry-run` non-deadref audit surveyed `projects/<name>/specs/services/*/architecture.md` across all 5 active projects (ecommerce-microservices-platform, fan-platform, global-account-platform, scm-platform, wms-platform) and found **5 distinct formats**:

| Project | H1 form | Service Type form | Identity table | Sample file |
|---|---|---|---|---|
| WMS | `# <svc> — Architecture` + intro paragraph | `### Service Type Composition` (H3 sub-section under Identity table row) | ✓ present | `projects/wms-platform/specs/services/inventory-service/architecture.md` |
| fan-platform | `# <svc> — Architecture` + intro paragraph | `## Service Type` (H2, separate section) | ✓ present | `projects/fan-platform/specs/services/community-service/architecture.md` |
| SCM | `# <svc> — Architecture` + intro paragraph | `## Service Type` (H2, separate section) | ✗ absent (hybrid) | `projects/scm-platform/specs/services/procurement-service/architecture.md` |
| ecommerce | `# Service Architecture` (flat, no service name) | `## Service Type` (H2, separate section under `## Service`) | ✗ absent | `projects/ecommerce-microservices-platform/specs/services/order-service/architecture.md` |
| GAP | `# Service Architecture — <svc>` (flat, service name in H1) | `## Service Type` (H2, separate section under `## Service`) | ✗ absent | `projects/global-account-platform/specs/services/auth-service/architecture.md` |

### 1.2 Format axis decomposition

Three structural axes diverge independently:

**Axis 1 — H1 form**: `# <svc> — Architecture` (WMS/fan/SCM, 3 projects) vs `# Service Architecture` flat (ecommerce, 1 project) vs `# Service Architecture — <svc>` (GAP, 1 project). Direct comparison: which form is the title?

**Axis 2 — Identity table**: a single `## Identity` H2 with `| Field | Value |` table consolidating service name / type / architecture style / language / data store / event policy (WMS/fan, 2 projects), vs flat H2 sections for each field (ecommerce/GAP, 2 projects), vs partial table-absent hybrid (SCM, 1 project).

**Axis 3 — Service Type declaration**: `### Service Type Composition` H3 sub-section (WMS, 1 project — supports the "rest-api + event-consumer" composition case) vs `## Service Type` H2 (fan/SCM/ecommerce/GAP, 4 projects — declares one primary type).

### 1.3 Author origin trace

- **ecommerce form** (oldest, 2026-04-21 import era): flat H2 list with `# Service Architecture` H1, no service name in title. Authored as standalone repo template before monorepo conventions emerged.
- **GAP form** (2026-04-30 import era): variation of ecommerce — added service name to H1 (`# Service Architecture — <svc>`). Still flat H2 list.
- **WMS form** (2026-04~05 native authoring): Identity-table introduced as a single-glance header — consolidates 8 metadata fields. `### Service Type Composition` H3 added to support inventory-service's dual `rest-api + event-consumer` case (TASK-BE-046). HARDSTOP-10 hook validates this form on edit.
- **fan-platform form** (2026-05-03 bootstrap): emulated WMS form for Identity table but kept `## Service Type` H2 (no Composition sub-section needed — all fan services are single-type).
- **SCM form** (2026-05-04 bootstrap): hybrid — adopted WMS H1 + intro but kept ecommerce/GAP `## Service Type` H2 and skipped Identity table. Partial migration toward WMS form without committing.

The drift is **not random** — it's a layered history of bootstrap times. Newer projects gradually adopted WMS-form elements but no project ever ratified a cross-portfolio canonical.

### 1.4 HARDSTOP-10 hook current behavior

The hook (`.claude/hooks/hardstop-detect.ps1` or similar — verify on implementation) currently fires on `### Service Type Composition` absence in **WMS service architecture.md only** (BE-150 / BE-154 / BE-161 three known instances). It does NOT fire on ecommerce/GAP/SCM/fan files. Whether this is intentional (project-scoped enforcement) or vestigial (hook authored when WMS was the only project) is unclear from inspection — the hook source is the source-of-truth.

If canonical form selection extends to require Identity-table + Service Type Composition cross-project, the hook must propagate to fire on all 5 projects' architecture.md files — substantial governance carry-over.

### 1.5 Why this is `/refactor-spec` out-of-scope

The slash-command rules state:

> - No new rules or decisions — only improve how existing rules are expressed
> - One file at a time — do not mix changes across files in a single edit

A canonical-form decision is **a new structural convention** across 24 architecture.md files (ecommerce 13 + GAP 8 + SCM 3 — fan already mostly WMS-form). This exceeds "polish how existing rules are expressed" and creates a new monorepo-wide spec authoring convention. Pre-empting future debate via ADR is the appropriate governance path.

### 1.6 Why pre-author before need triggers

Same rationale as ADR-MONO-009 (pre-authored Chrome DevTools MCP gap before incident triggers it):

- A future "next architecture.md author" decision-time moment is when the convention question recurs — and decision-time authoring is when criteria slip and reviewer trust is lowest.
- Pre-author documents the trade-off space (Path A / B / C) so the ACCEPTED transition is a single line ("user chooses Path X, 2026-XX-XX") rather than a fresh debate.
- PROPOSED status is **permitted-to-indefinite** if migration cost outweighs harmonization value (legitimate outcome — divergence is per-project consistent and readers don't navigate cross-project architecture.md frequently).

---

## 2. Decision

**D1 (PROPOSED canonical):** Adopt the **WMS Identity-table form** as cross-project canonical, with the H3 Service Type Composition sub-section recommended (required when service combines multiple types per `platform/service-types/INDEX.md` selection rules).

Concretely the canonical form is:

```markdown
# <service-name> — Architecture

<intro paragraph: "This document declares the internal architecture of …">

---

## Identity

| Field | Value |
|---|---|
| Service name | `<svc>` |
| Service Type | `<primary>` [+ `<secondary>` if dual] (see Service Type Composition below if dual) |
| Architecture Style | **<Hexagonal / Layered / DDD-style>** |
| Primary language / stack | … |
| Bounded Context | … |
| Deployable unit | … |
| Data store | … |
| Event publication | … |
| Event consumption | … |

### Service Type Composition  (required when Service Type is dual)

<H3 sub-section describing each type's responsibility split, when applicable>

---

## Architecture Style

<H2 — body justifies the chosen style, references `platform/architecture-decision-rule.md` and the relevant `platform/service-types/<type>.md`>

## Responsibility

<remaining H2 sections per service-specific content>
```

**D2 (migration trigger):** ACCEPTED transition is **user-explicit** (not auto-triggered). Migration scope ≈ 24 architecture.md edits across 3 projects (ecommerce 13 + GAP 8 + SCM 3) — substantial enough that user must commit cycle budget.

**D3 (migration order, post-ACCEPTED):** SCM first (3 file, partial-aligned), then GAP (8 file, flat), then ecommerce (13 file, oldest + most divergent). Rationale: ascending migration cost — earliest wins build confidence + verify hook propagation before largest scope.

**D4 (HARDSTOP-10 hook propagation):** When migration is ACCEPTED, hook must extend to fire on all 5 projects (currently WMS-only). Same migration task carries hook update.

**D5 (status indefinite-PROPOSED outcome):** If user decides migration cost is not worth harmonization value, PROPOSED stays indefinitely as governance documentation. Future audits referencing this ADR satisfy "decision was considered and deferred".

---

## 3. Alternatives Considered

### 3.1 Path A — WMS form canonical (D1 selected)

**Pros:**
- Identity table consolidates 8+ metadata fields in single glance — best reader UX
- Service Type Composition H3 already supports dual-type case (WMS inventory-service precedent)
- HARDSTOP-10 hook already enforces (extends naturally to other projects)
- fan-platform already mostly aligned (reduces total migration to 24 file)

**Cons:**
- Migration cost: ~24 architecture.md file edits (ecommerce 13 + GAP 8 + SCM 3)
- Each file requires Identity table backfill + Service Type Composition sub-section (when dual)
- HARDSTOP-10 hook must propagate cross-project (additional governance work)
- ecommerce's "Why This Architecture" + "Internal Structure Rule" + similar narrative H2 sections need new placement (Architecture Style body, etc.)

### 3.2 Path B — ecommerce flat form canonical

**Pros:**
- Simplest format — flat H2 list, no markdown table required
- Migration cost asymmetric: WMS 7 + fan 4 = 11 file simplification (drop Identity table)
- HARDSTOP-10 hook would relax (or remove entirely)

**Cons:**
- Loses Identity table reader-UX value
- Service Type Composition support has no natural location (dual-type case loses convention)
- HARDSTOP-10 hook removal means service-type declaration drift becomes possible (regression of governance progress)
- Newer convention (WMS) is empirically more rigorous — adopting older convention is governance regression

### 3.3 Path C — per-project intentional divergence (current state)

**Pros:**
- Zero migration cost
- Per-project consistency already satisfied (within each project, format is uniform)
- Readers usually navigate within a single project at a time (cross-project architecture.md navigation rare)

**Cons:**
- New project bootstrap chooses fresh (next bootstrap 시점에 다시 결정 압력)
- HARDSTOP-10 hook propagation question stays unresolved
- Cross-project sibling spec readers (e.g. agent dispatching across projects) hit format-switching cognitive load
- Tier 3 finding persists — future audits re-surface it

---

## 4. Consequences

### 4.1 If ACCEPTED (Path A — WMS canonical)

- ~24 architecture.md files migrate to Identity-table + Service Type Composition (when applicable) form
- HARDSTOP-10 hook propagates cross-project — new violation surface in ecommerce/GAP/SCM future edits
- D4 OVERRIDE applied per ADR-MONO-003a § D1.1 (project-internal spec polish — cross-project but markdown only, no production code touch)
- Migration cycle ≈ 3-5 tasks (per-project batches), comparable to BE-153~164 portfolio gap closure cycle
- Reader UX gain: consistent first-glance Identity table across all services portfolio-wide

### 4.2 If indefinite PROPOSED (D5 outcome)

- Tier 3 finding documented as "considered + deferred" rather than "missed"
- Next architecture.md author choice deferred to author judgment, with ADR-MONO-012 as decision reference
- HARDSTOP-10 hook stays WMS-only (current behavior)
- No migration cost, no new risk

### 4.3 If REJECTED (Path B or C explicit ratification)

- Tier 3 finding closed with negative outcome
- HARDSTOP-10 hook potentially relaxed or removed (Path B) or unchanged (Path C)
- New project bootstrap inherits chosen canonical
- WMS (and fan-platform) revert costs if Path B (loses Identity table — 11 file simplification)

---

## 5. Verification

Pre-ACCEPTED (PROPOSED status):

- This ADR exists at `docs/adr/ADR-MONO-012-cross-project-architecture-md-canonical-form.md` with `Status: PROPOSED` header.
- `docs/adr/INDEX.md` has the ADR-MONO-012 row.
- All 5 project sample paths in § 1.1 resolve (cross-ref validation).

Post-ACCEPTED (migration cycle deliverables — not in this task):

- All 24 target architecture.md migrated to D1 canonical form.
- HARDSTOP-10 hook propagation merged to all 5 projects.
- `grep -c "^## Identity" projects/*/specs/services/*/architecture.md` = total service count (validates Identity table presence).
- `grep -c "^### Service Type Composition" projects/*/specs/services/*/architecture.md` ≥ services with dual Service Type declaration.

---

## 6. Outstanding follow-ups

- **F-T3-A migration** (if D1 ACCEPTED): per-project architecture.md migration tasks. SCM 1-task (3 file) → GAP 1-task (8 file) → ecommerce 1-task (13 file) per D3.
- **F-T3-B HARDSTOP-10 propagation** (if D1 ACCEPTED): hook source update + cross-project enforce.
- **fan-platform partial-align catch-up** (if D1 ACCEPTED): fan currently has Identity table but not `### Service Type Composition` (all single-type). May or may not need backfill depending on D1 interpretation ("required when dual" → fan skip).
- **PROPOSED indefinite review trigger**: if a 6th project bootstraps (per ADR-MONO-008 finance / ADR-MONO-002 mes catalysts), this ADR's ACCEPTED transition may become forced — re-evaluate at that point.

---

## 7. References

- [ADR-MONO-003a](ADR-MONO-003a-d4-override-scope-canonicalization.md) § D1.1 (project-internal spec polish IN-scope of D4 OVERRIDE — this migration would qualify)
- [ADR-MONO-009](ADR-MONO-009-chrome-devtools-mcp-visual-regression.md) (PROPOSED-pattern precedent — pre-author with indefinite-PROPOSED tolerated)
- [CLAUDE.md](../../CLAUDE.md) § Hard Stop Rules HARDSTOP-10 (Service Type declaration enforce)
- [`.claude/commands/refactor-spec.md`](../../.claude/commands/refactor-spec.md) § Operational Patterns (Tier 3 audit-only path)
- [`platform/service-types/INDEX.md`](../../platform/service-types/INDEX.md) (Service Type catalog and selection rules referenced from Identity table row)
- Sample WMS form: [`projects/wms-platform/specs/services/inventory-service/architecture.md`](../../projects/wms-platform/specs/services/inventory-service/architecture.md)
- Sample fan form: [`projects/fan-platform/specs/services/community-service/architecture.md`](../../projects/fan-platform/specs/services/community-service/architecture.md)
- Sample SCM hybrid form: [`projects/scm-platform/specs/services/procurement-service/architecture.md`](../../projects/scm-platform/specs/services/procurement-service/architecture.md)
- Sample ecommerce flat form: [`projects/ecommerce-microservices-platform/specs/services/order-service/architecture.md`](../../projects/ecommerce-microservices-platform/specs/services/order-service/architecture.md)
- Sample GAP flat form: [`projects/global-account-platform/specs/services/auth-service/architecture.md`](../../projects/global-account-platform/specs/services/auth-service/architecture.md)
