# ADR-MONO-003a — D4 OVERRIDE Scope Canonicalization

**Status:** ACCEPTED
**Date:** 2026-05-12
**History:** ACCEPTED 2026-05-12 (TASK-MONO-063 — meta-policy ADR; no implementation gate).
**Decision driver:** D4 OVERRIDE scope (originally introduced by ADR-MONO-003 § 2026-05-11 update with bounded intent "B common-rule cleanup 한정") silently expanded to "B common-rule refactor ∪ OpenAI Harness gap series" via ADR-MONO-006 § 3.4 / § 4.2 / § 7 + memory `project_monorepo_template_strategy.md` L28 on 2026-05-12. The expansion is correct per the work that has landed, but the canonical source is now multi-doc, fragile against scope creep ("this new cleanup is also fine, right?"), and confusing to any reader who lands on ADR-MONO-003 alone.
**Supersedes:** none — ADR-MONO-003 § 2026-05-11 update remains the historical record of the original decision. This ADR canonicalizes the *scope definition* only; D1 (Phase 5 발사 DEFERRED) and the running-addendum pattern of ADR-MONO-003 are untouched.
**Related:** [ADR-MONO-003](ADR-MONO-003-phase-5-template-extraction-deferred.md) § 2026-05-11 update (origin), [ADR-MONO-006](ADR-MONO-006-lint-remediation-as-agent-context.md) § 3.4 / § 4.2 / § 7 (scope-extension precedent), [ADR-MONO-002](ADR-MONO-002-phase-4-template-extraction-trigger.md) (parent Phase 5 trigger ADR), memory [`project_monorepo_template_strategy`](../../../memory/project_monorepo_template_strategy.md), memory [`reference_openai_harness_engineering`](../../../memory/reference_openai_harness_engineering.md), [`scripts/verify-template-readiness.sh`](../../scripts/verify-template-readiness.sh).

---

## 1. Context

### 1.1 The fragmentation

D4 OVERRIDE was introduced as a single-paragraph update at the bottom of ADR-MONO-003 on 2026-05-11:

> **D4 = OVERRIDE** — `libs/`, `platform/`, `rules/`, `.claude/`, root build files 의 변경 자제 의도 해제. churn 시계 reset 비용 acknowledged.
>
> **Override scope (which churn is allowed)**:
> - 본 override 는 **B common rule cleanup** (`/validate-rules` 산출 fix) 에 한정 의도. PR #328 = 첫 적용.
> - 새 도메인 부트스트랩 (finance / erp / mes) 은 본 override 와 무관 — 별도 ADR 필요 (ADR-MONO-005 candidate).
> - 임의의 cross-project breaking 변경은 본 override 와 무관 — 의향 발화 시점에 명시 평가.

One day later (2026-05-12) the work program included OpenAI Harness gap A (TASK-MONO-059/060/061) and gap #2 (TASK-MONO-062), both of which touched shared paths (`platform/`, `.claude/`, `rules/`, CLAUDE.md). The user implicitly authorised these by directing the work. ADR-MONO-006 § 3.4 + § 4.2 + § 7 recorded the scope extension. Memory L28 mirrored it:

