# `.claude/hooks/`

PowerShell hooks invoked by Claude Code's harness on tool-call events. Each hook reads JSON from stdin (the tool invocation payload) and emits JSON to stdout to influence the call — typically `{ "decision": "block" | "ask", "reason": "<…>" }` to surface a message back to the agent's next turn.

Hook configuration lives in [`.claude/settings.json`](../settings.json). The list below mirrors that registration plus implementation references.

---

# Inventory

| Hook | Matcher(s) | Decision verbs | Purpose |
|---|---|---|---|
| [`hardstop-detect.ps1`](hardstop-detect.ps1) | `PreToolUse[Edit]`, `PreToolUse[Write]` | `block` | Auto-detect 5 mechanically-detectable Hard Stop triggers (HARDSTOP-01 / -03 / -05 / -09 / -10) and emit the canonical 4-block remediation stanza so the next turn carries replayable corrective steps. Runs FIRST in the Edit/Write hook chain so its `block` short-circuits before the lighter `ask` warnings. |
| [`spec-check.ps1`](spec-check.ps1) | `PreToolUse[Edit]`, `PreToolUse[Write]` | `ask` | Surface confirmation prompts for edits to `specs/contracts/` (contract-first reminder, `SPEC-CHECK-01`) and `platform/` (highest-priority source of truth reminder, `SPEC-CHECK-02`). |
| [`rule-consistency-check.ps1`](rule-consistency-check.ps1) | `PreToolUse[Edit]`, `PreToolUse[Write]` | `block` | Verify skill/agent/command files carry required frontmatter and that referenced spec paths exist (`RULE-CONSISTENCY-01..04`). |
| [`protect-main-branch.ps1`](protect-main-branch.ps1) | `PreToolUse[Bash]` | `block` | Block direct `git push` / force push / hard reset to `main`/`master`, **and any implicit-target `git push` (bare / `origin` / `origin HEAD` / `HEAD`) when the cwd's `git symbolic-ref --short HEAD` is `main`/`master`** (TASK-MONO-135 regression guard — closes the 2026-05-25 fan-platform worktree-HEAD-on-main leak). Allowlists: `portfolio-sync` derivation workdirs, `project-template` extraction workdirs. |
| [`warn-shared-checkout-switch.ps1`](warn-shared-checkout-switch.ps1) | `PreToolUse[Bash]` | `ask` | Surface a confirmation when a HEAD-moving `git checkout`/`git switch` runs in the MAIN checkout (`git-dir == git-common-dir`) with a **dirty** working tree — the concurrent-session shared-checkout hazard (CLAUDE.md § Concurrent-session worktree isolation; 2026-06-13 pc-fe-070 × increment-C incident; TASK-MONO-236). Dirty-gate suppresses clean-start `checkout -b`; linked worktrees / clean tree / no-op same-branch switch / `git -C` redirects silently allowed. |
| [`verify-worktree-isolation.ps1`](verify-worktree-isolation.ps1) | `PreToolUse[Edit]`, `PreToolUse[Write]` | `block` | Block Edit/Write whose `tool_input.file_path` resolves outside the cwd worktree's toplevel when cwd is a *linked* git worktree (Agent `isolation: "worktree"` dispatch). Detects linked worktree via `git-dir != git-common-dir`; main worktree / non-git cwd / detached HEAD / relative file_path / empty inputs silently allowed. TASK-MONO-136 regression guard — closes the 2026-05-25 PC-BE-005 dispatch leak (~2.8% rate, 4 leaks / 144 tool_uses). |
| [`notify.ps1`](notify.ps1) | `PreToolUse[AskUserQuestion]`, `PermissionRequest` | — (notification only) | Surface a desktop notification when user confirmation is needed. |
| [`format-check.ps1`](format-check.ps1) | `PostToolUse[Edit]`, `PostToolUse[Write]` | — (async) | Post-edit format / lint check. |
| [`test-on-edit.ps1`](test-on-edit.ps1) | `PostToolUse[Edit]`, `PostToolUse[Write]` | — (async) | Run unit tests touching the edited file. |
| [`task-completed.ps1`](task-completed.ps1) | `Stop` | — (notification only) | End-of-task notification. |

---

# Scheduled routines (gap #2)

Recurring **doc-gardening** agents that fire weekly via the harness's `/schedule` skill (NOT via `.claude/hooks/`). They detect documentation drift on a fixed cadence — the asynchronous complement to the synchronous `PreToolUse` detectors above.

| Routine | Skill | Schedule | Output |
|---|---|---|---|
| `monorepo-lab-validate-rules-weekly` | `validate-rules` (user-level plugin) | Mon 09:00 KST | Draft PR `chore(rules): weekly validate-rules audit (<date>)` with `RULE-CONSISTENCY-05+` stanzas, or no-op |
| `monorepo-lab-audit-memory-weekly` | `audit-memory` (user-level plugin) | Mon 09:30 KST | Memory file `audit_findings_<date>.md` with `MEMORY-AUDIT-NN` stanzas, or no-op |

Routine config (prompt bodies, registration procedure, failure modes) lives in [`../workflows/doc-gardening.md`](../workflows/doc-gardening.md). The harness keeps the registration state — re-create from that document if routines are deleted via UI.

---

# Hook output format

