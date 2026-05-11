# Task ID

TASK-MONO-060

# Title

Phase 3 — auto-inject Hard Stop remediation message via `.claude/hooks/` (OpenAI Harness gap A closure)

# Status

done

# Owner

monorepo

# Task Tags

- impl
- hooks
- rules
- harness

---

# Goal

Land the **agent-context injection** half of OpenAI Harness gap A — convert the 4-block remediation message standard from "format consistency when someone follows it" (Phase 1+2, TASK-MONO-059) into **active next-turn context** that the agent receives automatically whenever a mechanically-detectable Hard Stop or rule-violation trigger fires.

OpenAI Harness Engineering's reported mechanism (memory `reference_openai_harness_engineering.md` § "강제 메커니즘 핵심 3가지" #2) is *not* the lint format itself — it is the **injection**: a custom lint message that lands in the agent's prompt on its next turn, so the violation becomes input-grade context, not output-grade prose. Phase 1+2 delivered the format; this task delivers the injection.

The mechanism is already present in `monorepo-lab` — `.claude/hooks/spec-check.ps1` and `rule-consistency-check.ps1` already implement the PreToolUse pattern of emitting `{decision: "block" | "ask", reason: "..."}` JSON, which the harness surfaces back to the agent as next-turn context. What is missing is (a) a dedicated **Hard Stop detector** hook, and (b) **format alignment** so every emission carries the 4-block stanza defined in `platform/lint-remediation-message-standard.md`.

ADR-MONO-006 § 2.4 / § 6 outstanding #1 is the provenance for this task. Phase 1+2 ROI ≈ 50% of gap-A closure; Phase 3 (this task) closes the remaining ≈ 50%.

---

# Scope

## In Scope

### A. New `.claude/hooks/hardstop-detect.ps1`

A PreToolUse hook on `Edit` + `Write` matchers that performs mechanical Hard Stop detection on the file path / file content being edited. When a trigger fires, the hook emits `{decision: "block", reason: "<4-block stanza>"}` so the agent's next turn carries the standardised remediation message.

Mechanically-detectable triggers (Phase 3a — this task):

| Trigger | Detection signal | Detection cost |
|---|---|---|
| `HARDSTOP-01` (no `PROJECT.md`) | Walk up from `tool_input.file_path` checking for `PROJECT.md` at each level until hitting repo root | low (filesystem walk) |
| `HARDSTOP-03` (shared lib has project-specific content) | Edit target under `platform/` / `rules/` / `.claude/` / `libs/` / `tasks/templates/` / `docs/guides/`, AND new_string contains one of: known project names (`wms`, `ecommerce`, `gap`, `fan-platform`, `scm`), known service names (compiled from `projects/*/specs/services/*/`), known API path pattern (`/api/<service>/`) | medium (project/service name list cached on first run) |
| `HARDSTOP-05` (task not in ready/) | Edit target under `tasks/in-progress/` or `tasks/review/` (these are frozen per `tasks/INDEX.md` § Move Rules and `projects/<name>/tasks/INDEX.md`) | low (path prefix check) |
| `HARDSTOP-09` (architecture undeclared) | Edit target under `projects/<name>/apps/<service>/src/main/` AND `projects/<name>/specs/services/<service>/architecture.md` does not exist | low (filesystem check) |
| `HARDSTOP-10` (Service Type missing) | Edit target is `projects/<name>/specs/services/<service>/architecture.md` AND new_string / file content lacks a `Service Type` declaration matching one of the entries in `platform/service-types/INDEX.md` | medium (architecture.md parse + INDEX lookup) |

Each trigger MUST emit the **4-block stanza** verbatim per `platform/lint-remediation-message-standard.md` — same `[VIOLATION] / [WHY] / [REMEDIATION] / [REFERENCE]` form documented in `CLAUDE.md` HARDSTOP-NN stanzas. The hook may include the file path + line number as context, but the stanza body comes from the CLAUDE.md authored stanza (single source of truth — drift between hook output and the canonical stanza is the failure mode this task explicitly guards against).

### B. Format alignment for existing hooks

