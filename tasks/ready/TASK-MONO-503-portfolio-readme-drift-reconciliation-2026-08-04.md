# Task ID

TASK-MONO-503

# Title

Reconcile hub README.md ↔ 5 published standalone-repo READMEs — fix wrong standalone-link column, 3 severely
stale project READMEs, and 6 lower-severity drift items (2026-08-04 survey)

# Status

ready

# Owner

monorepo

# Task Tags

- docs

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

Following the 2026-08-04 portfolio standalone re-sync (5 axes: wms/iam/ecommerce/fan/scm, all `exit=0`), a
read-only survey (Explore agent, this session) compared root `README.md`'s project listing against each of the
5 published projects' own `README.md` (which doubles as that project's standalone-repo README, per
`scripts/sync-portfolio.sh`'s hoist-to-root strategy) and found 10 concrete drift findings, 3 of them severe
enough that a published standalone repo's README actively **understates its own shipped scope** — describing
fully-implemented services as "spec-only" or "not yet built."

This is not cosmetic: `project_portfolio_submission_strategy.md`'s whole rationale for the dual-deployment
strategy is that standalone repos are what time-constrained evaluators (recruiters, coding-test graders) read
first. A standalone README that undersells a project's actual completeness directly undermines the strategy.

After this task: root `README.md`'s project table accurately reflects each project's standalone-repo
publication status and service count, and each of the 5 published projects' `README.md` accurately reflects its
own current shipped scope (verified against `settings.gradle`, `PROJECT.md`, and actual `apps/` contents — not
against the READMEs' own prior claims).

---

# Scope

## In Scope

Fix all 10 findings from the survey, grouped by severity:

**Critical (wrong facts about publication status / shipped scope):**
1. Root `README.md` "Standalone repo" column: iam-platform currently shown `_(monorepo-only)_` — wrong, it IS
   published (`kanggle/iam-platform`). finance-platform / erp-platform currently shown as live-looking repo
   links — wrong, they are registered in `sync-portfolio.sh` but **not yet published**; mark
   `_(not yet published)_` instead.
2. `projects/wms-platform/README.md`: rewrite Status / service-map / "v2 스코프" framing — `inbound-service`,
   `outbound-service`, `admin-service` are fully implemented (in `settings.gradle`, real code), not "v2 스펙만."
   `notification-service` is entirely unmentioned — add it. Gateway description should mention the OIDC + tenant
   gate (root README already states this correctly — use it as the cross-check).
3. `projects/fan-platform/README.md`: rewrite Status (currently "🚧 부트스트랩 진행 중") / service-status table
   (currently shows community-service/artist-service as unpublished, fan-platform-web as "backend 안정화 후") /
   architecture diagram (membership shown as greyed-out `(v2)` placeholder, notification-service omitted
   entirely) — all 5 backend services + web are fully implemented per `settings.gradle` and root README's
   already-correct "v1.1 — 5 backend + web."
4. `projects/scm-platform/README.md`: rewrite entirely — currently claims "skeleton only, 서비스 코드 0,"
   "portfolio sync 미등록," "CI 미포함." All false: `procurement-service`, `inventory-visibility-service`,
   `demand-planning-service`, `logistics-service`, `gateway-service` are all implemented and in
   `settings.gradle`; scm-platform IS registered + published in `sync-portfolio.sh`; `.github/workflows/ci.yml`
   has a dedicated scm job. Delete the "Known Limitations" section (all 4 bullets are stale-false).

**Medium:**
5. `projects/ecommerce-microservices-platform/README.md`: Services table / architecture diagram / Swagger docs
   table / k6 scenario still list **Auth** as an active service — it was retired (`TASK-BE-132`, per
   `PROJECT.md`); root README already correctly states this. Replace with **settlement-service** (127 files,
   `TASK-BE-365`, in `settings.gradle`), which is completely missing from the same tables/diagrams despite root
   README already knowing about it ("seller settlement").
6. Root `README.md` scm-platform row: says "4 services" (procurement/inventory-visibility/demand-planning/
   gateway) — omits `logistics-service` (`TASK-SCM-BE-042`, 2026-07-24, postdates this row's last edit). Update
   to 5 services (or bump version label).

**Low (structural / consistency, not factual-scope):**
7. `projects/iam-platform/README.md` and `projects/ecommerce-microservices-platform/README.md`: no hub-backlink
   to monorepo-lab anywhere, no AI-collaboration/"Claude Code" section — add both, matching the
   wms-platform/fan-platform pattern (a "🔗 관련 링크" section + an explicit "how this was built" note).
   ecommerce's README additionally has no CI badge at all — add one.
8. `projects/fan-platform/README.md` CI badge (line ~6) points at `kanggle/monorepo-lab/actions` — wrong for a
   standalone-repo README; should point at `kanggle/fan-platform/actions`, matching the wms-platform/iam-platform
   pattern.
9. `projects/fan-platform/README.md`'s "Differentiation from IAM's frozen community-service" section describes
   `iam-platform/apps/community-service/` in present tense as a live comparison point — that service was retired
   2026-07-14 (`TASK-MONO-394`), source removed. Update to past tense / note the retirement, or drop the dead
   cross-link.
10. Root `README.md`'s blanket "License declaration pending... portfolio-use-only" statement contradicts
    `projects/ecommerce-microservices-platform/`'s actual shipped MIT `LICENSE` file (its own README badges
    "License: MIT"). Reconcile — either scope the root statement to exclude ecommerce's licensed content, or
    otherwise resolve the contradiction; do not leave the two claims flatly opposed.

## Out of Scope

- `finance-platform` / `erp-platform` READMEs — hub-only, not standalone-published; not compared against a
  standalone-repo README in this task (only the root README's link-column claim about them, finding #1, is in
  scope).
- Actually publishing finance-platform / erp-platform as standalone repos — a separate, bigger decision (first
  publish, not resync), not bundled here.
- Any code change — this task is documentation-only. If a README claim and the actual code genuinely disagree in
  a way that isn't "README is stale" (e.g. a real functional gap), that is a separate finding to file as its own
  task, not silently papered over here.
- Force-pushing the corrected READMEs to the 5 standalone repos — that happens on the *next* portfolio sync batch
  cadence (`scripts/sync-portfolio.sh`), once this PR is merged to `main`. This task only fixes the monorepo
  source-of-truth files.
- Architecture diagrams requiring image/diagram-tool output (e.g. Mermaid is fine if the existing README already
  uses Mermaid; do not introduce a new diagramming toolchain).

---

# Acceptance Criteria

- [ ] **AC-0 (re-verify gate)** — Before rewriting each README's stale sections, re-confirm the current shipped
      service list against `settings.gradle` + `projects/<name>/PROJECT.md` + actual `apps/*` directory
      contents (not against the README's own prior claims, and not by trusting this task's list without
      re-checking — time may have passed since the survey).
- [ ] **AC-1** — Root `README.md`'s project table: iam-platform shows its real standalone link; finance-platform
      and erp-platform show `_(not yet published)_` (or equivalent, matching the table's existing convention for
      unpublished rows) instead of live-looking links; scm-platform row lists all 5 services.
- [ ] **AC-2** — `projects/wms-platform/README.md`, `projects/fan-platform/README.md`,
      `projects/scm-platform/README.md`: Status section, service map / architecture diagram, and any
      "Known Limitations" section accurately reflect the fully-implemented state confirmed by AC-0. No section
      claims a shipped service is "spec-only," "v2," "not yet built," or "skeleton."
- [ ] **AC-3** — `projects/ecommerce-microservices-platform/README.md`: Auth replaced by settlement-service in
      the Services table, architecture diagram, Swagger docs table, and k6 scenario references (or Auth section
      removed and settlement-service added, whichever preserves table/diagram structure most cleanly).
- [ ] **AC-4** — `projects/iam-platform/README.md` and `projects/ecommerce-microservices-platform/README.md`
      each contain a hub-backlink to monorepo-lab and an AI-collaboration/"how this was built" mention;
      ecommerce's README has a CI badge.
- [ ] **AC-5** — `projects/fan-platform/README.md`'s CI badge points at `kanggle/fan-platform/actions`; its
      community-service differentiation section reflects the service's retirement (past tense or dead-link
      removed).
