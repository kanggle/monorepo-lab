# Task ID

TASK-PC-FE-034

# Title

Consolidate "개요" + "통합 개요" — promote the BFF cross-domain Operator Overview (`/dashboards/overview`) to the console landing/home, demote the GAP-only composed overview (`/dashboards`) to a GAP-card drill-down, collapse the duplicate nav entry, and add the missing ERP operations nav link (ADR-MONO-015 D1-B re-position + ADR-MONO-017 § D8 landing promotion; UI routing hierarchy only — read-only fan-out + § 2.4.9.1 contract body byte-unchanged)

# Status

review

# Owner

frontend-engineer (FE routing/nav re-org + spec re-position; no BE/producer change)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- test
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

# Dependency Markers

- **amends (ADR)**: [ADR-MONO-015](../../../../docs/adr/ADR-MONO-015-platform-console-dashboards-model.md) § D1-B — the GAP-only composed operator overview (`features/dashboards`, TASK-PC-FE-005) is **re-positioned** from a peer top-level overview to the **GAP-card drill-down detail** of the 5-domain overview. No decision reversal (B "composed overview" stands); the parity-line satisfaction is preserved (the GAP overview still exists, now reached via the GAP card).
- **amends (ADR)**: [ADR-MONO-017](../../../../docs/adr/ADR-MONO-017-platform-console-bff-architecture.md) § D8 — the MVP "Operator Overview" cross-domain dashboard (`features/operator-overview`, `/dashboards/overview`, TASK-PC-FE-011) is **promoted to the console landing/home** target. Composition contract (§ 2.4.9.1) is byte-unchanged; only the FE landing/nav binding changes.
- **precedent / re-positions**: [TASK-PC-FE-005](../done/TASK-PC-FE-005-console-operator-overview-dashboards-slice.md) (GAP-only composed overview — kept, demoted to drill-down) + [TASK-PC-FE-011](../done/TASK-PC-FE-011-mvp-operator-overview.md) (5-domain BFF overview — kept, promoted to home). This task **does not delete** either screen; it re-organises their nav/landing relationship to remove the "개요 vs 통합 개요" duplication.
- **touches (ERP nav gap)**: the ERP operations screen (`(console)/erp`, TASK-PC-FE-010) exists but has **no top-level nav entry** in `(console)/layout.tsx` (nav ends at Finance). Added here as a same-PR companion (the overview re-org already rewrites the nav list).
- **no dependency on**: any producer / `console-bff` change. Every fan-out leg (GAP-only 3-leg in `features/dashboards`, 5-domain in `features/operator-overview`) is reused byte-unchanged. Read-only; zero retrofit.

---

# Goal

Remove the operator-facing confusion between two similarly-named overview screens by establishing a clear hierarchy:

- **Home / landing = the 5-domain cross-domain overview** (`/dashboards/overview`, BFF federation). One nav entry: "개요".
- **GAP-only composed overview** (`/dashboards`, FE-005, accounts + audit + operators 3-leg) becomes the **drill-down detail reached by clicking the GAP card** on the home overview — not a peer top-level nav item.
- **The duplicate top nav entries collapse** from two ("개요" → `/dashboards` + "통합 개요" → `/dashboards/overview`) to one ("개요" → `/dashboards/overview`).
- **The missing ERP operations nav link is added** (parity with WMS/SCM/Finance operations links).

The result: a single, unambiguous landing that shows all 5 business domains at a glance, with the GAP platform-operations detail (accounts/audit/operators) one click deeper via the GAP card. No screen is deleted; no producer or composition contract changes; the change is purely the FE landing + nav + GAP-card link wiring, plus the spec re-position that authorises it.

# Scope

## In Scope

**Spec PR (this task md + ADR amendments + contract/architecture re-position + INDEX):**

1. **ADR-MONO-015 § D1-B amendment** — record the GAP-only overview re-position to a GAP-card drill-down (additive amendment block; B decision unchanged; parity-line satisfaction preserved). HARDSTOP-04 discipline: additive, no prior-decision reversal.
2. **ADR-MONO-017 § D8 amendment** — record the cross-domain Operator Overview promotion to the console landing/home (additive amendment block; D1-D8 decision axes byte-unchanged; only the FE landing binding is newly stated).
3. **`console-integration-contract.md` § 2.4.9.1** — add a UI-routing note: the GAP card's drill-down target is the GAP-only composed overview route (`/dashboards`); the route is the console landing. The composition route's request/response/error/auth/resilience/observability body stays **byte-unchanged** (read-only invariant intact).
4. **`specs/services/console-web/architecture.md`** — update the nav inventory + landing/home statement (one "개요" entry → `/dashboards/overview`; GAP detail reached via card; ERP nav present).
5. **Task md + `INDEX.md`** ready entry.