> **Override scope (2026-05-12 update) = B common rule cleanup ∪ OpenAI Harness gap series** (gap A closure: PR #383/386/388 + ADR-MONO-006 / gap #2 closure: PR #393/394 + 2 routine 등록 / gap #3 미발행 — 새 도메인 부트스트랩 및 임의 cross-project breaking 변경은 여전히 별도 평가).

The expansion is *correct* — gap-series work is structurally identical to B-refactor cleanup (cross-project DRY / surface-area reduction / no service-code change). The expansion is *fragile* because:

1. **Multi-doc consensus, no single authority.** Three files agree but no file points to itself as canonical. A reader of ADR-MONO-003 alone is misinformed. A future LLM session compacting context might preserve one source and drop the other two.
2. **Scope-creep risk.** "Is this new task also cleanup?" has no checkable answer without reading three files and inferring intent.
3. **No audit trail.** Which PRs landed under OVERRIDE is implicit — auditable only by reading individual task closures.
4. **Phase 5 trigger ambiguity.** ADR-MONO-003 § D2/D3 say "30-day churn-quiet window" + "verify-template-readiness exit 0" → ACCEPTED auto-transition. After OVERRIDE this gate is effectively bypassed (Check 3 is permanently FAIL for as long as OVERRIDE-class work continues). No file states the replacement trigger.

### 1.2 Why a separate ADR rather than an ADR-MONO-003 update

ADR-MONO-003 already carries three running-addendum updates (2026-05-09, 2026-05-10, 2026-05-11). A fourth would push the scope question further into a section a reader is unlikely to scroll to. More importantly, *consolidating authority is itself a decision* — the choice to make one file canonical, archive the others to historical, and define a meta-rule for future scope additions is a separate judgement from the original D4 OVERRIDE itself. An ADR records the judgement, not just the outcome.

The naming `ADR-MONO-003a` (not `004`) is deliberate: this ADR exists in the same decision lineage as ADR-MONO-003 (both about the Phase 5 launch gate). `ADR-MONO-003b` is reserved for the eventual Phase 5 launch ACCEPTED decision; `ADR-MONO-003a` sits between as the scope-canonicalization step.

### 1.3 Audit-trail completeness as of 2026-05-12

Eleven PRs have landed under OVERRIDE since 2026-05-11:

- B common-rule refactor: PR #328 (Wave 1) + #352 (Wave 2) + #372 (Wave 3) + #373 (rules audit) + #374 (CLAUDE.md split) = 5/5 closed.
- OpenAI Harness gap A: PR #383 (Phase 1+2 + ADR-006) + #386 (Phase 3 hook) + #388 (orphan fix) = closed.
- OpenAI Harness gap #2: PR #393 (doc-gardening) + #394 (close chore) = closed.
- OpenAI Harness gap #3: not yet filed — OPEN.

The audit trail in § 3 below enumerates each PR.

---

## 2. Decision

### D1 — IN-scope categories (exhaustive enumeration as of 2026-05-12)

The following categories are pre-authorised under D4 OVERRIDE. Any task fitting one of these may proceed without seeking new user-explicit authorisation, **provided** the task's spec or PR description cites this ADR § D1 + the matching category.

#### D1.1 — B common-rule refactor (closed 5/5)

Scope: cleanup-class refactor of shared rule surface (`rules/`, `platform/`, `CLAUDE.md`, `TEMPLATE.md`, `.claude/skills/INDEX.md`, javadoc), targeting drift / duplication / inconsistency surfaced by audit (validate-rules, manual review, agent-driven sweep). No service-code change. No contract change. No new domain.

Closed candidates (memory `project_b_common_rule_refactor_pending.md`):
- #1 CLAUDE.md split (PR #374)
- #2 error-handling.md catalog audit Wave 1+2+3 (PR #328 + #352 + #372)
- #3 rules audit (PR #373)
- #4 .claude/skills/INDEX.md sync (TASK-MONO-053 audit-only)
- #5 BaseEventPublisher javadoc (TASK-MONO-053 audit-only)

Status: **closed 5/5**. Future B-class candidates require user-explicit nod (per § D3 meta-rule) — the category is not unbounded.

#### D1.2 — OpenAI Harness gap series (in progress)

Scope: cross-cutting harness-engineering improvements derived from memory `reference_openai_harness_engineering.md` § "monorepo-lab 갭 매핑" — specifically the gap entries that touch shared rule / hook / workflow surface without changing service code or contracts.

Pre-authorised gaps (the three entries called out in memory's priority-action list):

| Gap | Closure status | PRs |
|---|---|---|
| Gap A — lint remediation message as agent context (Hard Stop / rule-violation 4-block stanza standard + PreToolUse hook injection) | **CLOSED** 2026-05-12 (Phase 1+2 + Phase 3 mechanical for HARDSTOP-01/03/05/09/10; Phase 3b semantic 5 deferred per ADR-MONO-006 § 6) | #383, #386, #388 |
| Gap #2 — doc-gardening automation (weekly `validate-rules` + `audit-memory` scheduled routines) | **CLOSED** 2026-05-12 (in-repo artifacts: `.claude/workflows/doc-gardening.md` + harness routine registration) | #393, #394 |
| Gap #3 — worktree-ephemeral observability stack (per-worktree Vector + VictoriaMetrics/Logs/Traces, OpenAI Harness "ear" sensor) | **OPEN** — not yet filed (deferred to e2e scenario accumulation; entry-condition heuristic not yet established) | — |

When gap #3 is filed (or any Phase 3b semantic Hard Stop work resumes), it inherits D4 OVERRIDE scope automatically — no re-authorisation needed.

#### D1.3 — This canonicalization ADR itself

Authoring `ADR-MONO-003a` + cross-reference updates + the forward-pointer line on ADR-MONO-003 + memory L28 update all touch shared paths (`docs/adr/`, memory directory) for the meta-purpose of recording the policy. This is included for completeness; the meta-ADR landing is itself an OVERRIDE PR (PR #TBD — to be backfilled in § 3 after merge).

### D2 — OUT-of-scope categories (explicit, non-exhaustive examples)

The following categories **do not** fall under D4 OVERRIDE and require separate user-explicit authorisation (typically a new ADR or explicit memory note before any commit):

#### D2.1 — New domain bootstrap

Adding finance / erp / mes / any new project skeleton under `projects/<name>/` is NOT an OVERRIDE-class change, even if the work itself is structurally similar to scm-platform bootstrap. ADR-MONO-002 § D4 deferred the bootstrap order decision to a future ADR (candidate `ADR-MONO-005` per ADR-MONO-003 § 5.4, now superseded by saga policy — the actual bootstrap ADR is to be re-numbered). Bootstrap inherently resets the churn clock for `libs/` (skeleton-driven `settings.gradle` change) and shifts portfolio narrative scope — both are decision points the OVERRIDE was not designed to cover.

#### D2.2 — Arbitrary cross-project breaking changes

Examples (non-exhaustive):
- Renaming a shared library export with consumer impact in 3+ services.
- Changing the shape of an existing API or event contract that crosses project boundaries.
- Bumping a `libs/java-*` `@RestController` / `@KafkaListener` shape that requires service-side adaptation.
- Removing a shared utility class with active consumers.

These are excluded even if framed as "cleanup", because the blast radius requires per-PR risk acknowledgement that the OVERRIDE pre-authorisation cannot substitute for.

#### D2.3 — Library major-version bumps with breaking changes

Spring Boot / Hibernate / JUnit / testcontainers / etc. major-version bumps with semver-breaking changes are excluded. Minor / patch bumps are fine (and typically don't touch the OVERRIDE-relevant churn clock anyway).

#### D2.4 — Phase 5 actual launch

Running `scripts/extract-template.sh` against monorepo HEAD + creating the `kanggle/project-template` GitHub repo + flipping "Template Repository" toggle is the **goal** D4 OVERRIDE was bypassed to preserve — launching it is a separate ACCEPTED decision recorded in a future `ADR-MONO-003b`. This ADR does not pre-authorise launch.

### D3 — Meta-rule for adding future categories

A new category may be added to § D1 (IN-scope) only via one of:

1. **New ADR** that explicitly invokes `ADR-MONO-003a § D3` and adds the category with rationale (preferred path for any category that will host multiple PRs, e.g. "ADR-MONO-007 — Refactoring debt sweep" or "ADR-MONO-008 — Test pyramid migration").
2. **User-explicit memory note + this ADR § 3 Audit trail row append** (acceptable for one-off cases where the category fits the spirit but doesn't justify a full ADR — e.g. a single isolated cleanup PR).

The meta-rule explicitly forbids:

- "This new cleanup is similar enough to B-refactor, so it should be fine" (without recorded acknowledgement).
- Implicit scope extension via task-spec language alone ("falls under OVERRIDE per … pattern") without ADR or memory authority.
- Multi-doc consensus (the failure mode this ADR is designed to prevent).

### D4 — Phase 5 trigger redefinition (replaces ADR-MONO-003 § D2 / § D3 auto-trigger)

The original Phase 5 trigger gate per ADR-MONO-003 § D2 / § D3 was:

- **D2** — 30-day churn-quiet window on shared paths
- **D3** — `scripts/verify-template-readiness.sh` full-mode exit 0 → ADR-MONO-003 auto-transitions DEFERRED → ACCEPTED or new ADR-MONO-003a (the "candidate" framing back then)

The OVERRIDE has effectively bypassed both: Check 3 is permanently FAIL while OVERRIDE-class work continues, and the auto-transition is supplanted by user-explicit decision per ADR-MONO-003 § 2026-05-11 update.

**Replacement trigger (in effect from 2026-05-12)**:

| Condition | Mechanism |
|---|---|
| Phase 5 launch decision | User-explicit statement of intent + new `ADR-MONO-003b` authoring (status: PROPOSED → ACCEPTED). No automatic timer. No verify-template-readiness exit-0 auto-promotion. |
| Phase 5 abandonment decision | User-explicit + new ADR (`ADR-MONO-003c` or supersede ADR-MONO-003 entirely). Records that monorepo is the permanent shape; Template repo is not pursued. |
| Re-evaluation cadence | Periodic re-evaluation deferred to user discretion. No standing trigger; the user reads this section + ADR-MONO-003 when curious. |

`scripts/verify-template-readiness.sh` retains diagnostic value but is **no longer a gate**. It surfaces what would block Template extraction *if* launched today; it does not auto-trigger anything. Check 3 may show FAIL for arbitrarily long; that is now an information signal, not a blocker.

---

## 3. Audit trail (PRs landed under D4 OVERRIDE)

Append-only. Entries added when each PR merges. Order: oldest first.

| # | PR | Date | Commit | Category | Description |
|---|---|---|---|---|---|
| 1 | #328 | 2026-05-11 | (Wave 1) | B-refactor | error-handling.md catalog audit Wave 1 (auth/transactional/general — wms backfill subset) |
| 2 | #352 | 2026-05-11 | 7ab7a9be | B-refactor | error-handling.md catalog Wave 2 (wms 11 codes) — TASK-MONO-051 |
| 3 | #372 | 2026-05-11 | 85016658 | B-refactor | error-handling.md catalog Wave 3 (ecommerce / scm / saas / fan ~66 codes) — TASK-MONO-052 |
| 4 | #373 | 2026-05-11 | ad982e34 | B-refactor | rules/ 4-way sync audit drift fix — TASK-MONO-056 |
| 5 | #374 | 2026-05-11 | ae11597f | B-refactor | CLAUDE.md split per AGENTS.md catalog pattern (312 → 209 lines) — TASK-MONO-057 |
| 6 | #383 | 2026-05-11 | 958a7d95 | Gap A (P1+2) | lint-remediation-message-standard.md + CLAUDE.md HARDSTOP-NN stanza rewrite + ADR-MONO-006 — TASK-MONO-059 |
| 7 | #386 | 2026-05-12 | 73120990 | Gap A (P3) | `.claude/hooks/hardstop-detect.ps1` + 4-block alignment for existing hooks + 6 fixtures — TASK-MONO-060 |
| 8 | #388 | 2026-05-12 | b89bcfaa | Gap A (orphan fix) | hardstop-detect.ps1 HARDSTOP-01 false-positive fail-open — TASK-MONO-061 |
| 9 | #393 | 2026-05-12 | 43a3968e | Gap #2 | `.claude/workflows/doc-gardening.md` + 2 weekly routine registration — TASK-MONO-062 |
| 10 | #394 | 2026-05-12 | (chore) | Gap #2 close chore | TASK-MONO-062 lifecycle ready→review→done |
| 11 | #395 | 2026-05-12 | 23791ebe | Meta-policy | This ADR canonicalization PR (TASK-MONO-063 spec — ADR-MONO-003a publish + ADR-MONO-003 forward-pointer + ADR-MONO-006 § Related update + memory L28/description/MEMORY.md index canonicalize) |
| 12 | #396 | 2026-05-12 | (close chore) | Meta-policy close chore | TASK-MONO-063 lifecycle ready→done + audit-trail row backfill |
| 13 | #410 | 2026-05-13 | c29032a0 | Meta-policy | ADR-MONO-003b PROPOSED publish (TASK-MONO-069 spec — Phase 5 launch criteria / procedure / sync / rollback pre-authored; PROPOSED status, no implementation gate) |
| 14 | TBD | 2026-05-13 | 68b6877c | Phase 5 launch | ADR-MONO-003b PROPOSED → ACCEPTED transition (TASK-MONO-070 — Phase 5 launch execution). Template repo `kanggle/project-template` created (public, `is_template: true`, 435 files / 2.7 MiB). One-off category — does NOT add "Phase 5 launch" to § D1 enumeration; Phase 5 is the singular goal D4 OVERRIDE preserves, not a recurring category. Bundle includes: 2 SKILL.md placeholder fixes (verify Check 1 blocker fix, committed at 68b6877c) + 1 hook allowlist edit (`.claude/hooks/protect-main-branch.ps1` `project-template` allowlist sibling to portfolio-sync, manually applied by operator due to safety-classifier blocking AI self-modification of safety hooks). |
| 15 | #TBD-MONO-113 | 2026-05-18 | (PR-A) | New domain bootstrap | ADR-MONO-008 PROPOSED → ACCEPTED transition (TASK-MONO-113 — finance-platform bootstrap authorization). D5.1–D5.6 evaluated + § D6.1 user-explicit intent satisfied; D1 Option C (Template fork `kanggle/finance-platform` + monorepo `projects/finance-platform/` direct-include); domain `fintech` / traits `[transactional, regulated, audit-heavy]` / service_types `[rest-api, event-consumer]`. **Phase 6 first downstream Template usage** (per ADR-MONO-003b § 1.4 — first repo created via `kanggle/project-template`). Per ADR-MONO-003a § D2.1, new-domain bootstrap requires a fresh ADR — ADR-MONO-008 is that ADR; this row records its ACCEPTED transition under D4 OVERRIDE recording authority. **One-off category** — does NOT add "New domain bootstrap" to § D1 enumeration (analogous to row #14's Phase-5-launch one-off note): each new-domain bootstrap is governed by its own fresh ADR (ADR-MONO-008/009/010…), not a recurring D4 OVERRIDE category. Bootstrap artifact = PR-B / TASK-MONO-114 (separate row backfilled on PR-B merge). |

**Outstanding (OPEN — not yet landed under OVERRIDE)**:

- Gap A Phase 3b semantic Hard Stop (HARDSTOP-02/04/06/07/08) — DEFERRED per ADR-MONO-006 § 6, no entry-condition heuristic yet.
- Gap #3 worktree ephemeral observability stack — **CLOSED 2026-05-12/13** (ADR-MONO-007 publish + Phase 1/2/3 — TASK-MONO-064/065/066/067 + audit cleanup TASK-MONO-068).
- Gap #4 Chrome DevTools MCP — DEFERRED, no trigger yet.

When these land, append rows here. No re-authorisation needed (covered by D1.2).

---

## 4. Consequences

### 4.1 Immediate

- Every external reference to "D4 OVERRIDE scope" should resolve to this ADR. ADR-MONO-006 § Related is updated in this PR; memory L28 is updated to point here.
- Future task specs / PR descriptions citing D4 OVERRIDE cite `ADR-MONO-003a § D1.<category>` as the authority, not ADR-MONO-003 § 2026-05-11 update (which is now historical) or ADR-MONO-006 § 4.2 (which is gap-A-specific provenance, not scope authority).
- ADR-MONO-003 carries a forward-pointer line at the end (§ "Forward pointer (2026-05-12)") directing readers to this ADR for the canonical scope.

### 4.2 Phase 5 trigger consequences

- `scripts/verify-template-readiness.sh` execution is now diagnostic-only. It will return non-zero exit (Check 3 FAIL) as long as OVERRIDE-class work continues. This is expected and not a problem.
- Phase 5 launch will not happen automatically. User must explicitly state intent + a new `ADR-MONO-003b` (or `ADR-MONO-003c` for abandonment) must be authored.
- No standing re-evaluation cadence. The user reads this ADR + ADR-MONO-003 § 2026-05-11 update when curious about state.

### 4.3 Future-self / future-LLM-session

- A future session compacting context preserves this ADR (a single file) rather than three sources of varying granularity. Less drift risk.
- Reader landing on ADR-MONO-003 alone follows the forward-pointer to this ADR and arrives at canonical state in one hop.
- "Is this allowed under OVERRIDE?" question now has a checkable answer (read § D1 / § D2 / § D3).

### 4.4 Re-expansion of CLAUDE.md / memory surface

This ADR does not enlarge CLAUDE.md (no Hard Stop rule added, no Layer Rule changed) and does not bloat memory (L28 update is a pointer, not a duplication). Net documentation surface increase ≈ this ADR file alone (~200 LOC).

---

## 5. Alternatives Considered

### 5.1 In-place update of ADR-MONO-003 § 2026-05-11 update

Considered: rewrite the original § 2026-05-11 update block to include the gap-A + gap-#2 scope, replace memory L28 with a one-line summary pointing back. Rejected — ADR mutability principle says append-only for decision content; rewriting the original update would erase the historical trail of how scope expanded over time. The forward-pointer pattern (append a new line at the end pointing to this ADR) is the established compromise.

### 5.2 Separate canonicalization ADR (chosen)

ADR-MONO-003a sits between ADR-MONO-003 (DEFERRED) and the eventual ADR-MONO-003b (LAUNCH / ABANDONMENT). The naming preserves the decision lineage (all three concern Phase 5 launch gate) without introducing a new ADR-MONO-NNN number that would imply orthogonal scope.

### 5.3 Memory-only canonicalization

Considered: skip the ADR entirely, consolidate everything into memory `project_monorepo_template_strategy.md` § "D4 OVERRIDE scope". Rejected — memory is per-user-session state; ADRs are repo-level commitments. Decisions about what cross-project work is pre-authorised affect every contributor (even a future LLM session that doesn't load this memory), so the source of truth belongs in the repo.

### 5.4 No canonicalization (status quo)

Considered: leave the three-source state and accept the fragility. Rejected — the next "is this cleanup?" question is days away, and the cost of canonicalization (~1 hour authoring) is small relative to the cost of a scope-creep PR landing under implicit assumptions and being reverted.

---

## 6. Relationship to ADR-MONO-003

| Aspect | ADR-MONO-003 | ADR-MONO-003a (this ADR) |
|---|---|---|
| D1 (Phase 5 launch decision) | DEFERRED — unchanged | Unchanged |
| D2 (re-evaluation cadence) | Original: 30-day churn-quiet window. § 2026-05-11 update weakened to "user-explicit". | § D4 — replaced by user-explicit + ADR-MONO-003b (no timer) |
| D3 (re-evaluation gate) | Original: `verify-template-readiness.sh` exit 0 auto-promotion. § 2026-05-11 update sealed auto path. | § D4 — diagnostic-only; not a gate |
| D4 (churn freeze intent) | Original: shared paths frozen except regression fix. § 2026-05-11 update reversed for "B common rule cleanup". | § D1 / § D2 / § D3 — IN-scope categories, OUT-of-scope examples, meta-rule for additions |
| Audit trail | Not present (audit implicit in task closures). | § 3 — explicit table, append-only |
| Forward pointer | Append: "Forward pointer (2026-05-12)" → this ADR | n/a — this is the destination |

**Practical reading order for a new session**:
1. Read this ADR's § 2 (Decision) and § 3 (Audit trail) for current state.
2. Read ADR-MONO-003 § 2026-05-11 update for historical context.
3. Read ADR-MONO-006 § 3.4 / § 4.2 / § 7 only if specifically researching gap-A provenance.
4. Memory L28 is a one-line index entry; not authoritative.

---

## 7. Provenance

- ADR-MONO-003 § 2026-05-11 update — original D4 OVERRIDE introduction. Author intent: "B common-rule cleanup 한정".
- ADR-MONO-006 § 3.4 / § 4.2 / § 7 — gap-A scope-extension precedent. User-acknowledged 2026-05-12 ("D4 OVERRIDE extension to OpenAI Harness gap series … Risk acknowledged. Recorded for ADR-MONO-003a future authoring." — quoted from ADR-MONO-006 § 7).
- Memory `project_monorepo_template_strategy.md` L28 (2026-05-12 update) — mirror of the multi-doc consensus, replaced by this ADR as canonical.
- TASK-MONO-063 spec (this ADR's task) — captures the fragility argument that motivated canonicalization.

**ADR-MONO-006 § 7 forward-references this ADR explicitly** ("Recorded for ADR-MONO-003a future authoring") — the canonicalization was anticipated at the moment of scope extension. This ADR delivers what § 7 promised.

분석=Opus 4.7 / 구현=Opus 4.7 (meta-policy authoring — scope criteria phrasing requires judgement on category boundaries and audit-trail completeness).
