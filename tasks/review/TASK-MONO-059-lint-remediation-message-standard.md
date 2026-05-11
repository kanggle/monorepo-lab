# Task ID

TASK-MONO-059

# Title

Standardize Hard Stop / rule-violation messages into agent-followable remediation form (OpenAI Harness gap A)

# Status

review

# Owner

monorepo

# Task Tags

- spec
- rules
- platform
- adr

---

# Goal

Convert every Hard Stop and rule-violation emission in the shared rule surface from prose ("stop and report the issue") into a **structured remediation message** an agent can replay verbatim. The standard message body carries four blocks — `[VIOLATION]` / `[WHY]` / `[REMEDIATION]` / `[REFERENCE]` — so that a session that hits the rule receives, in the same turn, the concrete file paths and action choices needed to unblock.

This is the first concrete delivery of **OpenAI Harness Engineering gap A** (memory `reference_openai_harness_engineering.md` § "monorepo-lab 갭 매핑" — "lint 에러 → 에이전트 remediation 주입" identified as the "최대 갭" / largest gap). The pattern source is OpenAI's reported finding that custom lint error messages double as the next-turn context for the agent (memory § "강제 메커니즘 핵심 3가지" #2).

This task delivers **Phase 1 (inventory) + Phase 2 (standard + 10 Hard Stop stanza rewrite + 2 adjacent emission rewrite)**. Phase 3 (automated injection via PreToolUse / Stop hook) is split into a separate `ready/` task (TASK-MONO-060 candidate). The Phase 1+2 ROI is **partial** without Phase 3 — the standard ensures consistent format whenever a human, agent, or skill manually emits a violation, but does not yet make the message *active context* on the agent's next turn (which requires hook injection). This trade-off is documented in ADR-MONO-006 § Outcome / § Follow-up.

---

# Scope

## In Scope

### A. New canonical spec — `platform/lint-remediation-message-standard.md`

Defines:
- The 4-block message template (`[VIOLATION]` / `[WHY]` / `[REMEDIATION]` / `[REFERENCE]`).
- Block-by-block authoring rules (each `[REMEDIATION]` MUST list ≥2 concrete options or one option + one escalation path; each `[REFERENCE]` MUST cite the rule file path + section anchor).
- Emission contracts — when a Hard Stop trigger fires, when a non-blocking rule warning fires, when a skill produces a finding.
- Recommended output format for `validate-rules` / `audit-memory` / future similar skills (advisory only — skill bodies are out of scope per the user-acknowledged decision recorded in ADR-MONO-006 § 3.3).

### B. CLAUDE.md "Hard Stop Rules" — 10-stanza rewrite

Each of the 10 Hard Stop conditions becomes a labelled stanza in the new format. The closing prose ("Report the blocking issue explicitly. Do not attempt workaround implementation.") is replaced with a pointer to `platform/lint-remediation-message-standard.md`. The 10 conditions are preserved verbatim in intent — only the *response template* changes.

The 10 conditions (per current `CLAUDE.md` L138–L147):

| # | Trigger |
|---|---|
| 1 | No `PROJECT.md` locatable for the working context |
| 2 | `PROJECT.md` missing/unparseable OR declares unknown domain/trait |
| 3 | Shared library file contains project-specific content |
| 4 | Domain/trait rule conflicts with common without explicit `## Overrides` block |
| 5 | Task is not in the appropriate `tasks/ready/` |
| 6 | Required specifications do not exist or conflict |
| 7 | Acceptance criteria are unclear |
| 8 | Required contracts are missing |
| 9 | Task requires architecture decisions not documented in specs |
| 10 | Target service's `Service Type` is undeclared or not in `platform/service-types/INDEX.md` |

### C. Adjacent emission rewrites — 2 isolated stanzas

- `platform/architecture-decision-rule.md` L24 — "If the declared architecture is missing, stop and report the issue." → standard 4-block stanza pointing back to the format spec.
- `rules/README.md` L85 — Hard Stop redirect updated to reference the standard format (1-line pointer change).

### D. ADR-MONO-006 — `lint-remediation-as-agent-context.md`

