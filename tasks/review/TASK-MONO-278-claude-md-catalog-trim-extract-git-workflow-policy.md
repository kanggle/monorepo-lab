# TASK-MONO-278 — Trim CLAUDE.md back to catalog: extract Cross-Project git/worktree/branch forensics to platform/git-workflow-policy.md

Status: ready

## Goal

Restore `CLAUDE.md` to its self-declared role — "**Catalog + safety net** — full detail lives in the canonical files linked below" (line 3) — by relocating the incident-forensic prose that has accumulated in the **Cross-Project Changes** section into a new on-demand canonical file, leaving terse normative catalog entries + pointers in `CLAUDE.md`.

`CLAUDE.md` is loaded into **every** session's context. Its Cross-Project tail (lines ~186–213) has drifted from catalog into full worked-incident write-ups (symptom/recovery procedures, `TASK-MONO-241` subagent-dispatch incident, `BE-052`/`BE-161` branch-name incidents, stacked-PR hazard recovery steps). This forensic detail is **agent-normative** but **does not belong in the every-session file** — it should live in a `platform/` file that is read on-demand (Source-of-Truth Priority layer 5), exactly like `error-handling.md` / `hardstop-rules.md`.

Behavior-preserving: **no normative rule is changed or removed** — every rule currently stated in the Cross-Project tail must remain agent-accessible, either kept (terser) in `CLAUDE.md` or moved verbatim-in-substance to the new canonical file. This is a documentation reorganization, not a policy change (no ADR, no `## Overrides`, no HARDSTOP-04 conflict).

## Scope

In one atomic PR (shared-path / monorepo-level work):

1. **New file `platform/git-workflow-policy.md`** (agent source-of-truth, project-agnostic — no service names / API paths / domain entities). Receives the full normative + procedural content of:
   - Branch name constraint (`master` substring → push block; rename-around-noun / `ms-` abbrev / `git push -u origin HEAD` workaround).
   - Concurrent-session worktree isolation (isolation unit = directory not branch; main checkout parked; symptom/recovery; subagent absolute-path dispatch guard).
   - Post-merge branch hygiene (delete merged refs; squash-merge-stale definition; attempt-first-then-hand-to-user on actual classifier block; `git fetch --prune` before concluding; stacked-PR base-ref-deletion auto-close hazard + recovery).
   - `.claude/` self-modification classifier block; CI `dorny/paths-filter` negation constraint.
   - Condense worked-incident citations: keep monorepo-level (`MONO-*`) references; project-specific incident IDs may be dropped or left to the already-linked personal-memory pointers (memory is per-host, not shared — the normative procedure itself must be fully self-contained in this shared file).

2. **`CLAUDE.md` Cross-Project Changes section** — replace the extracted prose (lines ~186–213) with one terse catalog bullet per rule + a single pointer to `platform/git-workflow-policy.md`. Keep the atomic-PR / Conventional-Commit-scopes content (lines 168–184) as-is.

3. **`CLAUDE.md` Task Rules line 107** — keep the 3-dimension merge-verification rule verbatim; remove only the trailing forensic incident parenthetical (`(TASK-PC-BE-002 회귀 회복 saga 2026-05-20 — PR #672 …)`); relocate that citation into `platform/git-workflow-policy.md` (or drop, since the rule is self-justifying).

4. **Source-of-Truth wiring** — add `platform/git-workflow-policy.md` to `platform/README.md` index (if `platform/` indexes its files there) so it is discoverable; confirm it slots under Source-of-Truth Priority layer 5 (`platform/` remaining files) without a CLAUDE.md priority-list edit.

Out of scope: the `# Recommending Tasks and Dispatching Agents` section (226–249) — deferred pending a separate decision on shared-vs-personal placement. No `rules/`, no `.claude/` edits (the latter is classifier-blocked anyway).

## Acceptance Criteria

- `CLAUDE.md` line count drops from 249 to ~205–210; the Cross-Project tail is terse catalog + a `platform/git-workflow-policy.md` pointer.
- **Zero normative loss**: every rule in the current Cross-Project tail (branch-name `master` block, worktree isolation, subagent absolute-path dispatch, post-merge ref deletion, prune-before-conclude, stacked-PR base-deletion hazard, `.claude/` block, CI path-filter negation ban) is present and agent-readable either in `CLAUDE.md` (terse) or `platform/git-workflow-policy.md` (full). A reviewer can map each old rule to its new home.
- `platform/git-workflow-policy.md` is project-agnostic (passes the shared-vs-project boundary; no service names / API paths / domain entities) — HARDSTOP-03 clean.
- Task Rules 3-dim merge-verification rule (line 107) is unchanged in substance; only the incident parenthetical moved/removed.
- All intra-doc links resolve (`platform/git-workflow-policy.md` reachable from CLAUDE.md; existing memory pointers preserved where still useful).
- `git mv`/edit re-stage discipline observed; no `Status: ready` blob lands under `done/`.

## Related Specs

- `CLAUDE.md` (the file trimmed) — §Cross-Project Changes, §Task Rules line 107.
- `platform/README.md` (platform file index, if present) — add new entry.
- `platform/repository-structure.md`, `platform/hardstop-rules.md` — adjacency check (no overlap/duplication with the new file).
- Source-of-Truth Priority (CLAUDE.md §75–96) — layer 5 already admits the new `platform/` file; verify no priority edit needed.

## Related Contracts

- None (doc-only; no API/event contract touched).

## Edge Cases

- A rule that is *both* normative and host-specific (e.g. classifier `.claude/` block, Windows worktree pitfalls) — keep the normative kernel in the shared file; host-forensics may point to personal memory but the shared file must stand alone for a fresh clone.
- `platform/` project-agnostic constraint vs. monorepo-level task-ID citations (`MONO-241`) — `MONO-*` are monorepo-level (not project-specific), acceptable; avoid project task IDs / service names in the shared file.
- A future reader landing in `platform/git-workflow-policy.md` cold — it must be self-contained (don't assume CLAUDE.md context).

## Failure Scenarios

- **Silent rule loss** — trimming a rule out of CLAUDE.md without landing it in the new file. Mitigated by the per-rule mapping AC; reviewer must diff old-tail rules against the two new homes.
- **Over-extraction** — moving terse catalog rules that belong in the every-session file (e.g. the one-line `master` branch ban is cheap and high-frequency → keep a one-liner in CLAUDE.md, full workaround list in platform). Keep the safety-net one-liners; move only the multi-paragraph forensics.
- **platform/ pollution** — leaking project-specific content into the shared file → HARDSTOP-03. Mitigated by the project-agnostic AC.
- **Reintroducing append drift** — future incidents re-inlined into CLAUDE.md. Note in the new file's header that worked-incidents belong there (or in memory), not in CLAUDE.md.

## Notes

Surfaced by a CLAUDE.md rule-optimization audit (2026-06-16) following the memory-optimization pass the same day. Companion analysis: per-session context cost is driven by CLAUDE.md (21.9 KB, every session) and the MEMORY.md index (already compressed 29.4→23.3 KB this session) — the Cross-Project forensic detail was being paid for in **both**, while the personal-memory copy is not shared with a fresh repo clone. The `# Recommending Tasks and Dispatching Agents` section is a separate, deferred `[?]` decision (shared CLAUDE.md vs personal memory) — not included here.
