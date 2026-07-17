# TASK-MONO-425 — the two rules that decide whether a hook runs at all live only in private memory; the hook-authoring guide never mentions them

- **Type**: TASK-MONO (monorepo-level — shared path `.claude/hooks/`)
- **Status**: done
- **Target**: `.claude/hooks/README.md` § Adding a new detector
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (the README edit is user-applied — `.claude/hooks/` is classifier-blocked for the agent)

## Goal

The 2026-07-17 memory audit (TASK-MONO-423) found six repo-wide rules living only in one operator's private
AI memory. Four were promoted (MONO-423) and one contradiction was corrected (MONO-424). The remaining two
both govern `.claude/hooks/` itself and so had to be handed to a human to apply — this ticket records that
handoff and its verification.

Both rules decide **whether a hook runs at all**, and both have already caused a **silent, zero-signal
failure of main-branch protection**. The hook-authoring guide (`README.md § Adding a new detector`) never
mentions either, so the next hook author re-learns them the hard way — by shipping a hook that does nothing.

## AC-0 — 착수 시 재측정 (audit-born; the audit is a hypothesis, not a source)

Measured 2026-07-17. Re-verify before/after: the README § Adding a new detector originally had a single
step 6 (matcher registration) and **no mention of encoding or of live-probing the wiring**. Confirm the
applied edit still matches the two rules below and did not disturb the surrounding steps.

## AC-1 — encoding: a new hook `.ps1` must be UTF-8 **with BOM**, and repo reads must be `-Encoding UTF8`

Windows PowerShell 5.1 — the shell these hooks actually run in — parses a BOM-less `.ps1` under the **host's
ANSI code page**. On a host whose code page differs from the authoring host, the script fails to parse and
**the hook silently does not run**: no error, no output, no signal. A main-protection hook that fails this
way is simply off, and nothing reports it. The same applies to reads — `Get-Content` without `-Encoding
UTF8` also defaults to ANSI, so a hook can silently mis-read the very spec it enforces and stop matching. A
green local run proves nothing here; it is a byproduct of *your* code page.

## AC-2 — wiring: matcher coverage cannot be fixture-tested; prove it with a live probe

A hook is live only on the tool matchers it is registered for. **A fixture pipes a payload straight to the
script**, so it passes whether or not the harness would ever invoke the hook on a real tool call — fixtures
prove the hook's *logic*, never its *wiring*. Three main-protection hooks were registered `matcher: "Bash"`
only; the same commands issued through the PowerShell tool bypassed them entirely — the hooks were never
called. Wiring must be proven with a **live two-tool probe in a new session** (matcher registration is read
at session start), issuing the same triggering command through each tool that can reach it and confirming the
block fires from both. When widening a matcher, add the corresponding block rather than assuming the existing
logic generalises.

> Fixtures prove the hook's *logic*. Only a live probe proves its *wiring*. A hook that is never invoked
> passes every fixture you have.

## Scope

- **In**: add the two rules to `.claude/hooks/README.md § Adding a new detector` (former single step 6 →
  step 6 encoding + step 7 wiring/probe + the pull-quote).
- **Out**: no hook `.ps1` is changed; no fixture is added (this is guide text, not a detector). No CI guard —
  neither rule is mechanically detectable at author time (the encoding failure is host-code-page-dependent;
  the wiring gap is invisible to fixtures by construction — that is the whole point).

## Acceptance Criteria

- **AC-0..AC-2**: as above.
- **AC-3**: the edit is **applied by the user** — `.claude/hooks/` is classifier-blocked for the agent even
  with explicit approval (measured; see `platform/git-workflow-policy.md` § `.claude/` Self-Modification,
  corrected in MONO-424). The agent scaffolds the task, branch, and PR around the user-applied edit and hands
  over the commit.
- **AC-4**: docs-only; `changes` gate green, code lanes SKIPPED.
- **AC-5**: the two source memories (`env_winps_ansi_codepage_silently_disables_hooks`,
  `project_guard_wiring_not_just_logic`) are retargeted to point at this canonical home, not deleted — repo
  owns the rule, memory keeps the worked incident (MONO-405 / MONO-402).

## Related Specs / Contracts

- `.claude/hooks/README.md` § Adding a new detector (target)
- `platform/git-workflow-policy.md` § `.claude/` Self-Modification (why AC-3's handoff is real)
- `tasks/INDEX.md` § done → `TASK-MONO-423` (the audit that surfaced this), `TASK-MONO-405` (encoding
  incident), `TASK-MONO-402` (wiring incident)

## Edge Cases / Failure Scenarios

- **This README itself must stay readable across code pages.** The rule is about `.ps1` hook scripts, not
  about this Markdown file; do not conflate the two in the wording.
- **The wiring rule is not "add more fixtures".** Adding fixtures cannot close it — a fixture that pipes to
  the script passes regardless of matcher registration. The rule's value is the *live probe*, and if that is
  softened to "test it well" the guide stops meaning anything.
- **AC-3 is not ceremony.** The agent genuinely cannot apply this edit; a shell-write around the block is
  explicitly forbidden. The handoff is the mechanism, not a formality.
