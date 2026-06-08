# Task ID

TASK-MONO-199

# Title

docs reality-alignment (13th): record the ecommerce↔wms order-fulfillment integration (ADR-MONO-022, the §D7 + §D4 v2 arc TASK-MONO-193/195/196/197/198) in `docs/project-overview.md` — the portfolio hub did not mention the monorepo's first business-domain↔business-domain runtime coupling.

# Status

done

# Owner

(unassigned) — monorepo-level docs reality-alignment. 분석=Opus 4.8 / 구현=Sonnet-class (docs-only, surgical).

# Task Tags

- docs

---

# Dependency Markers

- **선행**: TASK-MONO-193/195/196/197/198 (the ADR-022 arc — all merged). This records their net portfolio-level effect.
- **맥락**: the recurring `docs/project-overview.md` reality-alignment pattern (12 prior, surgical docs-only — MONO-141/148/168/172/177/178 …). Trigger here = the just-landed §D4 arc was absent from the hub.

# Goal

Keep `docs/project-overview.md` an accurate single-entry snapshot. After the ADR-022 arc closed §D4 entirely (forward fulfillment + backorder auto-cancel/refund + inventory reconciliation), the overview still described ecommerce and wms as independent v1 projects with no mention of the cross-project order→fulfillment loop — a real reality drift (the "Fulfillment" row was a service grouping only).

# Scope

## In Scope (docs-only, surgical)
1. **Header note** (`갱신 시점`): bump 2026-06-07 → 2026-06-08; prepend the ADR-022 arc as the latest meaningful change (forward §D7 + backorder v2(a) + reconciliation v2(b), with the MONO-193/195/196/197/198 + PR refs).
2. **§2.3 ecommerce**: add a "wms 주문-이행 통합" bullet — the cross-project loop (forward / backorder / reconciliation), ACL-on-ecommerce (D6), orderNo correlation (D5), e2e location.
3. **§2.1 wms**: add an "ecommerce 주문-이행 통합의 이행자" bullet — ecommerce as a second external order source (ERP-webhook-equivalent), wms domain kept pure.

## Out of Scope
- Any code / spec / contract change (the arc is already merged + documented in ADR-022 + the contract files).
- Re-describing v2(b) internal named follow-ups (those live in the task/ADR, not the hub snapshot).

# Acceptance Criteria

- AC-1: `docs/project-overview.md` mentions ADR-MONO-022 + the ecommerce↔wms fulfillment loop (forward + backorder + reconciliation) in both the header note and §2.3 (+ §2.1 cross-reference).
- AC-2: Date bumped to 2026-06-08; links to `adr/ADR-MONO-022-*.md` resolve.
- AC-3: No code/spec/contract change; docs lint passes (markdown links valid).

# Related Specs

- `docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md` (the authoritative decision record — unchanged).

# Related Contracts

- None (docs-only).

# Edge Cases / Failure Scenarios

- None (docs snapshot; no runtime behavior).

# Impact on `projects/<name>/`

- None — `docs/project-overview.md` only (repo-root shared docs, human-reference snapshot).

# Notes

Single PR (task + docs together) per `feedback_pr_bundling` (case-by-case): a docs-only reality-alignment executed immediately has no "ready-queue availability" window that the spec/impl split protects, so the two-PR ceremony is unwarranted. 13th in the surgical-alignment series.

---

# Implementation Notes (DONE 2026-06-08)

Edited `docs/project-overview.md` only: header note (date + ADR-022 arc prepended) + §2.3 ecommerce fulfillment-integration bullet + §2.1 wms second-order-source bullet. No code/spec/contract touched. Verified the ADR-022 link target exists (`docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md`).
