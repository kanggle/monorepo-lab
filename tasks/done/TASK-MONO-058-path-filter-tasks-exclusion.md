# Task ID

TASK-MONO-058

# Title

`.github/workflows/ci.yml` path-filter — exclude `projects/<name>/tasks/**` from project flag

# Status

done

# Owner

monorepo

# Task Tags

- ci
- chore

---

# Goal

Currently each project flag in `dorny/paths-filter@v3` matches `projects/<name>/**`, which includes `projects/<name>/tasks/**`. A project-internal chore PR (e.g., `tasks/review/X.md → tasks/done/X.md` move) therefore triggers the full project Integration + E2E + boot-jars suite (~3-5 min wasted CI), even though no code or spec changed.

Refinement: add `!projects/<name>/tasks/**` exclusion to each of the 5 project flags. Project-internal chore PRs (tasks/-only) will then skip the project's heavy jobs — same SKIP behavior root tasks/-only PRs already enjoy (TASK-MONO-045 baseline 47x speedup).

Memory provenance: `project_scm_be_series_in_progress.md` § 후속 (2026-05-09 갱신) candidate "TASK-MONO-049 path-filter refinement — `projects/<name>/tasks/**` 만 변경된 push에는 해당 project 잡들 SKIP. 미발행 — 후보로 유지". (Numbered as MONO-049 in memory but actually a new task; MONO-049 is taken by libs/java-messaging extraction.)

---

# Scope

## In Scope

- `.github/workflows/ci.yml` `changes` job filter section: append `'!projects/<name>/tasks/**'` to each of the 5 project flags (wms / ecommerce / gap / fan / scm).
- Update the "Filter rules" comment block (L33-44) to note the exclusion.
- Update the "Edge case — tasks/** or *.md only change" comment (L55-58) to reflect that project-internal tasks-only PRs also now skip.

## Out of Scope

- `projects/<name>/docs/**` / `knowledge/**` exclusions (separate consideration — those CAN affect downstream artifacts via `docs/` reads in some skill workflows; conservative scope = `tasks/**` only per memory).
- `nightly-e2e.yml` — already triggers via cron + push to main, not via path-filter. No change.
- Root-level `tasks/**` (already not in any flag).

---

# Acceptance Criteria

- [x] 5 project flags (wms / ecommerce / gap / fan / scm) each gain `'!projects/<name>/tasks/**'` exclusion entry.
- [x] Edge-case comment block accurately describes the new skip behavior.
- [x] Mixed PR (code change + tasks/ change in same PR) still triggers the project flag — positive pattern dominates negative when both match. Verified semantically via dorny/paths-filter@v3 minimatch semantics.
- [x] Push to main still triggers all jobs (`github.event_name == 'push'` fallback already in place).
- [x] Workflow self-change PR (this PR) triggers all jobs via `workflows` flag → CI run is full-pipeline regression guard.

---

# Related Specs

- `tasks/INDEX.md` § PR Separation Rule (close chore PR pattern that triggers tasks-only changes)
- `docs/guides/monorepo-workflow.md` § 6 CI Job Areas (path-filter mechanics)
- Memory `project_ci_path_filter_045.md` (TASK-MONO-045 baseline 47x speedup, this PR extends)
- Memory `project_scm_be_series_in_progress.md` § 후속 (origin of the refinement candidate)

# Related Skills

- N/A — CI infrastructure edit, no skill applies.

---

# Related Contracts

None.

---

# Target Service

N/A — shared CI configuration only.

---

# Architecture

N/A — single-file YAML edit.

---

# Implementation Notes

`dorny/paths-filter@v3` uses `picomatch` for pattern matching. A list of patterns is OR-evaluated for matches; `!` prefix marks negation. Standard rule: a file is "in" if any positive pattern matches AND no negation pattern matches.

For project-internal mixed PRs (code + tasks/):
- The code change matches the positive `'projects/<name>/**'` pattern → file is "in".
- The tasks/-only files match both positive (`projects/<name>/**`) AND negation (`projects/<name>/tasks/**`) → those files are "out".
- The flag is `true` if AT LEAST ONE file matches positively without being negated. Code file matches → flag true → project's jobs activate.

For project-internal tasks-only PRs:
- All files match positive AND negation → all files "out" → flag is `false`.
- Project's Integration / E2E / boot-jars jobs SKIP.
- `workflows` and `libs` flags are also false → only `build-and-test` runs via push-to-main fallback (if main) or nothing (if PR).

---

# Edge Cases

- **Mixed code + tasks/ PR**: project flag still true (positive wins for code paths). Same behavior as today for non-tasks/-only PRs.
- **Tasks/ + libs/ PR**: libs flag dominates (activates all). Tasks exclusion has no negative impact.
- **Tasks/ + workflows/ PR**: same — workflows flag activates all.
- **Tasks/-only PR on main push**: `github.event_name == 'push'` fallback ensures build-and-test still runs.
- **Cross-project tasks/-only PR** (multiple projects' tasks moved in one chore PR): each project's tasks/** files match the corresponding project's negation. All 5 project flags false. Same SKIP behavior as desired.

---

# Failure Scenarios

- **`!` prefix not honored by paths-filter version**: dorny/paths-filter@v3 docs confirm negation support. Cycle 1 verification = self-CI of this PR (workflows flag → full pipeline runs, no regression).
- **CI yaml parse error**: GitHub Actions surface immediately on push, blocks the workflow. PR self-CI catches.
- **Subtle minimatch quirk** (e.g., `tasks/` vs `tasks/**`): pattern uses `**` (recursive) to cover all subdirectories. Same pattern used elsewhere in the file for positive matching.

---

# Test Requirements

- **Self-CI verification (this PR)**: triggers `workflows` flag → all jobs activate. Full pipeline must remain green (regression guard against the yaml edit itself).
- **Deferred verification (next natural chore PR)**: any subsequent `projects/<name>/tasks/`-only chore PR will be the first real exercise. Expected: `Integration (<project>)`, `E2E (<project>)`, `Package boot jars (<project>)` jobs all SKIP for that PR. `build-and-test` skips for PR (no project flag true, no libs/workflows true).

---

# Definition of Done

- [x] `.github/workflows/ci.yml` 5 project flags include `!projects/<name>/tasks/**`.
- [x] Comment block updated.
- [x] `tasks/INDEX.md` done entry added.
- [x] Self-CI green (workflows flag full-pipeline verification).
- [x] Memory `project_scm_be_series_in_progress.md` candidate "path-filter refinement" closed (entry updated post-merge).

---

# Provenance

Surfaced by memory `project_scm_be_series_in_progress.md` § 후속 (2026-05-09 — 2026-05-11 갱신). Codified as a discrete task on 2026-05-11 after `/validate-rules` PASS confirmed rules library stable.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical yaml edit, well-defined acceptance criteria).
