# TASK-MONO-459 — docs/adr: correct broken observability + console citation slugs (006→007, stale 013/018 filenames)

Status: done

`(분석=Opus 4.8 / 구현=Opus 4.8 — 기계적 링크 슬러그 교정, 결정 본문 무변경)`

---

## Goal

Close a docs-citation drift found in the 2026-07-21 reconciliation audit and re-measured against `main` (`dd93fc420`): four cross-ADR markdown links point at **non-existent files** (dead slugs). Two conflate the observability-stack ADR with the lint-remediation ADR (`ADR-MONO-006` ≠ observability); two use stale pre-rename console/federation filenames. All are pure file-path link fixes — **no ADR decision body (D1–D8) is touched** (HARDSTOP-04 preserved).

## Re-measured evidence (line numbers verified, not inherited)

The correct targets exist: `ADR-MONO-007-worktree-ephemeral-observability-stack.md` (the real Vector + VictoriaMetrics ADR), `ADR-MONO-013-platform-console-foundation.md`, `ADR-MONO-018-platform-console-phase-8-federation-hardening.md`. `ADR-MONO-006` is actually `ADR-MONO-006-lint-remediation-as-agent-context.md` (unrelated topic).

Fixed (4 links / 3 files):

1. `docs/adr/ADR-MONO-017-platform-console-bff-architecture.md:8` — **Related** list: `[ADR-MONO-006](ADR-MONO-006-observability-stack.md)` → `[ADR-MONO-007](ADR-MONO-007-worktree-ephemeral-observability-stack.md)` (D7 observability reuse base).
2. `docs/adr/ADR-MONO-018-platform-console-phase-8-federation-hardening.md:8` — **Related** list: same 006→007 dead-link correction (D4 federation reuse base).
3. `docs/adr/ADR-MONO-018-…:135` — §3.2 "does NOT modify" consequences fence: 006→007 (link + the adjacent `ADR-006 § History` token).
4. `docs/adr/ADR-MONO-041-container-image-build-standard.md:13` — **Family** field: `ADR-MONO-013-unified-platform-console.md` → `ADR-MONO-013-platform-console-foundation.md` and `ADR-MONO-018-federation-e2e-harness.md` → `ADR-MONO-018-platform-console-phase-8-federation-hardening.md` (both pre-rename stale slugs).

## Deliberately NOT changed (scope discipline)

- **`ADR-MONO-018-…:81`** — the same dead `[ADR-MONO-006](…)` link appears INSIDE the §2 **D4 decision option table** (option A, CHOSEN). That is a D1–D8 decision body, and the ADR's own **line-10 additive note (2026-05-28, ADR-MONO-007a)** already documents this exact reference as the "known broken reference per TASK-MONO-137 meta" and asserts "D1–D8 decision bodies are byte-unchanged (HARDSTOP-04)". Rewriting it would violate the ADR's stated immutability discipline; the in-document correction is the additive note. Left as-is by design.
- **Closed task records** (immutable history, edit-blocked): the same dead 006 slug survives in `projects/platform-console/tasks/done/TASK-PC-BE-001-console-bff-skeleton.md:146`, `…/TASK-PC-FE-011-mvp-operator-overview.md:181`, `tasks/done/TASK-MONO-137-…:97`, and the `tasks/INDEX.md` MONO-137 done-entry prose. These are historical records of already-merged work (and `done/` in-place edits are classifier-blocked); they are not corrected. `TASK-MONO-181` (spec-dead-reference-batch) already fixed the live `console-integration-contract.md:1159` occurrence.

## Scope

**In:** the 4 link edits above (mechanical slug/path corrections, `docs/adr/` only).
**Out:** any ADR decision-body edit (L81), any `done/` history edit, any content/decision change, any anchor/heading fragment change (all four are plain file links, no `#anchor`).

## Acceptance Criteria

- **AC-0 (re-measure):** confirm on `main` that the 4 cited links resolve to non-existent files and the corrected targets exist (`ls docs/adr/`). Line numbers are a 2026-07-21 observation — re-verify.
- **AC-1:** the 4 links point to existing files; `git grep -E 'ADR-MONO-006-observability-stack\.md|ADR-MONO-013-unified-platform-console\.md|ADR-MONO-018-federation-e2e-harness\.md'` returns only the intentionally-preserved decision-body line (ADR-018:81) and immutable `done/`/INDEX history — no live spec/current-ADR-citation hit.
- **AC-2:** no ADR D1–D8 decision option table is modified (verify `git diff` touches only Related/Family/Consequences metadata lines, never a §2 option row).
- **AC-3:** the observability link text reads `ADR-MONO-007` (not just a repointed path under the old `006` label) so the citation is self-consistent.

## Related Specs

- `docs/adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md` (the correct observability ADR).
- `docs/adr/ADR-MONO-006-lint-remediation-as-agent-context.md` (the ADR that actually owns the 006 slug).
- `platform/architecture-decision-rule.md` — HARDSTOP-04 decision-body immutability (why L81 is preserved).

## Related Contracts

- None (documentation-only).

## Edge Cases

- ADR-018 carries THREE 006 references (L8, L81, L135), not one — L8/L135 are citations/consequences (fixed), L81 is a decision body (preserved). A blanket find-replace would have wrongly rewritten the decision body.
- The `ADR-006 § History` prose token on L135 is corrected alongside the link so the sentence is internally consistent; the `ADR-007` target owns no such History-note obligation either, so the "does NOT modify" statement stays true.

## Failure Scenarios

- **Blanket 006→007 replace touches L81:** silently rewrites a byte-frozen ACCEPTED decision body, violating HARDSTOP-04 and contradicting the ADR's own line-10 note. AC-2 guards this.
- **Path fixed but link text left as `ADR-MONO-006`:** a reader sees "ADR-MONO-006" labelling the observability ADR — the citation still misleads. AC-3 requires the label to read 007.
- **"Fixing" done/ history:** editing closed task records rewrites the audit trail (and is classifier-blocked); the drift there is intentional historical record, not a live defect.