- [ ] **AC-6** — Root `README.md`'s license statement and ecommerce's MIT `LICENSE`/README badge no longer flatly
      contradict each other.
- [ ] **AC-7** — All 6 touched README files render as valid Markdown (no broken table syntax, no dangling links
      introduced) — spot-check by reading the diff, no automated linter required (none exists in this repo for
      README prose per `docs/guides/` being human-reference-only, not CI-linted).
- [ ] **AC-8** — Every claim added or changed in these 6 files is traceable to a concrete, checked fact (a file
      that exists, a `settings.gradle` entry, a `PROJECT.md` statement, a task ID) — no new speculative or
      aspirational claims introduced in the course of fixing stale ones.

---

# Related Specs

- `project_portfolio_submission_strategy.md` (monorepo-lab operator memory, not a repo file) — the dual-deployment
  rationale this task is in service of; not itself a spec but explains *why* standalone README accuracy matters.
- `scripts/sync-portfolio.sh` — defines which projects are actually published (`PROJECT_REMOTES`) vs merely
  registered, and the hoist-to-root mechanism that makes `projects/<name>/README.md` double as the standalone
  repo's README.
- `projects/wms-platform/PROJECT.md`, `projects/fan-platform/PROJECT.md`, `projects/scm-platform/PROJECT.md`,
  `projects/ecommerce-microservices-platform/PROJECT.md`, `projects/iam-platform/PROJECT.md` — authoritative
  current classification/status for each touched project.