PROPOSED → ACCEPTED in the same PR. Records:
- Decision driver (OpenAI Harness gap A, memory reference).
- D1 (canonical 4-block template).
- D2 (10 Hard Stop stanza + 2 adjacent stanza enforcement).
- D3 (skill body alignment **out of scope**, recommended format only — user-confirmed).
- D4 (Phase 3 hook automation deferred to TASK-MONO-060 candidate; Phase 1+2 ROI explicitly partial).
- Alternatives Considered — rules/common.md new section vs new platform file; chose platform/ for parity with error-handling.md catalog pattern.
- Consequences — every new shared-rule violation emission MUST use the format; reviewers gate this in `review-checklist` updates (out of scope here, follow-up).
- D4 OVERRIDE applies per ADR-MONO-003 § 3.4 risk 2 (cleanup-class scope extended to OpenAI Harness gap series, user-acknowledged).

### E. Index/lifecycle updates

- `docs/adr/INDEX.md` — append ADR-MONO-006 row.
- `tasks/INDEX.md` — `ready` → `review` move on impl bundle.

## Out of Scope

- **Phase 3 hook automation** — PreToolUse / Stop hook that auto-detects Hard Stop triggers and injects the formatted message into the next turn. Split into TASK-MONO-060 candidate. The Phase 1+2 deliverable here is **the standard + the rewrites**; without Phase 3, format compliance depends on the emitter (human, agent, or skill body) following the standard voluntarily.
- **`validate-rules` / `audit-memory` skill body alignment** — these skills are user-level / plugin-supplied (not present in this repo's `.claude/skills/`). The standard documents the recommended output format only; actual skill body edits would land in the plugin repository.
- **Linter / checkstyle / ESLint output rewrites** — those are tool-emitted not rule-emitted. The standard targets the *shared rule surface* (CLAUDE.md, `platform/`, `rules/`), not tool-emitted compiler/lint errors.
- **`review-checklist` skill update to gate format compliance** — follow-up, filed only if reviewers find drift. Adding it pre-emptively would expand scope and risk ROI saturation before the format proves itself in practice.
- **Project-internal task / rule files** under `projects/<name>/` — `Hard Stop` triggers there inherit the shared format, no per-project rewrite needed in this task.
- **Backfilling historical ADR / done-task language** — ADRs are immutable; historical tasks are frozen. The standard applies forward only.

---

# Acceptance Criteria

- [ ] `platform/lint-remediation-message-standard.md` exists, declares the 4-block template, and documents the emission contract for Hard Stop / non-blocking warning / skill finding cases.
- [ ] `CLAUDE.md § Hard Stop Rules` carries 10 labelled stanzas in the standard format, one per existing trigger condition, with the trigger numbering preserved (1–10).
- [ ] `CLAUDE.md` closing prose for the Hard Stop section points to `platform/lint-remediation-message-standard.md` as the authoritative format definition.
- [ ] `platform/architecture-decision-rule.md` L24 emission is rewritten to the 4-block standard.
- [ ] `rules/README.md` L85 redirect references the new format spec.
- [ ] `docs/adr/ADR-MONO-006-lint-remediation-as-agent-context.md` exists with Status = ACCEPTED, Decision driver = TASK-MONO-059, and records D1–D4.
- [ ] `docs/adr/INDEX.md` row for ADR-MONO-006 appended in numerical order.
- [ ] No service / project code modified (this is meta-policy authoring only).
- [ ] No new files outside the four enumerated targets (`platform/lint-remediation-message-standard.md`, `docs/adr/ADR-MONO-006-…md`, the task file itself, and the in-place edits).
- [ ] D4 OVERRIDE marker (`ADR-MONO-003 § 3.4 risk 2 — cleanup-class scope, user-acknowledged for OpenAI Harness gap series`) appears in ADR-MONO-006 § Provenance.

---

# Related Specs

- `CLAUDE.md` (target — Hard Stop Rules section overwritten)
- `platform/lint-remediation-message-standard.md` (new — canonical format spec)
- `platform/architecture-decision-rule.md` (target — L24 stanza rewrite)
- `rules/README.md` (target — L85 cross-ref update)
- `docs/adr/ADR-MONO-006-lint-remediation-as-agent-context.md` (new — narrative + decision record)
- `docs/adr/INDEX.md` (target — row append)
- Memory `reference_openai_harness_engineering.md` § "monorepo-lab 갭 매핑" (gap A source) + § "강제 메커니즘 핵심 3가지" #2 (mechanism source)

# Related Skills

- N/A in this task. The standard documents the recommended output format for `validate-rules` / `audit-memory` (advisory only); their skill bodies live in the user-level plugin layer outside this repo.

---

# Related Contracts

None — internal monorepo operating-rules update only. No HTTP / event contract change.

---

# Target Service

N/A — shared paths only:
- `CLAUDE.md`
- `platform/lint-remediation-message-standard.md` (new)
- `platform/architecture-decision-rule.md`
- `rules/README.md`
- `docs/adr/ADR-MONO-006-lint-remediation-as-agent-context.md` (new)
- `docs/adr/INDEX.md`
- `tasks/INDEX.md`

---

# Architecture

N/A — meta-policy and documentation authoring. No service architecture decision.

---

# Implementation Notes

## 4-block message template

```
[VIOLATION] <rule_id>: <one-line condition that fired> at <file>:<line | section>
[WHY] <invariant the rule protects — one sentence; cite the named principle or prior incident>
[REMEDIATION] Choose one:
  1. <concrete corrective action with file paths>
  2. <alternative corrective action with file paths>
  3. <escalation path: open ADR / file ready/ task / ask owner>
[REFERENCE] <rule-file-path> §<section-anchor>
```

Authoring rules (binding):

- `<rule_id>` uses the form `HARDSTOP-<NN>` for Hard Stop triggers (NN = 01–10 per CLAUDE.md numbering) or `<source-file-shortname>-<NN>` for non-blocking warnings (e.g. `SHARED-LIB-03` for shared-library-policy item 3).
- `[REMEDIATION]` MUST list at least two options OR one option + one escalation path. Single-option lists are forbidden — the value of the standard is choice surface.
- Each option line is imperative + cites concrete file paths. Vague phrasing ("review the spec", "investigate the issue") is forbidden — the option must be replayable verbatim by an agent in its next tool call.
- `[REFERENCE]` MUST resolve to an existing section anchor in the named file. Broken anchors fail review.

## 10 Hard Stop stanza in CLAUDE.md

Each stanza is ~6–8 lines. Total Hard Stop section grows from 16 lines (CLAUDE.md current L134–L149) to ~85–90 lines. The section header retains its position between "Required Workflow" and "Layer Rules"; no re-ordering of CLAUDE.md sections is required.

The 10-line closing prose currently reads:

> Report the blocking issue explicitly. Do not attempt workaround implementation.

It becomes a one-line pointer:

> Every Hard Stop emission MUST follow the standard format defined in [`platform/lint-remediation-message-standard.md`](platform/lint-remediation-message-standard.md). Do not attempt workaround implementation.

## D4 churn-clock interaction

This PR touches `CLAUDE.md` + `platform/` + `rules/` + `docs/adr/` — all shared paths. Per memory `project_monorepo_template_strategy.md` (ADR-MONO-003 = DEFERRED + D4 OVERRIDE), D4 OVERRIDE scope is **extended** here to cover the OpenAI Harness gap series (user-acknowledged decision recorded in ADR-MONO-006 § Provenance). `last_churn` marker reset is minimal — this is a cleanup-class edit consistent with prior PRs #328 / #352 / #372 / #373 / #374 (B common-rule refactor series).

---

# Edge Cases

- **Existing emissions in agent prose** — sessions that have already started before this PR lands will continue to emit prose-form stops. The standard applies forward to *new* sessions only. No retroactive cleanup of in-flight session output.
- **Skill emissions** — `validate-rules` / `audit-memory` are user-level plugin skills. Their output format remains *recommended* by this standard; alignment landing in their bodies is a separate (plugin-repo) PR. The standard does not block on that.
- **Ambiguous trigger** — a session that hits multiple Hard Stop conditions simultaneously MUST emit one stanza per fired trigger, not a merged stanza. The standard's emission contract specifies this.
- **Trigger that doesn't fit `HARDSTOP-NN`** — non-blocking warnings (e.g. spec drift detected but not blocking) use `<source-shortname>-NN` instead. The standard documents both forms.
- **Stale `[REFERENCE]` anchor after a future refactor** — section anchor breakage in CLAUDE.md / `platform/` would silently invalidate emitted messages. Mitigation: future PRs that reorganize Hard Stop / referenced sections MUST grep the standard for affected anchors and update.

---

# Failure Scenarios

- **Format drift on first real emission** — agent emits a prose-form stop instead of the standard format. Detection = manual review; correction = follow-up edit on the originating prose, or (if frequent) elevate to TASK-MONO-060 hook automation priority.
- **Anchor breakage** — see edge case above. Same mitigation.
- **Over-specified `[REMEDIATION]` options** — if option 1 hard-codes a path that later moves, the option becomes wrong. Mitigation: prefer canonical pointers (e.g. `the project's PROJECT.md`) over absolute paths where possible; absolute paths only when unambiguous.

---

# Test Requirements

N/A — docs/spec-only edit. Verification:

```bash
# 1. CLAUDE.md Hard Stop Rules section contains 10 stanzas
grep -c "^### HARDSTOP-" CLAUDE.md   # expect 10

# 2. Each stanza carries all 4 blocks
for n in 01 02 03 04 05 06 07 08 09 10; do
  for block in '\[VIOLATION\]' '\[WHY\]' '\[REMEDIATION\]' '\[REFERENCE\]'; do
    grep -A 20 "^### HARDSTOP-$n" CLAUDE.md | grep -q "$block" || echo "MISS: $n / $block"
  done
done

# 3. lint-remediation-message-standard.md exists and is referenced
test -f platform/lint-remediation-message-standard.md
grep -n "platform/lint-remediation-message-standard.md" CLAUDE.md rules/README.md platform/architecture-decision-rule.md

# 4. ADR-MONO-006 INDEX entry
grep -n "ADR-MONO-006" docs/adr/INDEX.md

# 5. No production code changed
git diff --stat main -- 'libs/' 'projects/' | wc -l   # expect 0
```

CI: path-filter `rules` + `platform` flags match → `Build & Test` + `changes` jobs run; service Integration / E2E / boot-jars jobs SKIP (no service paths touched). Expected ≈ TASK-MONO-058 cadence (chore PR baseline ~20s).

---

# Definition of Done

- [ ] All Acceptance Criteria pass.
- [ ] Verification commands above all return expected counts.
- [ ] D4 OVERRIDE marker present in ADR-MONO-006.
- [ ] PR description summarizes: gap A delivery, Phase 1+2 partial ROI claim, Phase 3 hook deferred to TASK-MONO-060 candidate.
- [ ] Memory `reference_openai_harness_engineering.md` § "우선순위 액션 후보" item #1 — partial closure annotation (Phase 1+2 done, Phase 3 pending).

---

# Provenance

Memory `reference_openai_harness_engineering.md` (2026-05-07 receipt) § "monorepo-lab 갭 매핑" — gap A flagged as "최대 갭" / largest gap, with explicit `우선순위 액션 후보` #1: "Hard Stop / 룰 위반 메시지를 '에이전트가 그대로 따라할 수 있는 형태'로 표준화 — 가장 큰 ROI".

OpenAI Harness Engineering (Ryan Lopopolo, OpenAI MTS) reports the mechanism as one of three core enforcement primitives (memory § "강제 메커니즘 핵심 3가지" #2 — "커스텀 lint 에러 메시지 = 에이전트 다음 턴 컨텍스트"). The standard authored in this task is the first monorepo-lab attempt to encode that primitive into our shared rule surface.

User decisions recorded 2026-05-12 (this task drafting session):
1. D4 OVERRIDE extended to OpenAI Harness gap series (beyond original "B common-rule cleanup 한정" scope).
2. `validate-rules` / `audit-memory` skill body alignment **out of scope** — spec documents recommended format only.
3. Phase 3 hook automation split into separate `ready/` task (TASK-MONO-060 candidate).

분석=Opus 4.7 / 구현 권장=Opus 4.7 (meta-policy authoring + ADR decision + cross-file rule rewrite — judgment-heavy, not mechanical).