Two existing hooks emit prose-form reasons that should align with the standard:

- `.claude/hooks/spec-check.ps1` — currently emits `"Contract files (specs/contracts/) must be updated through the design-api or design-event workflow. Verify this change follows the contract-first rule."` Reword to a 4-block stanza using a new `<source-shortname>-NN` ID (e.g. `SPEC-CHECK-01`). Same for the platform/-edit warning.
- `.claude/hooks/rule-consistency-check.ps1` — currently emits `"Rule consistency warnings for <type>:\n- <list>\nFix these issues or confirm they are intentional."` Reword each warning class as its own stanza.

Out: `protect-main-branch.ps1` — its emission ("main/master branch protection: ...") is a Bash-tool guard, not a rule-violation in the sense the standard targets. Leave as-is. The standard's scope is rule-surface violations, not safety-rail enforcement.

### C. settings.json wiring

Register `hardstop-detect.ps1` under `hooks.PreToolUse[matcher=Edit]` and `hooks.PreToolUse[matcher=Write]` lists (alongside the existing `spec-check.ps1` + `rule-consistency-check.ps1` entries). Ordering: `hardstop-detect.ps1` runs FIRST (most authoritative), so its `block` decision short-circuits before the lighter warnings fire.

### D. Diagnostic test fixtures

Under a new `.claude/hooks/__tests__/` directory (or comparable location), add PowerShell `.ps1` scripts that feed canned `tool_input` JSON to the hook via stdin and assert the JSON-structured output matches expected 4-block stanza. One fixture per trigger detector — 5 fixtures total. These are diagnostic scripts (developer-run via `pwsh .claude/hooks/__tests__/run-all.ps1`), not CI gates. CI integration is deferred.

### E. Documentation

- `.claude/hooks/README.md` (if exists; create if not) — entry for `hardstop-detect.ps1` describing matchers, triggers, emission format, ordering with sibling hooks.
- `platform/lint-remediation-message-standard.md § Forward look — Phase 3 hook automation` — flip from "deferred to TASK-MONO-060" to "delivered (link to this task)". Same closure annotation in ADR-MONO-006 § 6 outstanding #1.

## Out of Scope

- **Semantic Hard Stop detection** — HARDSTOP-02 (PROJECT.md unparseable / unknown taxonomy — requires taxonomy parser), HARDSTOP-04 (domain/trait conflict without Overrides — semantic), HARDSTOP-06 (specs missing/conflict — semantic), HARDSTOP-07 (AC unclear — semantic), HARDSTOP-08 (contracts missing — semantic). Phase 3b future task if mechanical heuristics emerge.
- **Non-mechanical rule warnings** beyond what `rule-consistency-check.ps1` already covers. Format-aligning the existing warnings (Scope B) is bounded; introducing new warning detectors is a separate scope.
- **Cross-tool hooks** (Bash-matcher, AskUserQuestion-matcher) — `protect-main-branch.ps1` and `notify.ps1` operate at Bash / UI layer, outside the rule-surface scope.
- **CI integration of the hook fixtures** — the diagnostic fixtures (Scope D) are developer-run only. Adding them to `.github/workflows/ci.yml` is a downstream task once the fixtures prove stable.
- **PostToolUse extensions** — auto-format-check + test-on-edit already cover the post-Edit lane; this task does not add to them.

---

# Acceptance Criteria