# Related Contracts

- None — documentation-only task, no API/event contract touched.

---

# Target Service

- N/A (cross-project documentation; root `README.md` + 5 projects' `README.md`)

---

# Architecture

N/A — no code architecture involved. Follow existing README structure/section conventions within each file
(this task edits within established sections, it does not redesign each README's layout).

---

# Implementation Notes

- Root `README.md` and the 5 projects' `README.md` are independent files with no structural overlap — safe to
  edit in parallel (e.g. dispatched to separate reviewers/passes) without merge-conflict risk, as long as all
  land in one PR (CLAUDE.md § Cross-Project Changes — this is a cross-project structural change, one atomic PR).
- Use `wms-platform/README.md`'s existing "🧭 개발 방식" + "🔗 관련 링크" sections as the template for AC-4's
  hub-backlink + AI-collaboration additions to iam-platform/ecommerce READMEs — don't invent a new format when
  reproducing this exact repo's existing convention is straightforward.
- For AC-1's link-column format, match whatever convention the table already uses for genuinely
  monorepo-only projects (if any exist in the table) rather than inventing new prose.

---

# Edge Cases

- If AC-0's re-verification finds the actual current state has moved further since the 2026-08-04 survey (e.g.
  a 6th service shipped in the meantime), reflect the *current* state, not the survey's snapshot — same
  "re-measure, don't inherit" discipline as every other AC-0 gate in this repo.
- finance-platform/erp-platform: if, by implementation time, either has actually been published as a standalone
  repo (a decision explicitly out of scope here), STOP that portion and re-scope — do not silently update their
  link-column status without also verifying README-parity for whichever one changed.

---

# Failure Scenarios

- **F1 — fixing one project's README while leaving root README's corresponding row stale (or vice versa)**:
  guarded by doing all 6 files in one PR/one task, with AC-0 applied uniformly — a partial fix that only
  updates the project README but not root (or root's scm-platform service count but not the project's own
  README) reintroduces the exact drift this task exists to close.
- **F2 — overcorrecting into new unverifiable claims**: a rewritten "this project is complete" narrative that
  goes further than the checked facts (e.g. claiming test coverage numbers, performance characteristics, or
  production-readiness beyond what `settings.gradle`/`PROJECT.md` actually support). Guarded by AC-8.
- **F3 — silently deciding to publish finance/erp as standalone while "just fixing the README table"**: the
  correct fix for finding #1 is marking them accurately as not-yet-published, not force-pushing new standalone
  repos into existence. Guarded by Out of Scope.

---

# Test Requirements

- No automated test suite covers README prose in this repo. Verification is manual: re-read each of the 6 files
  post-edit against the AC-0 ground-truth check, and diff-review for accidental Markdown breakage (AC-7).

---

# Definition of Done

- [ ] Root `README.md` project table corrected (AC-1).
- [ ] `wms-platform`, `fan-platform`, `scm-platform` READMEs rewritten to reflect actual shipped scope (AC-2).
- [ ] `ecommerce-microservices-platform` README's Auth→Settlement swap done (AC-3).
- [ ] Hub-backlink + AI-collaboration sections added to `iam-platform` + `ecommerce-microservices-platform`
      READMEs; ecommerce CI badge added (AC-4).
- [ ] `fan-platform` CI badge fixed + community-service reference updated (AC-5).
- [ ] Root README / ecommerce license contradiction resolved (AC-6).
- [ ] All 6 files Markdown-valid, all new claims fact-checked (AC-7, AC-8).

---

# Provenance

Surfaced 2026-08-04, immediately following the same-day 5-axis portfolio standalone re-sync (`sync-portfolio.sh`,
5/5 `exit=0`) — the resync made stale standalone READMEs freshly live-published again, raising the stakes of the
drift. User explicitly scoped this follow-up to "허브+5축 standalone README 정합성" (out of 4 possible scopes
offered). Findings sourced from a dedicated read-only Explore-agent survey this session, cross-checked against
`settings.gradle`, each project's `PROJECT.md`, actual `apps/*` directory contents, and `.github/workflows/ci.yml`
— not from the READMEs' own prior claims.

분석=Sonnet 5 / 구현 권장=Sonnet 5 (documentation rewrite against clearly-enumerated, already fact-checked ground
truth; no architecture judgment left open — the one real decision point, whether to publish finance/erp
standalone, is explicitly deferred as Out of Scope / F3).