**Impl PR (FE — `console-web` routing/nav re-org):**

6. **Root landing** — `src/app/page.tsx` `redirect('/dashboards')` → `redirect('/dashboards/overview')`.
7. **Nav** — `src/app/(console)/layout.tsx`:
   - remove the separate "통합 개요" (`nav-operator-overview`) entry;
   - re-point "개요" (`nav-dashboards`) to `/dashboards/overview` (the cross-domain home);
   - keep "도메인 상태" (`nav-domain-health`, `/dashboards/health`) unchanged (distinct dashboard);
   - add an "ERP 운영" (`nav-erp`, `/erp`) entry after Finance.
8. **GAP-card drill-down** — `features/operator-overview/components/DomainCard.tsx`: wrap the GAP card (only `card.domain === 'gap'`) so it links to `/dashboards` (the GAP-only composed overview detail). Keyboard/focus accessible; degrade/forbidden branches still render (link present regardless of status, or link only on `ok` — decided in AC-6). The other 4 domain cards are unchanged in this task.
9. **GAP detail re-framing** — `(console)/dashboards/page.tsx` + `features/dashboards` `OperatorOverviewScreen`: adjust the heading/intro copy so the screen reads as "GAP 상세 (계정·감사·운영자)" reached from the home overview, with a back link to `/dashboards/overview`. No data/fan-out change.

**Test (e2e + unit):**

10. Update the Playwright e2e nav-rendering expectations (the `nav-*` testid set changes: `nav-operator-overview` removed, `nav-dashboards` now lands on the cross-domain overview, `nav-erp` added). Add a drill-down smoke: home overview → click GAP card → `/dashboards` GAP detail renders.

## Out of Scope

- **Deleting `features/dashboards` or `features/operator-overview`.** Both screens are kept; only their nav/landing relationship changes.
- **Any `console-bff` / producer / composition-contract body change.** § 2.4.9.1 request/response/auth/resilience stays byte-unchanged.
- **Mutation surface** — no `Idempotency-Key` / `X-Operator-Reason` / write path anywhere (ADR-MONO-017 § 2.4.9 hard invariant).
- **Drill-down on the non-GAP cards** (wms/scm/finance/erp card → domain ops screen). A reasonable future enhancement, but a separate task — this task only wires the GAP card to its existing detail screen and the ERP **nav** link.
- **Merging "도메인 상태" (`/dashboards/health`)** into the home overview. Distinct dashboard; untouched.
- **Catalog `gap.baseRoute`** — stays `/accounts` (FE-002 unchanged).

# Acceptance Criteria