- [ ] `.claude/hooks/hardstop-detect.ps1` exists and implements detection for `HARDSTOP-01` / `HARDSTOP-03` / `HARDSTOP-05` / `HARDSTOP-09` / `HARDSTOP-10` (5 mechanical triggers).
- [ ] Each detector emits a `{decision: "block", reason: <4-block stanza>}` JSON output matching the canonical stanza body in `CLAUDE.md § Hard Stop Rules` for the corresponding `HARDSTOP-NN`.
- [ ] `.claude/hooks/spec-check.ps1` and `.claude/hooks/rule-consistency-check.ps1` emit reasons in 4-block format with `SPEC-CHECK-NN` / `RULE-CONSISTENCY-NN` IDs.
- [ ] `.claude/settings.json` registers `hardstop-detect.ps1` under both `Edit` and `Write` matchers, running FIRST in the hook list.
- [ ] `.claude/hooks/__tests__/` carries one fixture per detector (5 fixtures) that, when run via the documented entry point, assert the JSON output shape.
- [ ] `platform/lint-remediation-message-standard.md § Forward look` updated to reflect Phase 3 delivery.
- [ ] ADR-MONO-006 § 6 outstanding #1 updated (delivered, with the PR# + commit hash).
- [ ] No production code under `libs/` or `projects/` modified.
- [ ] No regression in existing hook behaviour — `spec-check.ps1` still fires `ask` on `specs/contracts/` / `platform/` edits; `rule-consistency-check.ps1` still detects skill/agent/command frontmatter gaps.

---

# Related Specs

