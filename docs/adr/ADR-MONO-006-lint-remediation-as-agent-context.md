# ADR-MONO-006 — Lint Remediation Message as Agent Context

**Status:** ACCEPTED
**Date:** 2026-05-12
**History:** PROPOSED 2026-05-12 (TASK-MONO-059) → ACCEPTED 2026-05-12 (same PR — meta-policy ADR with no implementation gating).
**Decision driver:** TASK-MONO-059, sourced from memory `reference_openai_harness_engineering.md` § "monorepo-lab 갭 매핑" (gap A — "lint 에러 → 에이전트 remediation 주입") and § "강제 메커니즘 핵심 3가지" #2 (the OpenAI mechanism: "custom lint error messages = agent's next-turn context").
**Supersedes:** none — first ADR formalising the violation-message contract across the shared rule surface.
**Related:** [ADR-MONO-003](ADR-MONO-003-phase-5-template-extraction-deferred.md) § 3.4 risk 2 (D4 OVERRIDE scope — extended here to cover the OpenAI Harness gap series, user-acknowledged), [CLAUDE.md § Hard Stop Rules](../../CLAUDE.md#hard-stop-rules), [platform/lint-remediation-message-standard.md](../../platform/lint-remediation-message-standard.md), [platform/architecture-decision-rule.md](../../platform/architecture-decision-rule.md), [rules/README.md § Conflict Rules](../../rules/README.md#conflict-rules).

---

## 1. Context

### 1.1 The gap

A rule violation has two audiences: the human reviewer reading the log after the fact, and the agent's next turn. Prose stops like "If the declared architecture is missing, stop and report the issue." satisfy the first audience and starve the second — the agent has nothing to *do* with the message beyond echoing it back.

OpenAI's Harness Engineering report (Lopopolo, 2025) names the mechanism explicitly: their Codex agents are made productive by **custom lint error messages that double as the agent's next-turn context** — every violation message ships with the concrete remediation steps the agent should take next, so the lint error becomes input-grade context, not output-grade prose.

The monorepo-lab Hard Stop Rules (current `CLAUDE.md` § Hard Stop Rules) follow the prose-stop pattern: a 10-condition list that ends with "Report the blocking issue explicitly. Do not attempt workaround implementation." This is human-tone — fine for the audit log, near-useless as the agent's next-turn handle. Memory `reference_openai_harness_engineering.md` flagged this as the **largest gap** between the monorepo-lab harness and the OpenAI pattern, and ranked it priority action #1.

### 1.2 Why a policy ADR

The choice is not "should we adopt the OpenAI pattern" — it is "what form does the standard take, and how do existing emission sites adapt." Three sub-decisions need pinning down so future emissions don't drift:

1. **Template body.** What blocks does every emission carry, and what are the authoring rules per block?
2. **Scope of enforcement.** Does the standard apply to Hard Stop only, or also to non-blocking warnings and skill findings?
3. **Phase boundaries.** What does this ADR deliver immediately, and what is split into follow-up work?

This ADR pins all three and records the user-acknowledged D4 OVERRIDE extension that made the immediate delivery possible.

---

## 2. Decision

### 2.1 D1 — Canonical 4-block template

Every Hard Stop and rule-violation emission across the shared rule surface MUST carry exactly four blocks, in this order: `[VIOLATION]` / `[WHY]` / `[REMEDIATION]` / `[REFERENCE]`. The body of the template, block-by-block authoring rules, and worked examples live in [`platform/lint-remediation-message-standard.md`](../../platform/lint-remediation-message-standard.md) — that file is the canonical reference. This ADR records *why* the template takes that shape and *that* it applies; the operational detail (anchor format, escalation paths, multiple-trigger handling) lives in the spec.

Binding sub-rules (carried in the spec):

- `[REMEDIATION]` MUST list ≥2 options OR 1 option + 1 escalation path. Single-option lists are forbidden.
- Each option is imperative + cites concrete file paths. Vague phrasing ("review the spec", "investigate the issue") is forbidden.
- `[REFERENCE]` MUST resolve to an existing section anchor — broken anchors fail review.
- One stanza per fired condition. Multiple simultaneous triggers produce multiple stanzas, never a merged one.

### 2.2 D2 — 10 Hard Stop stanza + 2 adjacent emission stanza

The 10 Hard Stop conditions currently listed in `CLAUDE.md` § Hard Stop Rules (`HARDSTOP-01` … `HARDSTOP-10` after this PR) are each rewritten as a standardised stanza. The trigger numbering and the underlying conditions are preserved verbatim — only the response template changes.

Two adjacent emission sites are also rewritten in the same PR because they share the same audience problem:

- `platform/architecture-decision-rule.md § Mandatory Rule` — "If the declared architecture is missing, stop and report the issue." → `ARCH-RULE-01` stanza in the standard format.
- `rules/README.md § Conflict Rules` item 3 — Hard Stop redirect updated to cite the new format spec.

Project-internal task / rule files under `projects/<name>/` are NOT rewritten in this ADR's delivery — those inherit the shared format automatically, and rewriting them would duplicate maintenance surface without adding signal.

### 2.3 D3 — Skill body alignment out of scope

`validate-rules` and `audit-memory` are user-level / plugin-supplied skills not present in this repo's `.claude/skills/`. The standard documents the **recommended** output format so these skills' findings slot into the same shape as agent-emitted Hard Stop messages, but the actual skill body edits are a separate PR landing in the plugin repository.

This was an explicit user decision during TASK-MONO-059 drafting (2026-05-12) — option "spec 에 권장 format 만 명시, skill 본체 수정 out of scope" was chosen over the alternative of bundling skill body changes into the same PR. Rationale: skill body alignment is best-effort cosmetic work; gating the standard's adoption on it would invert the priority. The format itself is the load-bearing change.

### 2.4 D4 — Phase 3 hook automation deferred

This ADR delivers **Phase 1 (inventory) + Phase 2 (standard authoring + emission rewrites)**. Phase 3 — a PreToolUse / Stop hook in `.claude/hooks/` that auto-detects Hard Stop trigger conditions and **injects** the formatted message into the agent's next-turn prompt — is split into a separate `tasks/ready/` candidate (`TASK-MONO-060`, to be filed).

The ROI of Phase 1+2 alone is explicitly **partial**: the standard ensures format consistency *when* an emitter (agent / human / skill) follows it, but does not yet make the formatted message *active context* on the agent's next turn. The OpenAI Harness Engineering report describes the mechanism as "lint error = next-turn context" — the value emerges when the message is *injected*, not just *available*.

Phase 1+2 is a prerequisite for Phase 3 (the hook has to know the template shape to inject anything meaningful), so the ordering is correct. But "we have the standard" should not be misread as "we have the mechanism". Phase 1+2 ROI ≈ 50% of the gap-A closure; Phase 3 delivers the rest.

---

## 3. Alternatives Considered

### 3.1 Standard body location

| Option | Pros | Cons | Decision |
|---|---|---|---|
| New file `platform/lint-remediation-message-standard.md` | Parity with `platform/error-handling.md` catalog pattern (precedent: TASK-MONO-051 / -052); discoverable as a peer of other platform specs; clear single-purpose home | One more file in `platform/` | **Chosen** |
| New section in `rules/common.md` | Single-file convenience | `rules/common.md` is explicitly "index only — no rule body" per its own header; adding a rule body would break the index contract this rule library is built on | Rejected |
| New section in `CLAUDE.md` | Maximum visibility | CLAUDE.md is "catalog + safety net" per TASK-MONO-057's recent split; pulling a multi-page spec body back into it would undo that work | Rejected |

### 3.2 Number of `[REMEDIATION]` options

| Option | Pros | Cons | Decision |
|---|---|---|---|
| Exactly 1 | Simpler authoring | Defeats the standard's purpose — without choice surface the agent has nothing to pick between, just an instruction to follow | Rejected |
| ≥2, OR 1 + escalation | Mandatory choice surface; escalation path covers the "no in-band fix applies" case | Slightly more authoring effort | **Chosen** |
| Free-form (1 to N) | Maximum flexibility | "free-form" is exactly the prose-stop pattern this ADR exists to replace | Rejected |

### 3.3 Backfilling historical ADR / done-task language

Considered: sweeping every ADR / done task that contains prose-stop language and rewriting in the new format. Rejected — ADRs are immutable historical records; done tasks are frozen lifecycle artifacts. The standard applies **forward only**. Anyone reading an old ADR's prose stop will recognise it as the pre-format era; nothing about that recognition introduces risk.

### 3.4 Defer until Phase 5 unlock

Considered: parking TASK-MONO-059 in `tasks/ready/` until ADR-MONO-003 D2 re-evaluation (≥ 2026-06-10) per the existing D4 churn freeze. Rejected — the user explicitly extended D4 OVERRIDE scope to cover the OpenAI Harness gap series (decision 2026-05-12), invoking the same "cleanup-class scope" precedent that applied to the B common-rule refactor series (PR #328, #352, #372, #373, #374). The OVERRIDE is bounded: it covers gap-series work, not arbitrary cleanup, and is logged here for ADR-MONO-003a future authoring.

---

## 4. Consequences

### 4.1 Immediate

- Every new Hard Stop emission across CLAUDE.md, platform/, and rules/ uses the 4-block format. Reviewers seeing prose stops in new authoring fail review and request rewrite.
- The 10 Hard Stop stanza + 2 adjacent stanza are now machine-greppable (`^### HARDSTOP-` / `\[VIOLATION\]` / `\[REMEDIATION\]`), which Phase 3 hook automation will exploit.
- `validate-rules` / `audit-memory` outputs remain prose for now — the format spec recommends but does not enforce alignment on user-level skills.

### 4.2 D4 churn-clock impact

This PR touches CLAUDE.md + platform/ + rules/ + docs/adr/ — all shared paths. D4 OVERRIDE applies per § 3.4 above. The OVERRIDE scope is now: **B common-rule refactor (closed 5/5) ∪ OpenAI Harness gap series (in progress)**. `last_churn` marker reset is minimal — this is cleanup-class authoring consistent with PR #328 / #352 / #372 / #373 / #374 precedent.

### 4.3 Discoverability

CLAUDE.md § Hard Stop Rules grows from 16 lines (10 prose bullets + closing) to ~95 lines (10 fenced stanzas + 1-line closing pointer). This re-expands a section that TASK-MONO-057 had compressed; net CLAUDE.md line count after this PR ≈ 290 (TASK-MONO-057 brought it to 209, this ADR adds ~80). That is still 22 lines below pre-TASK-MONO-057 (312). The expansion is justified — the original 16-line compressed Hard Stop list optimised for human reading; the new 95-line stanza set optimises for agent-context replayability, which is what the section actually needs to do.

### 4.4 Future change cost

Adding a new Hard Stop trigger now requires a new stanza in the format, not just a bullet line. The cost is ~6–8 lines per trigger, which is small but non-zero. Renaming a referenced section anchor requires grepping the standard for affected `[REFERENCE]` lines and updating in the same PR — that change protocol is recorded in `platform/lint-remediation-message-standard.md § Change protocol`.

---

## 5. Verification

### 5.1 Format compliance grep

```bash
# CLAUDE.md Hard Stop Rules section contains 10 stanzas
grep -c "^### HARDSTOP-" CLAUDE.md   # expect 10

# Each stanza carries all 4 blocks
for n in 01 02 03 04 05 06 07 08 09 10; do
  for block in '\[VIOLATION\]' '\[WHY\]' '\[REMEDIATION\]' '\[REFERENCE\]'; do
    grep -A 20 "^### HARDSTOP-$n" CLAUDE.md | grep -q "$block" || echo "MISS: $n / $block"
  done
done

# Standard file exists and is referenced from all rewrite sites
test -f platform/lint-remediation-message-standard.md
grep -n "platform/lint-remediation-message-standard.md" CLAUDE.md rules/README.md platform/architecture-decision-rule.md
```

### 5.2 No service code regression

```bash
git diff --stat main..HEAD -- 'libs/' 'projects/' | wc -l   # expect 0
```

### 5.3 CI

path-filter `rules` + `platform` flags match → `Build & Test` + `changes` jobs run; service `Integration` / `E2E` / `boot-jars` jobs SKIP (no service paths touched). Expected cadence ≈ TASK-MONO-058 chore PR baseline (~20s for path-filter `changes` only).

---

## 6. Outstanding follow-ups

| # | Task | Status | Owner |
|---|---|---|---|
| 1 | `TASK-MONO-060-hook-inject-remediation-message.md` — Phase 3 hook automation (PreToolUse / Stop hook that auto-detects Hard Stop trigger conditions and injects the formatted message into the agent's next-turn prompt) | not yet filed (this PR delivers Phase 1+2 only) | monorepo |
| 2 | `validate-rules` / `audit-memory` skill body alignment to emit 4-block stanzas natively | DEFERRED to plugin-repo PR (skills are user-level / plugin-supplied; out of scope per D3) | plugin-repo owner |
| 3 | `review-checklist` skill update to gate format compliance at PR review time | not filed — only if reviewers find drift in practice; pre-emptive would expand scope before format proves itself | reviewer |

§ 6 outstanding = 1 substantive (Phase 3 hook) + 2 advisory (skill alignment / review-checklist gate). Phase 1+2 ROI ≈ 50% gap-A closure; Phase 3 closes the rest.

---

## 7. Provenance

Memory `reference_openai_harness_engineering.md` (2026-05-07 receipt, OpenAI Harness Engineering by Ryan Lopopolo) — § "monorepo-lab 갭 매핑" identifies gap A as the **largest gap** ("최대 갭") with explicit priority-action ranking #1 ("Hard Stop / 룰 위반 메시지를 '에이전트가 그대로 따라할 수 있는 형태'로 표준화 — 가장 큰 ROI").

User-acknowledged decisions captured 2026-05-12 (TASK-MONO-059 drafting session):

1. **D4 OVERRIDE extension** to OpenAI Harness gap series (beyond original "B common-rule cleanup 한정" scope per memory `project_monorepo_template_strategy.md`). Risk acknowledged. Recorded for ADR-MONO-003a future authoring.
2. **Skill body alignment out of scope** — recommended format only, plugin-repo PR is the alignment vehicle.
3. **Phase 3 hook split** into separate `tasks/ready/` candidate (TASK-MONO-060). Phase 1+2 ROI ≈ 50% partial closure documented in § 2.4 / § 6.

분석=Opus 4.7 / 구현=Opus 4.7 (meta-policy authoring + cross-file rule rewrite + ADR decision).
