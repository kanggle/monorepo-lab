# Task ID

TASK-MONO-128

# Title

Root `docs/project-overview.md` post-Phase-5/6/7 reality-alignment sweep — BE-302/305/PC-FE-015 pattern 5회째 (portfolio-front-page stale narrative closure)

# Status

ready

# Owner

monorepo (root tasks/, doc-only)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- onboarding

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

- **depends on**: nothing. All referenced reality is on `origin/main`:
  - finance v1 live — FIN-BE-001 DONE + FIN-BE-002/003/004 (honest green-wash chain TRUE TERMINAL) + FIN-BE-005 (platform-console operator read consumer reconciliation) all DONE 2026-05-19/20.
  - erp v1 live — ERP-BE-001 (masterdata-service Hexagonal) + ERP-BE-002 (platform-console operator read consumer reconciliation) + MONO-124 (erp-integration CI job) all DONE 2026-05-20.
  - console-bff v1 lands — TASK-PC-BE-001 console-bff skeleton DONE 2026-05-20 + TASK-PC-FE-011 MVP "Operator Overview" composition route § 2.4.9.1 DONE 2026-05-20 + TASK-PC-FE-013 "Domain Health Overview" composition route § 2.4.9.2 DONE 2026-05-21.
  - TASK-BE-305 GAP `ProductCatalog` finance/erp `available: true` reality-alignment DONE 2026-05-21 (producer side: `console-registry-api.md` + `multi-tenancy.md`).
  - TASK-PC-FE-015 platform-console consumer-side narrative sweep DONE 2026-05-21 (`PROJECT.md` + `console-integration-contract.md` 5 surgical edits).
- **origin**: surfaced during the TASK-PC-FE-015 post-merge audit (2026-05-21). BE-305 closed the GAP producer-side stale narrative; PC-FE-015 closed the platform-console consumer-side stale narrative; the **monorepo-root portfolio-front-page** `docs/project-overview.md` carries the same stale narrative for finance + erp + console-bff. This task is the natural **5회째 reality-alignment** of the same post-Phase-5/6/7 + Option (a) Phase 2 closure chain.
- **prerequisite for**: nothing (this closes the root-level surface of the pattern).
- **spec-first**: spec PR (this task md + root INDEX entry, markdown fast-lane) → impl PR (5 surgical edits in `docs/project-overview.md`) → close chore PR.
- **no ADR** (BE-302 / BE-305 / PC-FE-015 reality-alignment pattern, 5회째): every governing decision (ADR-008/013/014/015/016/017) is already ACCEPTED; every Phase 5/6/7 + Option (a) Phase 2 chain is merged. This task aligns the root portfolio narrative; competing convention 부재 → ADR-trigger N/A.

---

# Goal

`docs/project-overview.md` is the **portfolio front page** — the file evaluators / collaborators read first to understand the monorepo's domain composition + Phase status. Post-Phase-5/6/7 + Option (a) Phase 2 closure it carries 5 stale narrative drifts (same pattern BE-305 / PC-FE-015 closed elsewhere in the portfolio):

| # | Line | Stale | Live reality |
|---|---|---|---|
| 1 | L128 | `console-bff: rest-api: 교차 도메인 집약 API (ADR-MONO-013 Phase 7, deferred)` | console-bff lands (TASK-PC-BE-001 DONE 2026-05-20) + MVP `§ 2.4.9.1 Operator Overview` (TASK-PC-FE-011) + `§ 2.4.9.2 Domain Health` (TASK-PC-FE-013) merged |
| 2 | L130 | finance section header `(v1 부트스트랩 🚧)` (in-progress emoji) | FIN-BE-001~005 모두 DONE; FE-009 console section live; Phase 5 COMPLETE |
| 3 | L134 | `finance 도메인 구현 = TASK-FIN-BE-001 (deferred)` | FIN-BE-001 DONE 2026-05-19; entire chain DONE |
| 4 | L147 | erp section header `(v1 부트스트랩 🚧)` (in-progress emoji) | ERP-BE-001 + ERP-BE-002 + MONO-124 모두 DONE; FE-010 console section live; Phase 6 COMPLETE |
| 5 | L151 | `erp 도메인 구현 = TASK-ERP-BE-001 (deferred)` | ERP-BE-001 DONE 2026-05-20; entire chain DONE |

Each is a single-line (or single-emoji) fix that aligns the front-page narrative to live state. None require code change. None require ADR amendment.