- `platform/lint-remediation-message-standard.md` (canonical format — Hook output must conform)
- `CLAUDE.md § Hard Stop Rules` (10 stanza — Hook emission body source)
- `docs/adr/ADR-MONO-006-lint-remediation-as-agent-context.md` (Phase 3 provenance + § 6 outstanding #1)
- `.claude/settings.json` (hook registration)
- Existing hooks: `.claude/hooks/spec-check.ps1` / `rule-consistency-check.ps1` / `protect-main-branch.ps1` (precedent pattern)
- Memory `reference_openai_harness_engineering.md` § "강제 메커니즘 핵심 3가지" #2 (mechanism source)

# Related Skills

- N/A — agent-config infrastructure, no skill dispatched during impl.

---

# Related Contracts

None — `.claude/hooks/` is harness configuration, not a HTTP/event contract surface.

---

# Target Service

N/A — agent harness configuration. Targets:

- `.claude/hooks/hardstop-detect.ps1` (new)
- `.claude/hooks/spec-check.ps1` (modified — format alignment)
- `.claude/hooks/rule-consistency-check.ps1` (modified — format alignment)
- `.claude/hooks/__tests__/` (new — fixture scripts)
- `.claude/hooks/README.md` (new or modified)
- `.claude/settings.json` (modified — hook registration)
- `platform/lint-remediation-message-standard.md` (1-line update)
- `docs/adr/ADR-MONO-006-lint-remediation-as-agent-context.md` (§ 6 row update)

---

# Architecture

Hook architecture is dictated by Claude Code's PreToolUse contract — script reads JSON from stdin, writes JSON to stdout with `decision` + `reason` fields. `decision: "block"` halts the tool call; `decision: "ask"` surfaces a confirm prompt; absent/other = allow. The agent's next turn receives the `reason` field as part of the tool-call error context.

The hook reads `tool_input.file_path` (Edit/Write) and optionally `tool_input.new_string` / `tool_input.content`. Detection logic is pure file-system + string match — no network calls, no external commands beyond `Test-Path` / `Get-Content`.

Project / service name list (HARDSTOP-03 detection) is built once per hook invocation by globbing `projects/*/PROJECT.md` and `projects/*/specs/services/*/`. The cost is acceptable for an interactive hook (~10ms on warm cache). No caching layer needed at this phase.

---

# Implementation Notes

## Hook output JSON contract

```powershell
$stanza = @"
[VIOLATION] HARDSTOP-03: Shared library file ``$filePath`` references project name ``$detectedProject`` at line $lineNo.
[WHY] Shared libraries must remain project-agnostic so every project can adopt them unchanged; mixing project-specific content here breaks the Library vs Project boundary that this rule library is built on.
[REMEDIATION] Choose one:
  1. Move the offending content back to the owning project under ``projects/$detectedProject/`` (apps / specs / knowledge / docs as appropriate) and keep the shared file generic.
  2. If the content is genuinely cross-service / cross-project, propose promotion via ``docs/adr/ADR-MONO-XXX-<slug>.md`` proposing a generic abstraction, and PAUSE this task until the ADR is ACCEPTED.
  3. If the content is documentation noise (example / illustration), replace it with an abstract placeholder (`<service>`, `<entity>`) per existing precedent.
[REFERENCE] platform/shared-library-policy.md § Forbidden in Shared Libraries
"@

$result = @{
    decision = "block"
    reason   = $stanza
}
$result | ConvertTo-Json -Compress
exit 0
```

## Stanza body source — single source of truth

The hook MUST NOT duplicate stanza prose. The canonical stanza body lives in `CLAUDE.md § Hard Stop Rules`. The hook reads a parameterised template (file path + line number injected) — the invariant statements (`[WHY]`, `[REMEDIATION]` options 1-N, `[REFERENCE]`) match the CLAUDE.md stanza verbatim. Future stanza wording changes in CLAUDE.md MUST be propagated to the hook in the same PR (the change-protocol section of `platform/lint-remediation-message-standard.md` already requires this).

A diagnostic test fixture verifies stanza body match — failure indicates drift.

## Ordering with sibling hooks

`spec-check.ps1` currently fires `decision: "ask"` for `specs/contracts/` / `platform/` edits. `rule-consistency-check.ps1` fires `decision: "block"` on frontmatter gaps. `hardstop-detect.ps1` MUST run FIRST so its `block` short-circuits before the lighter `ask` warnings — a Hard Stop is more authoritative than a contract-first reminder.

Hook order in `.claude/settings.json` Edit matcher list:

```
1. hardstop-detect.ps1   (new — authoritative)
2. spec-check.ps1        (existing — soft ask)
3. rule-consistency-check.ps1 (existing — block on frontmatter gaps)
```

## False-positive risk

HARDSTOP-03 detection (project/service name in shared file content) carries the highest false-positive risk — `platform/` rule files MAY legitimately reference project names in example tables or change-history sections. Mitigation:

1. **Inverse pattern allow-list** — match only when the project name appears as a path token (e.g. `apps/wms/` / `projects/wms/`) or as code-fenced identifier (e.g. `WmsOutboundOrder`), not as free-prose mention.
2. **`<!-- hardstop-allow: <reason> -->` line annotation** — if the false-positive recurs, the author may add this comment immediately above the offending line. The hook respects the annotation and skips that line.
3. Diagnostic fixture coverage: include 2 fixtures for HARDSTOP-03 — one positive (genuine violation), one negative (annotated allow). Verify the hook fires on the first and skips the second.

## Encoding consistency

Existing hooks use UTF-8 stdin reading. New hook follows same pattern (`StreamReader([Console]::OpenStandardInput(), [System.Text.Encoding]::UTF8)`). JSON output passes through `ConvertTo-Json -Compress`. No BOM is required for stdout (matched by Claude Code parser).

---

# Edge Cases

- **Multiple triggers fire on the same edit** — emit multiple stanzas, comma-separated newline-delimited in the single `reason` field. The `platform/lint-remediation-message-standard.md` § "Multiple simultaneous violations" already mandates one stanza per trigger.
- **Hook fires during portfolio-sync** (`/tmp/portfolio-sync/<project>/`) — `protect-main-branch.ps1` already has an allowlist for this path. `hardstop-detect.ps1` SHOULD apply the same allowlist: portfolio-sync is rewriting derived artifacts, not authoring shared content.
- **Hook fires on internal AI-session edits to `.claude/hooks/` itself** — when authoring this task or future hook updates, the hook would self-fire. Mitigation: HARDSTOP-03 detector skips `.claude/hooks/` edits explicitly (the directory is itself shared, but content here MAY reference projects in detection-pattern allow-lists — meta-config exception).
- **HARDSTOP-09 false negative** — agent edits `projects/<name>/apps/<service>/build.gradle` (non-`src/main/`) which doesn't trigger the `apps/<service>/src/main/` filter, even though architecture spec is genuinely missing. Mitigation: the trigger is best-effort — `src/main/` covers ≥95% of production-code edits; other paths fall through to the other hooks and human review.
- **HARDSTOP-10 false positive** — agent partially edits `architecture.md`, removing the Service Type header in one Edit and adding it back in the next. The first Edit fires. Mitigation: the hook fires on Write (full-file) cleanly; on Edit (partial), the post-edit content may not be fully assembled. Detector reads the file AFTER applying the proposed edit (string-merge `old_string` / `new_string`) before evaluating.

---

# Failure Scenarios

- **Stanza drift between CLAUDE.md and hook output** — the hook's emitted reason text diverges from the canonical CLAUDE.md stanza. The diagnostic test fixture catches this on developer run, but no CI gate prevents it pre-Phase-3b. Mitigation: change-protocol in `platform/lint-remediation-message-standard.md` § Change protocol already requires the hook update in the same PR as the stanza wording change.
- **Hook performance regression** — if HARDSTOP-03 detection's project/service name list rebuild on every Edit causes perceptible session lag, cache the list in `$env:TEMP` keyed by `git rev-parse HEAD`. Defer this optimisation until the lag is observed.
- **JSON parsing failure** — if `tool_input.file_path` is absent or unparseable, the hook silently exits 0 (allow) rather than blocking. This matches existing hooks' fail-open posture.

---

# Test Requirements

## Diagnostic test fixtures (developer-run, not CI)

`.claude/hooks/__tests__/` carries one `.ps1` fixture per detector:

```powershell
# .claude/hooks/__tests__/hardstop-01-no-project-md.ps1
$input = @'
{
  "tool_input": {
    "file_path": "/tmp/some-orphan-dir/file.md",
    "new_string": "..."
  },
  "cwd": "/tmp/some-orphan-dir"
}
'@
$output = $input | pwsh -NoProfile -File ../hardstop-detect.ps1
$parsed = $output | ConvertFrom-Json
if ($parsed.decision -ne "block") { throw "Expected block, got $($parsed.decision)" }
if ($parsed.reason -notmatch '\[VIOLATION\] HARDSTOP-01:') { throw "Wrong stanza ID" }
if ($parsed.reason -notmatch '\[WHY\]')                    { throw "Missing WHY block" }
if ($parsed.reason -notmatch '\[REMEDIATION\] Choose one:') { throw "Missing REMEDIATION block" }
if ($parsed.reason -notmatch '\[REFERENCE\]')              { throw "Missing REFERENCE block" }
"PASS: hardstop-01"
```

Single-entry runner: `.claude/hooks/__tests__/run-all.ps1` invokes each fixture, exit 1 on first failure.

## Manual session check

After landing, run a deliberate violation in a throwaway branch (e.g. write `projects/wms-platform/apps/master-service` reference into a `platform/` file) and verify the hook fires with the standardised stanza in the next turn. Manual smoke test; not CI-gated.

---

# Definition of Done

- [ ] All Acceptance Criteria pass.
- [ ] `pwsh .claude/hooks/__tests__/run-all.ps1` exits 0 with 5+ PASS lines.
- [ ] Manual session smoke test confirms agent receives the 4-block stanza on a deliberate violation.
- [ ] PR description quotes the Phase 1+2 ROI ≈ 50% claim from ADR-MONO-006 § 2.4 and asserts Phase 3 closes the remaining ≈ 50%.
- [ ] Memory `reference_openai_harness_engineering.md` § "우선순위 액션 후보" item #1 — full closure annotation.
- [ ] ADR-MONO-006 § 6 outstanding #1 marked DELIVERED with PR + commit reference.

---

# Provenance

ADR-MONO-006 § 6 outstanding #1 (filed in TASK-MONO-059, PR #383, commit `958a7d95`). Memory `reference_openai_harness_engineering.md` § "강제 메커니즘 핵심 3가지" #2 (mechanism source) + § "우선순위 액션 후보" #1 (priority ranking — Phase 3 closes the ≈ 50% gap remaining after TASK-MONO-059 delivered Phase 1+2).

D4 OVERRIDE applies per ADR-MONO-003 § 3.4 risk 2 — scope extended to OpenAI Harness gap series per user decision recorded in ADR-MONO-006 § Provenance. This task is the second deliverable under that extended scope.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical PowerShell + JSON detection logic + fixture authoring; judgment requirement is concentrated in the spec — implementation is largely pattern-matching against the existing hook precedents).
