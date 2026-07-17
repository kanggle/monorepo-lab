# TASK-MONO-426 — /validate-rules found rule files that contradict the platform specs they cite, and a shared command that depends on private-only sources

- **Type**: TASK-MONO (monorepo-level — shared paths `.claude/commands/`, `.claude/agents/`, `.claude/skills/`)
- **Status**: done
- **Target**: `.claude/commands/{design-event,write-tests,refactor-spec}.md`, `.claude/agents/common/{event-architect,backend-engineer,qa-engineer}.md`, `.claude/skills/testing/contract-test/SKILL.md`
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (rule-file wording + spec alignment)

## Goal

A `/validate-rules` full scan (2026-07-18) surfaced two Critical and two Warning rule-file inconsistencies.
Each was verified against the actual repo (agent reports were treated as hypotheses and re-checked). Bring the
consumers back into agreement with the platform specs they cite.

## AC-0 — 착수 시 재측정 (audit-born; the scan is a hypothesis, not a source)

Measured 2026-07-18. Re-verify each finding against current files before editing; if an intervening change
already fixed one, drop it and note it. Re-confirm the canonical shape at the source (`event-driven-policy.md`
envelope, `testing-strategy.md` H2 exception) rather than trusting this ticket's transcription.

## AC-1 (Critical) — event envelope: the command and its agent contradict the spec they both cite

`design-event.md` (§ Standard Envelope + § Rules) and `event-architect.md` (§ Event Schema) define the event
envelope as **snake_case, 5 fields** (`event_id`/`event_type`/`occurred_at`/`source`/`payload`). The command's
own step 2 tells the agent to read `platform/event-driven-policy.md`, whose § Event Envelope Format mandates
**camelCase, 10 base fields** (`eventId`/`eventType`/`eventVersion`/`occurredAt`/`source`/`aggregateType`/
`aggregateId`/`traceId`/`actorId`/`payload`). Real contracts already follow the camelCase form
(`projects/iam-platform/specs/contracts/events/auth-events.md:17` = `"eventId"`). Following either the command
or the agent produces a contract that violates the platform spec.

- **Authoritative**: `platform/event-driven-policy.md` (contract SoT, Source-of-Truth layer 5; the command
  itself defers to it). Correct both consumers to the canonical camelCase 10-field envelope, and fix the
  `design-event.md § Rules` line that reads "Envelope fields: snake_case" → camelCase.

## AC-2 (Critical) — `contract-test/SKILL.md` points at a directory that does not exist

Line 18 sends API-response schemas to `specs/contracts/api/`. No project has that directory; the platform-wide
convention (`platform/architecture.md`, `platform/service-boundaries.md`) is `specs/contracts/http/`, and every
other skill uses it. (Line 19's `specs/contracts/events/` is correct and stays.)

- **Fix**: `specs/contracts/api/` → `specs/contracts/http/`.

## AC-3 (Warning) — a shared command depends on private-only sources (the MONO-423 defect class)

`refactor-spec.md` cites, as if repo policy:
- line 207 — "global CLAUDE.md § Windows Shell Environment" (the operator's **private global config**; absent
  from the project `CLAUDE.md`), and
- line 210 — `feedback_pr_on_request.md` (a **private AI-memory file**, not in the repo).

Both underlying instructions are sound, but a shared `.claude/commands/` file must not depend on sources that
another developer or a fresh session cannot see — the same class MONO-423 just cleaned up. **Fix**: state each
rule source-agnostically (don't use Bash `sed`/`sed -i` for bulk edits; open a PR only when the user asks —
keeping the real repo citation `tasks/INDEX.md § PR Separation Rule`), removing the two private-only citations.

## AC-4 (Warning) — "H2 forbidden" is now over-strict vs the platform exception

`write-tests.md`, `backend-engineer.md`, and `qa-engineer.md` state "H2 forbidden" unconditionally, but
`platform/testing-strategy.md § H2 auxiliary-slice exception` now permits a narrow, non-authoritative
`@DataJpaTest` H2 slice alongside an authoritative Testcontainers IT. The blanket statement rejects a pattern
the platform spec explicitly allows.

- **Authoritative**: `platform/testing-strategy.md` (owns the exception). **Fix**: qualify the three "H2
  forbidden" lines to point at the exception, without inverting the default (integration layer stays
  Testcontainers-only).

## Scope

- **In**: the seven rule-file edits above.
- **Out**: the Info items from the same scan (coordinator `service_types` optional/load-bearing wording;
  implement-task Single-Task worktree cross-ref; start-task quoted sub-label; validate-rules `skills/INDEX.md`
  shorthand; agent responsibility-overlap boundaries) — cosmetic, batch later if at all. No spec is changed:
  every fix moves a consumer toward an unchanged platform spec.

## Acceptance Criteria

- **AC-0..AC-4**: as above.
- **AC-5**: after fixing, re-run the relevant `/validate-rules` checks (envelope grep, `specs/contracts/api`
  grep, private-citation grep, H2-blanket grep) and confirm each returns clean; self-verify each detector
  against a known-true term first.
- **AC-6**: `.claude/{commands,agents}` edits are agent-committable (measured, MONO-424); `.claude/skills/`
  needs explicit per-action authorization — if the classifier blocks the `contract-test/SKILL.md` edit or its
  commit, hand that one hunk to the user and land the rest.
- **AC-7**: docs/config-only; `changes` gate green, code lanes SKIPPED.

## Related Specs / Contracts

- `platform/event-driven-policy.md § Event Envelope Format` (authoritative for AC-1)
- `platform/testing-strategy.md § H2 auxiliary-slice exception` (authoritative for AC-4)
- `platform/architecture.md` / `platform/service-boundaries.md` (`specs/contracts/http/` convention, AC-2)
- `tasks/INDEX.md § PR Separation Rule` (the real citation kept in AC-3)

## Edge Cases / Failure Scenarios

- **The envelope fix is not just the JSON block.** `design-event.md § Rules` states "Envelope fields:
  snake_case" in prose — fixing only the code block leaves the contradiction in the Rules list.
- **Do not invert the H2 default.** The exception is narrow (non-authoritative `@DataJpaTest` slice only); the
  integration-test layer stays Testcontainers-only. An over-correction that reads "H2 is fine" is the same
  defect mirrored.
- **AC-3 is source-hygiene, not instruction-deletion.** Keep the actual guidance (no bulk `sed`; PR-on-request)
  — only remove the citations to sources a non-operator reader cannot resolve.
- **`.claude/skills/` may genuinely block.** Per the MONO-424 map, skills/ is intent-gated; attempt once, and
  if blocked, hand the single SKILL.md hunk over rather than shell-writing around it.