# Decision authority (why no ADR, why root tasks/, why doc-only)

- **No ADR (BE-302 / BE-305 / PC-FE-015 reality-alignment pattern, 5회째)**: governing decisions (ADR-MONO-008 finance / ADR-MONO-013 § D6 Phase 5/6/7 / ADR-MONO-014 / ADR-MONO-015 / ADR-MONO-016 erp / ADR-MONO-017 console-bff) all ACCEPTED. Phase 5/6/7 + Option (a) Phase 2 chains all merged. This task aligns the root portfolio narrative; competing convention 부재 → ADR-trigger N/A.
- **Why root `tasks/` (not project-internal)**: `docs/project-overview.md` is a shared monorepo-level doc (per `CLAUDE.md § Repository Layout` — `docs/` at repo root is shared, no project owns it). Per `CLAUDE.md § Task Rules` "Monorepo-level work (shared paths: ... `docs/guides/`, ... root `build.gradle`/...) → task in repo-root `tasks/ready/`". Although the immediate target is `docs/project-overview.md` (not `docs/guides/`), the file is root-level shared content and the established convention for any root `docs/` doc-only sweep is root `tasks/`.
- **Why 3-PR chain (not single-PR)**: keeps lifecycle (spec / impl / close) consistent with every other root task (MONO-117/118/119/120/121/123/124/125/126/127 — all 3-PR chain) + with BE-302/305/PC-FE-015 sister tasks. Doc-only nature does not justify deviation.
- **Why no `🚧` flag globally removed (only finance + erp section headers)**: the `🚧` may remain valid for genuinely in-progress sections elsewhere; this task scope-locks to the 2 stale instances (finance + erp). If a sibling stale `🚧` surfaces during audit, it's logged honestly but a separate task per scope (not silently bundled).

---

# Scope

## In Scope

**Spec PR**:

- `tasks/ready/TASK-MONO-128-project-overview-md-post-phase-5-6-7-reality-alignment.md` — this task md.
- `tasks/INDEX.md` — ready entry.

**Impl PR (5 surgical edits, 1 file, doc-only)**:

- `docs/project-overview.md`:

  - **Edit 1 (L128)**: `| console-bff | rest-api | 교차 도메인 집약 API (ADR-MONO-013 Phase 7, deferred) |` → `| console-bff | rest-api | 교차 도메인 집약 API (ADR-MONO-013 Phase 7 LIVE — TASK-PC-BE-001 skeleton + § 2.4.9.1 Operator Overview + § 2.4.9.2 Domain Health) |`
  - **Edit 2 (L130)**: finance section header — drop `🚧` emoji + update phase narrative. BEFORE: `### 2.7 [finance-platform](...) — 비은행 금융 서비스 (v1 부트스트랩 🚧)` → AFTER: `### 2.7 [finance-platform](...) — 비은행 금융 서비스 (v1 live — Phase 5 COMPLETE 2026-05-19/20)`
  - **Edit 3 (L134)**: BEFORE: `- 상태: v1 부트스트랩 (TASK-MONO-114) — projects/finance-platform/ tree + account-service 부트 가능 skeleton (비즈니스 로직 0) + PROJECT.md/specs/GAP V0017 시드 + monorepo wiring. 도메인 구현 = TASK-FIN-BE-001 (deferred).` → AFTER: 상태 narrative updated to reflect: bootstrap done (MONO-114) + account-service v1 live (FIN-BE-001~005 chain DONE 2026-05-19/20) + platform-console federation live (FE-009 `/finance` console section + § 2.4.7 per-domain credential).
  - **Edit 4 (L147)**: erp section header — drop `🚧` + update phase narrative. BEFORE: `### 2.8 [erp-platform](...) — 전사 기간계 (v1 부트스트랩 🚧)` → AFTER: `### 2.8 [erp-platform](...) — 전사 기간계 (v1 live — Phase 6 COMPLETE 2026-05-20)`
  - **Edit 5 (L151)**: BEFORE: `- 상태: v1 부트스트랩 (TASK-MONO-119) — projects/erp-platform/ tree + masterdata-service 부트 가능 skeleton (비즈니스 로직 0) + PROJECT.md/specs/GAP V0018 시드 + monorepo wiring. 도메인 구현 = TASK-ERP-BE-001 (deferred).` → AFTER: 상태 narrative updated to reflect: bootstrap done (MONO-119) + masterdata-service v1 live (ERP-BE-001 + MONO-124 + ERP-BE-002 chain DONE 2026-05-20) + platform-console federation live (FE-010 `/erp` console section + § 2.4.8 per-domain credential).

