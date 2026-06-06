# Task ID

TASK-MONO-185

# Title

**Shared-layer `gap`→`iam` alignment (full consistency)** — sweep the remaining current-architecture `GAP` / `Global Account Platform` references out of the **shared** paths (`platform/`, `rules/`, `docs/guides/`, `TEMPLATE.md`, `scripts/`) that the MONO-179/180 rename left as prose/comment residue. Per explicit user decision (2026-06-07), this **extends the MONO-182/183 reviewer-facing alignment to the shared engineering layer**, reversing — for the shared layer only — MONO-180's "backend/contract prose `GAP` = intentional residue" stance. Docs/comments only; no behavior change.

# Status

done

# Owner

claude (Opus 4.8) — monorepo-level shared-path alignment (`platform/` + `rules/` + `docs/guides/` + `TEMPLATE.md` + `scripts/`). One atomic PR (CLAUDE.md § Cross-Project Changes; MONO-182/183 reality-alignment cadence).

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- onboarding

---

# Dependency Markers

- **선행/맥락**: gap→iam rename 179(#1149)/180(#1151) + docs alignment 182(#1155 project-overview)/183(#1157 README). 179/180 은 **operator-visible/reviewer-facing 표면만** `GAP`→`IAM` 정합하고 backend/contract/engineering-internal 산문 `GAP`은 **residue 로 의도적 보존**(memory `project_gap_to_iam_rename` residue 버킷). 본 task 는 사용자가 **full consistency** 를 선택(2026-06-07 AskUserQuestion)하여 그 residue 정책을 **shared 레이어에 한해 뒤집고** 정합.
- **trigger**: shared 레이어 drift 실측(`\bGAP\b` grep) — `platform/` ×1 file, `rules/` ×1, `docs/guides/` ×3, `TEMPLATE.md`, `scripts/` ×5. `.github/`/`infra/`/`libs/`/`tasks/templates/`/`CLAUDE.md` = 이미 0.
- **부수 효과**: `TEMPLATE.md` 의 `## GAP IdP Integration Pattern` heading → `## IAM IdP Integration Pattern` 정합이 **scm/finance/erp README 의 `TEMPLATE.md#iam-idp-integration-pattern-new-projects` inbound 앵커**(heading 이 `#gap-...` 이라 깨져있던 — MONO-184 file-existence 체크는 앵커 미검사)를 **resolve** 시킴. 그리고 `scripts/extract-template.sh` 가 신규 프로젝트에 `## TODO: GAP IdP Integration` + "Global Account Platform (GAP)" 를 **생성**하던 forward-propagation 차단(TEMPLATE.md §pattern 과 동일 정합).

# Goal

After this task, no **current-architecture** `GAP` / `Global Account Platform` reference remains in the shared paths (`platform/`, `rules/`, `libs/`, `tasks/templates/`, `docs/guides/`, `TEMPLATE.md`, `scripts/`, root `README.md`). The IdP project is named `IAM` everywhere in shared docs/scripts. No code behavior changes (all `scripts/` hits are comments / echo strings / generated-template heredoc text — zero functional `gap-*`/`gap.local` identifiers, verified). The only remaining shared-layer `GAP` is a historical task-batch label and is out of scope (below).

# Scope

## In Scope (11 files, uppercase `GAP`→`IAM`; case-sensitive, collision-free — every `GAP` is the IdP project name)

**Shared docs (6):**
1. `platform/error-handling.md` (×10) — error-code emitter labels (`GAP auth-service`, `GAP IdP`, `GAP community`/`admin-service`).
2. `rules/domains/fan-platform.md` (×4) — IdP integration prose (`GAP (iam-platform)` half-renamed line → `IAM`).
3. `docs/guides/monorepo-workflow.md` (×3) — keeps lowercase English "the gap" L282 byte-unchanged (case-sensitive replace).
4. `docs/guides/dev-tooling.md` (×2).
5. `docs/guides/console-fullstack-local-dev.md` (×7).
6. `TEMPLATE.md` (×33) — incl. `## GAP IdP Integration Pattern` heading + the `## GAP IdP Integration` PROJECT.md template + ADR-table label + env comments; **+ lowercase project-slug** `(fan-platform, wms, gap, ecommerce)` L253 → `iam` (targeted, only lowercase project-slug in the file).

**Shared scripts (5) — comments / echo / generated-template text only (no functional identifier):**
7. `scripts/sync-portfolio.sh` (×6) — ecommerce-exclusion rationale comments (GAP OIDC cutover).
8. `scripts/console-demo-up.sh` (×2) — operator-facing demo echo.
9. `scripts/extract-template.sh` (×7) — generated `## TODO: GAP IdP Integration` + `Global Account Platform (GAP)` phrase + env comments (full-phrase replaced to `Identity & Access Management (IAM)`).
10. `scripts/console-demo-up.ps1` (×3) — demo echo.
11. `scripts/console-demo/seed/01-iam.sql` (×3) — SQL comments.

## Out of Scope

- **`.claude/hooks/__tests__/hardstop-10-service-type-missing.ps1` L66** — `MONO-097 GAP / MONO-098 ecommerce` is a **historical task-batch label** (the name of a past migration batch), legitimate dated residue; AND `.claude/` is classifier-blocked for the agent. No change.
- **Project-level backend/contract spec prose `GAP`** (e.g. `projects/*/specs/.../*.md`, console-integration-contract) — MONO-180 residue; this task aligns the **shared** layer only (user's scope). A separate decision would be needed to extend to project-internal spec prose.
- **Flyway migration bodies, `tasks/done/**`, ADR pre-amendment bodies, English-word "gap", Tailwind `gap-*`** — residue buckets (unchanged).
- Any functional code / behavior — all `scripts/` edits are comment/echo/heredoc text; verified no `gap-*`/`gap.local`/`$gap`/`gap:` functional identifier exists in `scripts/`.

# Acceptance Criteria

- AC-1: `git grep -n "\bGAP\b\|Global Account Platform"` across the shared paths (`platform/ rules/ libs/ tasks/templates/ docs/guides/ TEMPLATE.md CLAUDE.md .github/ infra/ scripts/`) returns **0** (was: 6 docs files + 5 scripts files).
- AC-2: `TEMPLATE.md` heading is `## IAM IdP Integration Pattern (New Projects)` → anchor `#iam-idp-integration-pattern-new-projects` resolves the scm/finance/erp README inbound links.
- AC-3: No behavior change — `scripts/` diff is comments/echo/heredoc text only; no functional identifier touched; `.sh`/`.ps1`/`.sql` shebangs + encoding preserved (UTF-8 no-BOM, line endings unchanged).
- AC-4: Lowercase English "the gap" (`docs/guides/monorepo-workflow.md` L282) byte-unchanged (case-sensitive `.Replace`).

# Related Specs

- None (shared docs/scripts alignment).

# Related Contracts

- None. `platform/error-handling.md` is the shared error-code **catalog** — only the emitter-service NAME labels align (`GAP`→`IAM`); no code/status/semantic change.

# Edge Cases

- **`GAP`→`IAM` is collision-free in all 11 files** — every uppercase `GAP` denotes the IdP project (no English-word/acronym `GAP`; verified per-file via content grep). Case-sensitive `.Replace('GAP','IAM')` leaves lowercase English "the gap" + all `iam-*` paths untouched. Same technique as MONO-183.
- **`Global Account Platform (GAP)`** (extract-template.sh L457, the only full-name in the shared layer) → `Identity & Access Management (IAM)` (full-phrase replace before the `GAP`→`IAM` pass, else it would read "Global Account Platform (IAM)").
- **scripts are CODE** — but every `GAP` hit is in a `#`/`--` comment, an `echo`/`Write-Host` string, or generated-template heredoc text; no functional `gap-*` identifier (179 already cleaned those; re-verified 0 in `scripts/`). Cosmetic only.

# Failure Scenarios

- **Touching a functional identifier** → script break. Verified `scripts/` has zero functional `gap`-token (`gap-`/`gap.`/`gap:`/`$gap`); all hits cosmetic.
- **`Global Account Platform (IAM)` half-rename** → handled by full-phrase replace first.
- **BOM/CRLF corruption of `.sh`** → `[System.IO.File]::WriteAllText` with `UTF8Encoding($false)` preserves no-BOM + existing line endings (ReadAllText→Replace→WriteAllText does not convert).
- **Re-introducing `GAP`** → re-run AC-1 grep (0).