All rule-violation emissions across `hardstop-detect.ps1` / `spec-check.ps1` / `rule-consistency-check.ps1` follow the **4-block remediation message standard** defined in [`../../platform/lint-remediation-message-standard.md`](../../platform/lint-remediation-message-standard.md):

```
[VIOLATION] <rule_id>: <one-line condition> at <file>:<line | section>
[WHY] <invariant the rule protects>
[REMEDIATION] Choose one:
  1. <concrete action>
  2. <alternative action>
  3. <escalation: open ADR / file ready/ task>
[REFERENCE] <rule-file-path> § <section-anchor>
```

Rule IDs in use:

- `HARDSTOP-NN` (NN = 01–10) — canonical 4-block body lives in [`../../platform/hardstop-rules.md`](../../platform/hardstop-rules.md); [`../../CLAUDE.md § Hard Stop Rules`](../../CLAUDE.md#hard-stop-rules) carries the catalog with click-through links. The hook injects only file path / line number; the `[WHY]` / `[REMEDIATION]` / `[REFERENCE]` blocks match the canonical platform stanza verbatim (single source of truth).
- `SPEC-CHECK-NN` — emitted by `spec-check.ps1` (currently 01 contract-edit, 02 platform-edit).
- `RULE-CONSISTENCY-NN` — synchronous PreToolUse hook reserves 01–04 (skill-missing-spec, agent-missing-fields, command-missing-frontmatter, broken-spec-ref). Asynchronous scheduled routine (`monorepo-lab-validate-rules-weekly`) emits 05+ for structural multi-file drift the hook can't detect synchronously.
- `MEMORY-AUDIT-NN` — emitted by `monorepo-lab-audit-memory-weekly` scheduled routine (01 stale, 02 contradiction, 03 dangling ref, 04 CLAUDE.md duplicate).

`protect-main-branch.ps1`, `verify-worktree-isolation.ps1`, and `warn-shared-checkout-switch.ps1` are intentionally exempt — their messages are safety-rail enforcements (Bash- and Edit/Write-tool guards), not rule-surface violations, so the standard does not apply.

---

# Adding a new detector

1. Add the detection logic to the appropriate hook script (`hardstop-detect.ps1` for new HARDSTOP triggers; `rule-consistency-check.ps1` for new soft warnings; new hook file for orthogonal concerns).
2. Choose a rule ID — `HARDSTOP-NN` (extends the CLAUDE.md sequence) or `<source-shortname>-NN`.
3. Author the stanza body — `[WHY]` / `[REMEDIATION]` / `[REFERENCE]` blocks. For HARDSTOP triggers, the body MUST match the corresponding `platform/hardstop-rules.md` stanza verbatim (and the catalog entry in `CLAUDE.md § Hard Stop Rules` must be added/updated in the same PR).
4. Add a fixture under [`__tests__/`](__tests__/) — one PASS case per emission, plus negative cases for false-positive prevention where applicable.
5. Update this `README.md` inventory + `../../platform/lint-remediation-message-standard.md` § Change protocol (if the change affects the format).
6. **Save the `.ps1` as UTF-8 *with BOM*, and read repo files with `-Encoding UTF8`.** Windows PowerShell 5.1 — the shell these hooks actually run in — parses a BOM-less `.ps1` using the *host's ANSI code page*. On a host whose code page differs from the authoring host, the script fails to parse and **the hook silently does not run**: no error, no output, no signal. A main-protection hook that fails this way is simply off, and nothing tells you. The same applies to reads: `Get-Content` without `-Encoding UTF8` also defaults to ANSI, so a hook can silently mis-read the very spec it is enforcing and stop matching. A green local run proves nothing here — it is a byproduct of *your* code page.
7. **If a new hook script, register it under the appropriate matcher in `../settings.json` — then prove the wiring with a live probe.** A hook is live only on the tool matchers it is registered for, and **matcher coverage cannot be fixture-tested**: a fixture pipes a payload straight to the script, so it passes whether or not the harness would ever invoke it. Three main-protection hooks were registered `matcher: "Bash"` only; the same commands issued through the PowerShell tool bypassed them entirely — the hooks were never called. Probe it live, in a **new session** (matcher registration is read at session start), by issuing the same triggering command through **each** tool that can reach it and confirming the block fires from both. When widening a matcher, add the corresponding block rather than assuming the existing logic generalises.

   > Fixtures prove the hook's *logic*. Only a live probe proves its *wiring*. A hook that is never invoked passes every fixture you have.

---

# Running the diagnostic fixtures

```bash
pwsh .claude/hooks/__tests__/run-all.ps1
```

Each fixture is self-contained — synthesizes a tool-call JSON payload, pipes it to the target hook, and asserts the JSON output shape (decision + stanza ID + 4-block presence). All fixtures must pass after any hook edit. CI integration is deferred (Phase 3 follow-up).

---

# Provenance

- The 4-block format alignment is part of OpenAI Harness gap A delivery — [`platform/lint-remediation-message-standard.md`](../../platform/lint-remediation-message-standard.md) (Phase 1+2, TASK-MONO-059, PR #383).
- `hardstop-detect.ps1` is the Phase 3 closure — auto-injects the formatted message into the agent's next turn via the harness's existing PreToolUse `decision/reason` plumbing (TASK-MONO-060, ADR-MONO-006 § 6 outstanding #1).
