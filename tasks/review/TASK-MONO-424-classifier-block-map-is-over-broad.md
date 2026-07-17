# TASK-MONO-424 — the `.claude/` classifier-block note is over-broad; the repo already recorded the measurement that falsifies it, in a task note nobody reads

- **Type**: TASK-MONO (monorepo-level — shared paths `CLAUDE.md`, `platform/`)
- **Status**: review
- **Target**: `CLAUDE.md` § Cross-Project Changes (L191), `platform/git-workflow-policy.md` § `.claude/` Self-Modification Is Classifier-Blocked
- **Analysis model**: Opus 4.8 · **Impl model**: Sonnet (two-statement doc correction, non-code)

## Goal

Two committed repo statements say the auto-mode classifier hard-blocks agent edits/commits under
`.claude/hooks|agents|commands`. **The `agents` and `commands` halves are false**, and the repo's own
`tasks/INDEX.md` already says so — citing the PR that proves it. Correct both statements to the measured
scope (`hooks/` + `settings.json`), and point them at the evidence.

## AC-0 — 착수 시 재측정 (audit-born; the audit is a hypothesis, not a source)

Measured 2026-07-17. **Re-verify before editing**: read `tasks/INDEX.md`'s MONO-409 DONE note and confirm it
still records the falsification, and confirm both target statements still assert the over-broad scope. If an
intervening change already corrected either → drop that half and note it. **The code and the repo win over
this ticket's prose.**

## AC-1 — the finding: the repo contradicts itself, and the losing side is the one everybody reads

| Statement | Says | Evidence cited |
|---|---|---|
| `CLAUDE.md:191` | `.claude/hooks\|agents\|commands` edits/commits "are hard-blocked even with approval → hand the patch to the user" | **none** |
| `platform/git-workflow-policy.md` § `.claude/` Self-Modification | same three directories, "even with explicit user approval" | **none** |
| `tasks/INDEX.md` § done → `TASK-MONO-409` note | *"실측 오류 … `.claude/{agents,commands}` 는 Edit·git-commit·push 모두 통과(#2616 이 직접 랜딩). 실제 차단은 `hooks/`·`settings.json` 만 … CLAUDE.md 의 문구가 과도."* | **PR #2616** — which landed `.claude/agents/` + `.claude/commands/` edits, and is in `origin/main` |

`TASK-MONO-396` (PR #2525) is a second instance for `commands/`.

- **The measured scope is**: `hooks/` and `settings.json` are hard-blocked (intent-resistant — even an
  explicit user instruction does not clear them); `commands/`, `agents/`, `config/` pass; `skills/` passes
  with explicit per-action authorization.
- **This is not private-memory-vs-canon.** Both sides are committed repo artifacts. The side citing a
  measurement beats the side asserting without one.

## AC-2 — correct `CLAUDE.md:191`

Narrow the blocked set to `.claude/hooks/` + `.claude/settings.json`. Keep the hand-off instruction **for
that set only** (it is real, and intent-resistant — the classifier refuses even on an explicit user
instruction, so hand-off is genuinely the only path). Keep `platform/` explicitly not subject.

## AC-3 — correct `platform/git-workflow-policy.md` § `.claude/` Self-Modification

Same correction. Retitle the section so it does not assert the over-broad claim in its own heading. State the
measured map, cite MONO-409 (#2616) / MONO-396 (#2525) as the evidence, and keep the existing "do not attempt
a shell-write bypass" rule — that rule is about **not dodging a real block**, and survives the narrowing
unchanged.

## AC-4 — say that the map is an observation, not a guarantee

The classifier's policy is external to this repo and **can change silently**. Both corrected statements must
say: *do not pre-emptively hand off on assumption — attempt the edit once; if it is actually blocked, hand it
over then.* The cost asymmetry is the argument: a wasted attempt costs one round-trip, while a wrong
assumption costs a needless human hand-off **and files a false completion note in the ticket** (which is
exactly how the bad line propagated — MONO-409's own "필독" preamble repeated it).

## Scope

- **In**: the two statements above.
- **Out**:
  - `.claude/hooks/README.md` — two further promotions from the same audit (new hooks must be UTF-8 **BOM**
    or they silently no-op; hook **wiring**/matcher coverage needs a live two-tool probe). Genuinely
    classifier-blocked (`hooks/`) → patch handed to a human. Separate follow-up.
  - The agent's private memory map — already reconciled outside the repo; this ticket does not depend on it.
  - **No live probe is needed or in scope.** The adjudication is a repo fact, not a new measurement.

## Acceptance Criteria

- **AC-0**: re-verified at implementation time.
- **AC-1..AC-4**: as above.
- **AC-5**: both corrected statements agree with each other **and** with the MONO-409 DONE note. Grep for any
  third copy of the over-broad claim before finishing — this ticket exists because the claim was in two
  places at once, so assume it may be in three.
- **AC-6**: docs-only; `changes` gate green, code lanes SKIPPED.

## Related Specs / Contracts

- `tasks/INDEX.md` § done → `TASK-MONO-409` (the falsifying record) and `TASK-MONO-423` (the audit that
  surfaced this)
- `platform/git-workflow-policy.md` § `.claude/` Self-Modification (target)

## Edge Cases / Failure Scenarios

- **Do not widen the correction into "the classifier is permissive".** `hooks/` + `settings.json` really are
  intent-resistant, and that is the half worth keeping loud. An over-corrected note is the same defect
  mirrored.
- **Do not cite git history as the proof.** Squash-merge rewrites authorship to the PR author, so a
  `.claude/agents/` commit existing is equally consistent with "a human applied the patch". The
  `Co-Authored-By: Claude` trailer does not settle it either (an agent that only drafted the message would
  leave the same trailer). **The evidence is the MONO-409 note's prose**, which records what was attempted
  and what passed. Cite that.
- **The root cause is retrieval, not truth.** The measurement was written down — into a task DONE note, a
  place no one greps when asking "am I allowed to edit this?". Correcting the two rule statements fixes the
  answer; it does not fix the habit of recording measurements where the rule doesn't live. Worth noting in
  the DONE note rather than silently leaving it.
