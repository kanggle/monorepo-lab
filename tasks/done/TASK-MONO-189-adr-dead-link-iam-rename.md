# Task ID

TASK-MONO-189

# Title

Fix the **broken `docs/adr/` markdown navigation links** that still point at the renamed `projects/global-account-platform/…` directory (the project was renamed → `projects/iam-platform/` in the gap→iam arc, MONO-179). 16 link hrefs across 8 ADR files now 404; all targets exist under `iam-platform`. Href-only repair — link **labels** and all historical prose / inline-code path mentions / `gap` domain-slug are preserved verbatim (MONO-179 historical-record retention). Completes the dead-ref dimension into `docs/adr/`, which the spec-only (MONO-181) and README-only (MONO-184) checkers did not cover.

# Status

done

# Owner

claude (Opus 4.8) — monorepo-level dead-ref fix (8 files under shared `docs/adr/`, href-only). One atomic PR (single shared subtree; dead-ref arc precedent MONO-181/184/186/187).

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code

---

# Dependency Markers

- **선행/맥락**: gap→iam project rename (MONO-179, `projects/global-account-platform/` → `projects/iam-platform/`). The spec dead-ref batch (MONO-181) was specs-only; README batch (MONO-184) was README-only; neither swept `docs/adr/`. This is the surfaced `docs/adr/` residue noted in memory `project_gap_to_iam_rename` ("신규 finding=`docs/adr/` dangling `global-account-platform` 경로").
- **MONO-179 retention boundary (authoritative)**: ADR-MONO-013 § additive-note (2026-06-06) records that "`projects/global-account-platform/...` path references and the `gap` domain-slug in this ADR's historical body remain as historical record," and that two persisted-contract values are intentionally kept (`tenant_id='global-account-platform'` V0019; NextAuth provider id `gap` V0011/V0012). This task repairs only **clickable link hrefs** (so navigation resolves); it does NOT touch historical prose, inline-code path mentions, link labels, or persisted-contract strings. Therefore a post-fix `git grep "global-account-platform" docs/adr/` is **non-zero by design**.

# Goal

After this task, every markdown navigation link in `docs/adr/*.md` resolves to an existing file: `git grep -nE "\]\([^)]*global-account-platform" docs/adr/` returns **0**. No ADR decision, prose, label, or contract value changes.

# Scope

## In Scope (href-only, 8 files under `docs/adr/`)

Two mechanical substitutions inside markdown link hrefs `](…)` only:

1. **Directory rename** — `](../../projects/global-account-platform/` → `](../../projects/iam-platform/` across all 8 files (replace-all per file; the `](` prefix guarantees inline-code/prose `global-account-platform` mentions are NOT matched).
2. **Filename correction (piggy-backed)** — the two `…/docs/adr/ADR-002-admin-rbac.md` link targets (in ADR-MONO-019 L8, ADR-MONO-020 L11) point at a filename that does not exist even pre-rename; the current file is `ADR-002-admin-tenant-scope-sentinel.md`. Repoint both to the existing file.

Affected files (link count): ADR-MONO-001 (2), ADR-MONO-003 (2), ADR-MONO-012 (1), ADR-MONO-013 (2: Related L8 + additive-note L212), ADR-MONO-014 (3), ADR-MONO-019 (3), ADR-MONO-020 (3), ADR-MONO-021 (1) = 16 link hrefs (the 2 ADR-002 also get the filename fix).

Verified targets (all EXIST under `projects/iam-platform/`): `docs/adr/{ADR-001-oidc-adoption, ADR-002-admin-tenant-scope-sentinel, ADR-003-public-client-refresh-token-revoke-converter, ADR-004-oauth-callback-ci-linux-503-isolation, ADR-005-service-to-service-workload-identity}.md`, `docs/adr/` (dir), `PROJECT.md`, `specs/services/auth-service/architecture.md`.

## Out of Scope

- **Inline-code / prose mentions** of `global-account-platform` and the `gap` domain-slug (e.g. ADR-MONO-001 port tables, ADR-MONO-010/011/012 CI/Gradle-coord narrative, ADR-013/019/020/021 inline-code Java paths) — historical record, retained per MONO-179.
- **Link labels** — `[ADR-001 (GAP)]`, `[global-account-platform PROJECT.md]`, etc. stay verbatim (only the parenthesised href changes).
- Persisted-contract strings (`tenant_id='global-account-platform'`, provider id `gap`) — out of scope (MONO-179).
- consumer-project spec prose GAP→IAM alignment (separate, larger refactor-spec residue).

# Acceptance Criteria

- AC-1: `git grep -nE "\]\([^)]*global-account-platform" docs/adr/` returns **0** (was 11 matching lines / 16 link hrefs).
- AC-2: Each repointed href resolves — for every `](…/projects/iam-platform/…)` target introduced, the file/dir exists (`test -e`).
- AC-3: `git diff` touches only `docs/adr/*.md`; every changed line is an href substitution (dir-swap and/or the ADR-002 filename). No label, prose, heading, or inline-code change.
- AC-4: No new broken links introduced — `git grep -nE "\]\([^)]*ADR-002-admin-rbac" docs/adr/` returns 0.

# Related Specs

- None (ADR documents are decision records, not specs). Targets live under `projects/iam-platform/{docs/adr,specs,PROJECT.md}` — referenced, unchanged.

# Related Contracts

- None. (Persisted-contract `global-account-platform`/`gap` values explicitly retained, MONO-179.)

# Edge Cases

- **`](` guard** — restricting the substitution to the `](../../projects/global-account-platform/` token (not the bare string) is what keeps inline-code/prose historical mentions untouched; a naive string replace would corrupt the historical record.
- **ADR-002 filename drift** — the old `ADR-002-admin-rbac.md` href was already wrong (no such file); the dir-swap alone would leave it broken, hence the piggy-backed filename fix to `ADR-002-admin-tenant-scope-sentinel.md`.
- **Uniform `../../` depth** — all ADR files sit in `docs/adr/`, so every link uses `../../projects/…`; no per-file depth variance.

# Failure Scenarios

- **Over-reach (string replace instead of href replace)** → rewrites historical prose / persisted-contract strings → contradicts MONO-179 ACCEPTED. Prevented by the `](` anchored substitution + AC-3 diff review.
- **Repointing to a non-existent iam-platform path** → still broken. Prevented by AC-2 `test -e` on every introduced target (all pre-verified to exist).
