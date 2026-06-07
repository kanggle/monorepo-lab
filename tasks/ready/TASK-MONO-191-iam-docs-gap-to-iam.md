# Task ID

TASK-MONO-191

# Title

Align **iam-platform's own `docs/`** (4 self-ADRs + migration-notes) project-name references GAP → IAM, the last GAP-prose surface in the repo outside intentional retentions. MONO-188 aligned iam's `specs/` but `/refactor-spec` does not reach `docs/`; consumer `knowledge/` is already 0. User chose full alignment (accepting the historical-ADR anachronism) for project-name consistency. Contract values (issuer claim) and verbatim historical CI evidence are retained.

# Status

ready

# Owner

claude (Opus 4.8) — single-project docs prose alignment (iam-platform `docs/`, 5 files). One atomic PR (gap→iam arc precedent; docs-only fast-lane). Prose/path-only; zero code/contract-value change.

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code

---

# Dependency Markers

- **선행**: MONO-179 (rename), MONO-188 (iam `specs/` GAP→IAM — this is the symmetric `docs/` pass), MONO-190 (consumer `specs/`). With 191 the **GAP-prose dimension reaches 0 repo-wide** (knowledge/ already 0; only retentions remain).
- **사용자 결정 (2026-06-07)**: iam 자체 docs 의 historical ADR 도 "전체 정합" 선택 — historical decision-record 의 "GAP" 가 현재 프로젝트명 "IAM" 으로 바뀌는 시대착오(anachronism)를 감수하고 프로젝트명 일관성을 택함. (MONO-179 의 "ADR historical body 보존" 기본정책을 iam 자체 docs 한정 override.)

# Goal

After this task, iam-platform's `docs/` carries no GAP project-name prose: `git grep -lwE "GAP" -- projects/iam-platform/docs` returns **0 files**. Two `global-account-platform` strings remain by design (issuer contract value + verbatim CI-log evidence). Lowercase `gap` unchanged.

# Scope

## In Scope — iam-platform `docs/` (3 transforms)

1. **`(?<![A-Za-z])GAP(?![A-Za-z])` → `IAM`** — 46 uppercase project-name refs across ADR-001/003/004/005 + migration-notes (all prose: `GAP IdP`/`GAP 전용`/`GAP's`/`GAP Integration`/`GAP issuer`/`GAP 는`/`GAP 를` …).
2. **`` `global-account-platform` `` → `` `iam-platform` ``** (backtick-wrapped prose project-name) — ADR-001:20, ADR-002:13 (2).
3. **`projects/global-account-platform/` → `projects/iam-platform/`** (inline-code source paths; the directory is actually renamed) — ADR-003:397, ADR-004:366 (2).

## Out of Scope (retentions — do NOT change)

- **`iss=global-account-platform`** (ADR-001:25) — issuer **contract value** in historical design context (the runtime `iss` flip is a separate persisted concern; MONO-179 retains contract values).
- **`Integration (global-account-platform, Testcontainers) PASS …`** (ADR-004:336) — **verbatim CI-run output** pasted as historical evidence; altering quoted evidence is dishonest.
- **lowercase `gap`** — rejected-Option-C hypothetical lib `libs/java-gap-client` + `GapJwtVerifier`/`GapClient`/`GapAuthFilter` class names (never built), `gap-platform` standalone-repo prose, `feat/gap-be-273-…` branch name. All lowercase → out of the uppercase match; all historical/hypothetical.
- iam `specs/` (MONO-188), repo-root `docs/adr/` links (MONO-189), consumer `specs/` (MONO-190).

# Acceptance Criteria

- AC-1: `git grep -lwE "GAP" -- projects/iam-platform/docs` = **0 files** (was 5).
- AC-2: `git grep -c "global-account-platform" -- projects/iam-platform/docs` = exactly **2 lines** (ADR-001:25 `iss=`, ADR-004:336 CI-log), both unchanged.
- AC-3: lowercase `gap` count under `projects/iam-platform/docs` unchanged = **7** (java-gap-client ×5, gap-platform, feat/gap-be-273 branch).
- AC-4: `git diff` touches only `projects/iam-platform/docs/**`; every changed line is a GAP→IAM or project-name/path swap; no markdown link target altered.

# Related Specs

- None (decision records + migration note). iam `specs/` already aligned (MONO-188).

# Related Contracts

- None changed. The issuer claim value (`iss`) is explicitly retained in its historical-design line.

# Edge Cases

- **Historical anachronism (accepted)** — ADR-001 records the decision to promote "GAP"; renaming to "IAM" makes the narrative read as if the project was already IAM at decision time. User accepted this for consistency. (Contrast: repo-root ADR-MONO-013 retained historical-body GAP — the policy here is iam-docs-specific.)
- **Backtick-anchored project-name swap** — replacing `` `global-account-platform` `` (backtick-wrapped) — not the bare string — avoids touching the `iss=` value and the CI-log paste, which use different delimiters.
- **Order** — GAP→IAM first, then the `global-account-platform` swaps (independent tokens; no interaction).

# Failure Scenarios

- **Blind `global-account-platform`→`iam-platform`** → corrupts the `iss=` contract value and the verbatim CI-log evidence. Prevented by the delimiter-anchored replacements + AC-2 (exactly 2 retained).
- **Case-insensitive GAP replace** → hits lowercase `java-gap-client` / `gap-platform` / branch name. Prevented by the uppercase lookaround + AC-3.