- [ ] **AC-1** Visiting `/` (authenticated) lands on the 5-domain cross-domain overview (`/dashboards/overview`); unauthenticated → `/login` (the `(console)` guard, unchanged).
- [ ] **AC-2** The top nav has exactly **one** overview entry labelled "개요" pointing to `/dashboards/overview`; the previous separate "통합 개요" entry is gone; no nav item points to `/dashboards` (GAP detail is card-reached only).
- [ ] **AC-3** "도메인 상태" (`/dashboards/health`) nav entry is unchanged and still reachable.
- [ ] **AC-4** A new "ERP 운영" nav entry (`nav-erp`) points to `/erp` and renders the existing ERP ops screen.
- [ ] **AC-5** On the home overview, the GAP card is an accessible link (keyboard-focusable, `role`/`aria` correct) to `/dashboards`; activating it renders the GAP-only composed overview (accounts/audit/operators 3-leg).
- [ ] **AC-6** Decide + implement: the GAP-card drill-down link is present when `card.status === 'ok'`; on `degraded`/`forbidden` the card keeps its existing placeholder + retry behaviour and the drill-down affordance is suppressed (no link to a screen the operator can't populate) — OR the agreed alternative recorded in the task. (Default: link only on `ok`.)
- [ ] **AC-7** The `/dashboards` GAP detail screen reads as a GAP drill-down (heading/intro re-framed) and offers a back link to `/dashboards/overview`. Its 3-leg fan-out + per-card isolation behaviour is byte-unchanged.
- [ ] **AC-8** ADR-MONO-015 § D1-B + ADR-MONO-017 § D8 carry additive amendment blocks recording the re-position/promotion (no prior-decision reversal; HARDSTOP-04 clean). `console-integration-contract.md` § 2.4.9.1 composition body is byte-unchanged; only a UI-routing note is added.
- [ ] **AC-9** Playwright e2e nav-rendering + the new GAP drill-down smoke pass; no `nav-*` testid assertion is left stale. `console-web` typecheck + lint + unit green.
- [ ] **AC-10** No producer / `console-bff` source touched. Read-only; zero retrofit (confirmed by diff scope = `console-web` + platform-console specs/ADRs only).

# Related Specs

- [ADR-MONO-015](../../../../docs/adr/ADR-MONO-015-platform-console-dashboards-model.md) § D1-B (GAP composed overview — re-positioned to drill-down).
- [ADR-MONO-017](../../../../docs/adr/ADR-MONO-017-platform-console-bff-architecture.md) § D8 (cross-domain Operator Overview — promoted to landing).
- [`specs/services/console-web/architecture.md`](../../specs/services/console-web/architecture.md) (nav inventory + landing/home; Server vs Client Components).
- [ADR-MONO-013](../../../../docs/adr/ADR-MONO-013-platform-console-foundation.md) § 3 / D7.4 (parity checklist — `dashboards` line satisfaction preserved via the kept GAP overview).

# Related Contracts

- [`console-integration-contract.md`](../../specs/contracts/console-integration-contract.md) § 2.4.9.1 (cross-domain Operator Overview composition route — body byte-unchanged; UI-routing note added) + § 2.4.4 (GAP composed overview — the drill-down target) + § 2.4.9.2 (Domain Health — untouched).

# Edge Cases

- **No active tenant**: both the home overview (`/dashboards/overview`, 400 NO_ACTIVE_TENANT → "select a tenant" gate) and the GAP detail (`/dashboards`, same gate) keep their existing tenant-gate behaviour. The drill-down link does not bypass the gate.
- **GAP card degraded/forbidden on home**: per AC-6 default, the drill-down affordance is suppressed (the GAP detail would itself degrade) — the card keeps its retry/forbidden placeholder.
- **BFF unavailable (home overview 502)**: the home renders the existing "통합 개요를 일시적으로 불러올 수 없습니다" banner; the operator can still reach domain screens via the other nav entries (WMS/SCM/Finance/ERP/감사·보안/운영자/카탈로그). Landing degrades gracefully — the console shell stays intact.
- **Deep-link to old `/dashboards`**: still valid (the GAP detail route is kept) — a bookmarked `/dashboards` opens the GAP detail directly. Only the *default* landing and *nav* change.
- **ERP nav for an operator without ERP entitlement**: the `/erp` screen's existing entitlement/degrade behaviour governs; the nav link itself is static (same posture as the other domain ops links).

# Failure Scenarios

- **Stale e2e nav assertions** — removing `nav-operator-overview` / re-pointing `nav-dashboards` breaks any e2e that asserts the old testid set. Mitigation: AC-9 forces the e2e update in the same PR; a CI-RED-at-merge is a regression gate (CLAUDE.md merge-verify 3-dim).
- **Landing redirect loop** — mis-wiring `page.tsx` → `/dashboards/overview` while the overview redirects back would loop. Mitigation: the overview page only redirects on 401 (`/login`); no `/` ↔ overview cycle. Verify with the e2e landing smoke.
- **Drill-down to a degraded GAP detail** — linking the GAP card to `/dashboards` when GAP is down lands the operator on a fully-degraded screen. Mitigation: AC-6 suppresses the link unless GAP card is `ok`.
- **Spec-skip Hard Stop** — implementing the FE re-org before the ADR/contract re-position is merged would conflict with the standing "둘 다 유지" spec (FE-011 §51) → HARDSTOP-06. Mitigation: the spec PR (ADR amendments + contract note) lands first; the impl PR cites it.
- **Accessibility regression** — making the GAP card a link must preserve heading semantics + keyboard focus order. Mitigation: AC-5 explicit; e2e/axe check the card link role.

---

분석=Opus 4.8 / 구현 권장=Opus (routing hierarchy re-design + ADR amendment precision + e2e nav regression gate). 단순 nav 라벨/링크만 떼어내면 Sonnet 가능하나, ADR 2건 additive amendment + landing 승격 + drill-down 접근성이 얽혀 Opus 권장.