## Out of Scope

- **Any other line in `docs/project-overview.md`** — scope-locked to the 5 stale narratives above. If a sibling stale narrative surfaces during impl, it is **logged honestly** in the PR description but **not silently bundled** — a separate task per scope (BE-302/305 discipline).
- **Other monorepo-level docs** (`README.md`, `TEMPLATE.md`, `CLAUDE.md`, `docs/guides/*`, `platform/*.md`, `rules/*.md`, ADRs) — byte-unchanged in this task. If similar drift surfaces during audit, separate task.
- **ADR amendment** — none. ADR-008/013/014/015/016/017 all byte-unchanged. The "future erp ADR" references in ADR-MONO-013 § D6 row 6 / § 4 / § Comparison table (line 114/159/185) are **historical PROPOSED-time text** within an ACCEPTED ADR; HARDSTOP-04 immutability discipline says D1-D8 decision text is byte-unchanged. ADR amendment would require its own staged PROPOSED→ACCEPTED ADR (out of scope here, separate task if needed).
- **Code, tests, schema, specs/** — all byte-unchanged.
- **`projects/finance-platform/PROJECT.md` / `projects/erp-platform/PROJECT.md`** — out of scope (project-internal, separate task if their narratives carry the same stale `🚧` / "deferred" — initial audit suggests both are accurate as project-internal `PROJECT.md` was updated by the bootstrap tasks themselves).
- **`docs/adr/*.md`** — see ADR amendment exclusion above. The "future erp ADR" references are historical-PROPOSED-time text in ACCEPTED ADRs; HARDSTOP-04 discipline (additive notes only).
- **Project-internal task migrations** (rename / move) — none.

# Acceptance Criteria

- **AC-1 (spec PR atomic)**: spec PR contains exactly 2 files (this task md + root `tasks/INDEX.md` entry). No production code, no spec md edits. Markdown path-filter fast-lane.
- **AC-2 (impl PR surgical, scope-locked)**: impl PR `git diff --stat origin/main` shows exactly **1 file modified** — `docs/project-overview.md` only. No code, no test, no other doc. ≤10 line delta (the 5 edits are 1-2 lines each).
- **AC-3 (BE-305/PC-FE-015 narrative reuse)**: the AFTER text for the finance/erp section headers + 상태 bullets cross-references the Phase 5/6 COMPLETE marker and the corresponding task chain verbatim from BE-305/PC-FE-015 spec PR (which used the same "V1 live per ADR-MONO-013 § D6 Phase 5/6 COMPLETE 2026-05-19/20" framing). No new narrative introduced; root portfolio narrative aligns to the producer-side / consumer-side language already merged.
- **AC-4 (no ADR amendment)**: `git diff --stat origin/main -- docs/adr/` = **empty**. The "future erp ADR" references in ADR-MONO-013 § D6 row 6 / § 4 / § Comparison table are NOT touched (HARDSTOP-04 discipline).
- **AC-5 (no projects/* re-touch)**: `git diff --stat origin/main -- projects/` = **empty** in impl PR.
- **AC-6 (no other root doc re-touch)**: `git diff --stat origin/main -- README.md TEMPLATE.md CLAUDE.md docs/guides/ platform/ rules/ libs/` = **empty** in impl PR.
- **AC-7 (`🚧` scope-locked)**: only finance + erp section headers have `🚧` removed. Other sections of `docs/project-overview.md` that may carry `🚧` (if any — checked during audit) stay byte-unchanged in this task; if a genuinely-stale sibling `🚧` is discovered, it's logged in the PR description for a separate task.
- **AC-8 (CI green)**: markdown fast-lane only (`changes` + `Frontend E2E smoke` pass; all Gradle / Integration jobs skipped). No code/test job triggered. **BE-303 3-dim verified at close chore** per [`CLAUDE.md § Task Rules`](../../CLAUDE.md).

# Related Specs

- `docs/project-overview.md` § 2.7 finance-platform + § 2.8 erp-platform + § 2.9 platform-console service map (line 128) — the edited section + line.
- `projects/finance-platform/PROJECT.md` — read-reference (project-internal status, not edited here).
- `projects/erp-platform/PROJECT.md` — read-reference (same).
- `projects/platform-console/PROJECT.md` — read-reference (PC-FE-015 already updated 2026-05-21 — consumer-side narrative aligned, not re-touched here).
- `projects/platform-console/specs/contracts/console-integration-contract.md § 2.4.9.1 + § 2.4.9.2` — read-reference (the live composition routes the L128 edit refers to).

# Related Contracts

- None. This task is portfolio-narrative-doc only; no contract change.

# Edge Cases

- **Other `🚧` instances in `docs/project-overview.md`** — if any are discovered during impl, each is scope-locked (AC-7 + Out of Scope). Audit checks confirm only finance + erp section headers carry `🚧` for the post-Phase-5/6 stale state.
- **Other `(deferred)` / `(WIP)` / `(in-progress)` mentions** — if a sibling stale state is discovered, logged in PR description for separate task, not silently bundled.
- **`projects/finance-platform/PROJECT.md` / `projects/erp-platform/PROJECT.md` carrying same stale `🚧`** — out of scope per AC-5; if discovered stale, separate project-internal task.
- **ADR-MONO-013 § D6 row 6 / § 4 / § Comparison table "future erp ADR" references** — these are PROPOSED-time text frozen in ACCEPTED ADR per HARDSTOP-04 immutability (additive notes only). The portfolio-doc this task edits is not an ADR — straightforward edit. ADR-013 stays byte-unchanged.
- **Korean narrative consistency**: `docs/project-overview.md` is Korean-primary (per existing style); AFTER text continues in Korean for consistency.
- **Phase 7 reference granularity**: L128 AFTER reference to "§ 2.4.9.1" + "§ 2.4.9.2" is **section-level**, not task-id-level (the section IDs are the durable reference; task IDs are historical artifacts). Future Phase 7 dashboard additions extend `§ 2.4.9.3`, ... naturally per the additive pattern.

# Failure Scenarios

- **The impl PR also re-touches `docs/adr/*`** → AC-4 fail (HARDSTOP-04 immutability violation). **Reject in review.**
- **The impl PR also re-touches `projects/*/PROJECT.md` or project-internal docs** → AC-5 fail. **Reject** — sibling stale `🚧` in project-internal `PROJECT.md` (if any) is a separate project-internal task per § Out of Scope.
- **The impl PR also re-touches `README.md` / `TEMPLATE.md` / `CLAUDE.md` / `docs/guides/`** → AC-6 fail. **Reject** — sibling stale in those files is a separate task per § Out of Scope.
- **The impl PR removes `🚧` from a section that is genuinely in-progress** (e.g. a hypothetical "v2 부트스트랩 🚧" section) → over-correction; AC-7 scope-locks to finance + erp **post-Phase-5/6** stale headers only.
- **The AFTER text introduces a new architectural claim** (e.g. "Phase 7 will use SSE for cross-domain dashboards") → over-correction; AFTER text only updates *factual state* (what's merged, what remains future), not architectural intent.
- **A reviewer requests ADR amendment for the "future erp ADR" references** → reject per § Decision authority; ADR-013 ACCEPTED, HARDSTOP-04 immutability — additive notes only, separate staged ADR if architectural change.
- **A reviewer requests adding "deprecated" markers for the BEFORE text** → reject; the BEFORE text was current-at-author-time, not a public API contract that needs migration.

# Verification

1. Spec PR diff: exactly 2 files (this task md + root INDEX entry). `git diff --stat origin/main -- docs/` is **empty** in spec PR.
2. Impl PR diff: exactly 1 file (`docs/project-overview.md`). `git diff --stat origin/main -- projects/ docs/adr/ docs/guides/ README.md TEMPLATE.md CLAUDE.md platform/ rules/ libs/ tasks/` is **empty** in impl PR.
3. Markdown fast-lane: `changes` job + `Frontend E2E smoke` pass; all Gradle / Integration / build jobs SKIPPED.
4. AC-4 / AC-5 / AC-6 grep zero.
5. Self-CI 20/20 GREEN (markdown fast-lane); BE-303 3-dim verified at close chore start.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (BE-302/305/PC-FE-015 mechanical reality-alignment, 5 surgical doc edits in 1 file; no judgement beyond the already-decided fact + BE-305/PC-FE-015 spec language reuse) — or executed directly in this session given the very small scope (5 edits in 1 file, well-defined AFTER text) / 리뷰=Opus 4.7 (inline self-review + AC-2 surgical-diff + AC-4/5/6/7 scope-lock verification + BE-303 3-dim).
