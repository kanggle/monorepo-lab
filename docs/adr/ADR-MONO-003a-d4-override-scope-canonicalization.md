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
| 15 | #593 | 2026-05-18 | 1c98fab6 | New domain bootstrap | ADR-MONO-008 PROPOSED → ACCEPTED transition (TASK-MONO-113 — finance-platform bootstrap authorization). D5.1–D5.6 evaluated + § D6.1 user-explicit intent satisfied; D1 Option C (Template fork `kanggle/finance-platform` + monorepo `projects/finance-platform/` direct-include); domain `fintech` / traits `[transactional, regulated, audit-heavy]` / service_types `[rest-api, event-consumer]`. **Phase 6 first downstream Template usage** (per ADR-MONO-003b § 1.4 — first repo created via `kanggle/project-template`). Per ADR-MONO-003a § D2.1, new-domain bootstrap requires a fresh ADR — ADR-MONO-008 is that ADR; this row records its ACCEPTED transition under D4 OVERRIDE recording authority. **One-off category** — does NOT add "New domain bootstrap" to § D1 enumeration (analogous to row #14's Phase-5-launch one-off note): each new-domain bootstrap is governed by its own fresh ADR (ADR-MONO-008/009/010…), not a recurring D4 OVERRIDE category. Bootstrap artifact = PR-B / TASK-MONO-114 (separate row backfilled on PR-B merge). |
| 16 | #595 | 2026-05-18 | d2b579f2 | New domain bootstrap | finance-platform bootstrap **artifact** (TASK-MONO-114, PR-B — ADR-MONO-008 § D6.2 PR-B). 6th portfolio project: `rules/domains/fintech.md` (NEW on-demand domain rule) + `.claude/config/activation-rules.md` fintech link + `projects/finance-platform/` direct-include tree (PROJECT.md `fintech`/`[transactional,regulated,audit-heavy]` + account-service skeleton + TASK-FIN-BE-001) + GAP V0017 ×2 seed (tenant `finance` + `finance-platform-internal-services-client` client_credentials, BCrypt(10) `finance-dev` independently re-verified) + monorepo wiring (settings.gradle / package.json / project-overview roster / README hub / sync-portfolio / ci.yml finance per-project filter mirroring scm). GAP Testcontainers IT pass 2m56s = V0017 authoritative. Dispatcher independently re-verified HARDSTOP-02/03 + BCrypt + V0017 byte-shape + gradle no-regression + diff scope (BE-301 discipline, agent report not trusted). **Same one-off category as row #15** (does NOT add to § D1 — ADR-MONO-008-governed). External `kanggle/finance-platform` Template-fork = user hand-off, **PENDING** (classifier-blocked); Option C standalone side recorded as not-yet-confirmed in ADR-MONO-008 § 6. |
| 17 | #613 | 2026-05-19 | f511105d | New domain bootstrap | finance-platform 외부 Template-fork **CONFIRMED** (TASK-MONO-116). ADR-MONO-008 D1 Option C standalone side 완료 — row #16 의 "**PENDING** (classifier-blocked)" 해소. 사용자 2026-05-19 셸 실행 (옵션 A: `gh repo create kanggle/finance-platform --template kanggle/project-template --public --clone` + stale remote ref prune). `gh repo view kanggle/finance-platform` 객관 검증: `templateRepository={name:project-template,owner:kanggle}`, `visibility:PUBLIC`, `isTemplate:false`, `createdAt:2026-05-19T03:42:33Z` = 진짜 Template-derived fork. **Same one-off category as #15/#16** (does NOT add to § D1 — ADR-MONO-008-governed; § 3 말미 "When these land, append rows here. No re-authorisation needed (covered by D1.2)" 가 명시 인가 → 신규 ADR 불요). 기존 row #1~#16 본문 불변 (append-only). finance v1 monorepo-side(행위-증명 chain MONO-115→FIN-BE-002→003→004) + standalone-side 양쪽 완전 종결. |
| 18 | #616 | 2026-05-19 | d189ffcc | Meta-policy | ADR-MONO-016 PROPOSED publish (TASK-MONO-117 — erp-platform bootstrap criteria / D1 integration mode / D2 classification / D3 first service / D4 procedure / D5 readiness / D6 transition pre-authored; **PROPOSED status, no implementation gate, self-ACCEPT prohibited**). Per ADR-MONO-003a § D2.1, erp new-domain bootstrap requires a fresh ADR — ADR-MONO-016 is that ADR; this row records its PROPOSED publish (criteria pre-author, NOT the bootstrap authorisation). **Same pre-author Meta-policy category as row #13** (ADR-MONO-003b PROPOSED publish) — **one-off, does NOT add to § D1**; new-domain bootstrap is governed by its own fresh ADR (ADR-MONO-008/016/…), not a recurring D4 OVERRIDE category. Identifier note: `ADR-MONO-009` = Chrome DevTools MCP (gap #4, pre-existing); erp ADR = **016** (001..015 contiguous → next free); stale "ADR-MONO-009 candidate" forward-refs corrected. ADR-016 ACCEPTED transition + actual bootstrap artifact = future task at user-explicit intent (ADR-MONO-008 → MONO-113/114 analog). 기존 row #1~#17 본문 불변 (append-only). |
| 19 | #619 | 2026-05-19 | 5beb04bc | New domain bootstrap | ADR-MONO-016 PROPOSED → ACCEPTED transition (TASK-MONO-118, PR-A doc-only — erp-platform bootstrap authorization). § D6.1 user-explicit intent `"ADR-016 ACCEPTED"` (exact form, not the excluded ambiguous form) + AskUserQuestion D1 = Option C (Both) / D3 = `masterdata-service` finalized; D5.1–D5.7 evaluated; D2 = `erp` / `[internal-system, transactional, audit-heavy]` / `[rest-api]` (frontend-app excluded per ADR-MONO-013 binding — erp backend-only, UI = platform-console parity slice). NOT self-ACCEPT — governed § D6 mechanical execution of the user's explicit intent (ADR-MONO-008/TASK-MONO-113 + ADR-MONO-013/TASK-MONO-108 precedent). Per ADR-MONO-003a § D2.1, erp new-domain bootstrap requires a fresh ADR — ADR-MONO-016 is that ADR; this row records its ACCEPTED transition under D4 OVERRIDE recording authority. **Same one-off category as row #15** (ADR-MONO-008 ACCEPTED) — **does NOT add "New domain bootstrap" to § D1**; each new-domain bootstrap is governed by its own fresh ADR (ADR-MONO-008/016/…), not a recurring D4 OVERRIDE category. Bootstrap artifact = PR-B / TASK-MONO-119 (#620, squash `9e13aabb`) — per the consolidated erp design (TASK-MONO-118/119 ACs) the PR-B # is backfilled into ADR-MONO-016 § 6's ACCEPTED row, NOT a separate § 3 row (differs from the finance #15/#16 two-row pattern; both task ACs explicitly scope out a § 3 row #20). 기존 row #1~#18 본문 불변 (append-only). |
| 20 | #625 | 2026-05-19 | 8173c75b | Hook-source hardening (D4 OVERRIDE) | TASK-MONO-120 — HARDSTOP-09 deferred-skeleton/Option-3 over-fire recognition guard in `.claude/hooks/hardstop-detect.ps1`. The path-only HARDSTOP-09 branch false-positive-fired on the rule-sanctioned deferred-skeleton bootstrap (ADR-MONO-008/016 § D6.2 PR-B: inline Option-3 citation + follow-up `tasks/ready/` `TASK-*-BE-*` owning architecture.md — exactly `platform/hardstop-rules.md` HARDSTOP-09 Remediation Option 3). Fix = fire-time 2-condition AND guard (inline citation regex on one line + follow-up task referencing `architecture.md`); suppress only when both hold, otherwise fire (**fail-closed — not a blanket bypass**; the reviewer still verifies substance, so the hook recognising a well-formed Option-3 deferral is rule *enforcement* not weakening). Provenance: erp PR-B (g)(1) over-fire; finance PR-B was the same target (objectively confirmed via `git log --diff-filter=A` — finance architecture.md first appeared at TASK-FIN-BE-001 #597, not the bootstrap PR-B). Lifecycle: spec #622 / agent-parts (fixture + run-all + lifecycle) #623 / **honest INDEX reconcile #624** (recorded the then-incomplete state — the AI edit·stage·commit of this safety hook was classifier hard-blocked **verbatim**; standing rule followed end-to-end: STOP + operator hand-off, no work-around, no fabricated `done`; the #623-merge gap was objectively detected: FF = 3 files / merged-main grep = 0 guard tokens — green-wash refused) / **operator-applied hook guard #625 squash `8173c75b`**. Completion objectively verified: merged-main guard tokens = 8, all 8 fixtures / 25 PASS lines GREEN (positive allow + negative still-fires + existing HARDSTOP-09 + canonical-body-sync byte-identical = MONO-099/100 preserved + zero regression), dispatcher independent file re-grep L247-294. **Hook-source-fix D4 OVERRIDE recording authority — sibling to rows #7 (MONO-060) / #8 (MONO-061) + MONO-096/102; one-off, does NOT add to § D1.** Numeral note: row #19's "scope out a § 3 row #20" referred specifically to the *erp PR-B artifact* (TASK-MONO-119 — correctly no erp-artifact § 3 row); THIS row #20 is a distinct, unrelated hook-hardening entry — incidental numeral coincidence, no contradiction. Rows #1–#19 byte-unchanged (append-only). |
| 21 | #628 | 2026-05-19 | e2ca7bde | New domain bootstrap | erp-platform 외부 Template-fork CONFIRMED (TASK-MONO-121 — finance row #17 analog). ADR-MONO-016 D1 Option C standalone side 완료 — § 6 2026-05-19 ACCEPTED row 의 `Both (Template fork…)` decision 의 standalone artifact 실생성 확정 (§ 6 에 동일 2026-05-19 `Option C standalone side CONFIRMED` resolution row append). Dispatcher 독립 `gh repo view kanggle/erp-platform` 객관검증: templateRepository=`kanggle/project-template`, owner `kanggle`, PUBLIC, `isTemplate:false`, `createdAt 2026-05-19T10:01:11Z` = 진짜 Template-derived fork. **Same one-off category as #15/#16/#17/#19** (does NOT add to § D1 — ADR-MONO-016-governed; finance #17 동형, reality-alignment·competing convention 무·"no re-authorisation needed" 인가). **번호 명료화 (audit-trail 내부 정합)**: row #20 = TASK-MONO-120 hook-source hardening (D4 OVERRIDE); THIS row #21 = erp fork CONFIRMED (New domain bootstrap) — 카테고리·의미 상이, 번호만 순차. row #19 의 "scope out a § 3 row #20" 는 *erp PR-B artifact* 한정이라 이미 row #20 본문이 해소했고, 본 #21 은 erp **fork-CONFIRMED** 으로 또 다른 의미 — 무관·무모순. **정직 caveat (green-wash 금지)**: 동반 stale-remote-ref-prune 사용자 명령은 경로오류로 미완 — 본 row 가 "완료"로 적지 않음 (별 user-shell 재-handoff). erp = portfolio 마지막 도메인 (mes 의도적 드롭) → 신규 도메인 부트스트랩 row 더 이상 없음. 기존 row #1~#20 본문 불변 (append-only). |
| 22 | #663 | 2026-05-20 | 1fbe017a | Meta-policy | ADR-MONO-017 PROPOSED publish (TASK-MONO-125 — `platform-console-bff` Architecture: Phase 7 Aggregation & Cross-Domain Dashboards; D1-D8 frame + CHOSEN-PROPOSED direction recorded, ACCEPTED transition deferred to separate follow-up task). Per ADR-MONO-013 § D6 Phase 7 ("console-bff fan-out + cross-domain dashboards") gate (*"5 domains live"*) satisfied 2026-05-20 by TASK-PC-FE-010 close PR #661 `eaa1de51` (Phase 6 COMPLETE; 5/5 backend domains live: GAP + wms + scm + finance + erp). ADR-MONO-013 § D5 prescribes `service_types += rest-api` *when the console-bff lands* but DEFERS the BFF's architectural *what* (D1-D8); without an ADR the implementation-time selection would silently bake architecture (HARDSTOP-09). **Same pre-author Meta-policy category as row #13** (ADR-MONO-003b PROPOSED publish) **and #18** (ADR-MONO-016 PROPOSED publish) — **one-off, does NOT add to § D1**; this is a staged-child ADR of ADR-MONO-013 (sibling to ADR-MONO-014/015 which were also published under ADR-013's authority, not via fresh § D1 categories). ADR-017 ACCEPTED transition + the `console-bff` skeleton + the first cross-domain dashboard MVP are **future tasks** (ADR-MONO-014 → TASK-BE-298 / ADR-MONO-015 → TASK-PC-FE-005 staged-execution sibling pattern). ADR-MONO-013 amended additively in this PR (§ D5 parenthetical + § History new "Additive note" — `grep -c "Additive note"` 4→5; HARDSTOP-04 D1-D8 byte-unchanged). Rows #1–#21 byte-unchanged (append-only). |
| 23 | (this PR) | 2026-05-20 | (this PR squash) | Meta-policy | ADR-MONO-017 PROPOSED → ACCEPTED transition (TASK-MONO-126 — doc-only governance flip; sibling ADR-014/MONO-110 + ADR-015/MONO-112 + ADR-016/MONO-118 staged-child ACCEPTED 패턴 정확 답습). User-explicit intent = AskUserQuestion option A selection 2026-05-20 (post `/audit-memory`, *"다음 작업 추천"* → header *"Next task"* → option A *"A — ADR-017 ACCEPTED transition (Recommended)"* + description *"이 옵션 선택 자체가 § D6.1 user-explicit intent 'ADR-017 ACCEPTED' 로 간주되어 진행됨"* — ambiguous form 아닌 명시 confirm form; sibling ADR-016 *"ADR-016 ACCEPTED"* + ADR-014/015 *"ACCEPTED 승격 + ..."* 동형). D1-D8 CHOSEN-PROPOSED direction **finalised byte-unchanged** from PROPOSED (ACCEPTED = *finalise* not re-decide; HARDSTOP-04 + sibling ADR-014/015 ACCEPTED-flip 답습; § 1 Context + § 2 Decision tables + § 3 Consequences + § 4 Alternatives + § 5 Relationship + § 7 Provenance byte-identical; flip = Status + History ACCEPTED 줄 append + § 6 PENDING placeholder → 실제 ACCEPTED row replace + § 1.3 minimal past-tense). **Same one-off Meta-policy category as row #22** (ADR-017 PROPOSED publish) **and #13/#18** (ADR-003b/016 PROPOSED publish) **and #15/#19** (ADR-008/016 ACCEPTED transition) — **does NOT add to § D1**; staged-child ADR ACCEPTED transition 은 각 fresh ADR governed §D6 mechanical 집행, recurring D4 OVERRIDE category 아님. ADR-MONO-013/014/015/016/002 byte-unchanged (PROPOSED stage amendment 가 final; ACCEPTED stage 는 자기-ADR 만 flip — sibling ADR-014/015 ACCEPTED 동형). 후속 = TASK-PC-BE-001 (`console-bff` Spring Boot skeleton) + TASK-PC-FE-011 (MVP "Operator Overview" cross-domain dashboard) — 각 platform-console project-internal future tasks, dependency-correct base = 본 row PR ACCEPTED main. Rows #1–#22 byte-unchanged (append-only 절대). |
| 24 | (this PR) | 2026-05-31 | (this PR squash) | Meta-policy | ADR-MONO-019 PROPOSED → ACCEPTED transition (TASK-MONO-153 — doc-only governance flip; sibling ADR-014/MONO-110 + ADR-015/MONO-112 + ADR-017/MONO-126 + ADR-018/MONO-138 staged-child ACCEPTED 패턴 답습). Recorded here per **ADR-MONO-019 § 3.3 step 0** explicit instruction (*"ADR-MONO-003a § 3 audit-row append"*) — the immediately-preceding ACCEPTED-transition precedent is row #23 (ADR-017/MONO-126). User-explicit intent = first-message direct request *"ADR-MONO-019 PROPOSED → ACCEPTED 승급 ... 작성·머지"* + *"진행해"* 2026-05-31 (ambiguous form 아닌 명시 confirm form; sibling ADR-017 *"ADR-017 ACCEPTED"* / ADR-018 *"ADR-018 ACCEPTED"* 동형). D1-D6 CHOSEN-PROPOSED direction **finalised byte-unchanged** from PROPOSED #953 squash `b4ec7edc` (ACCEPTED = *finalise* not re-decide; § 1 Context + § 2 Decision tables + § 3 Consequences + § 4 Alternatives + § 5 Relationship + § 7 Provenance byte-identical; flip = Status + History ACCEPTED clause append + § 1.3 minimal past-tense + § 6 ACCEPTED row append). **Same one-off Meta-policy category as row #23** (ADR-017 ACCEPTED) — **does NOT add to § D1**; staged-child ADR ACCEPTED transition 은 각 fresh ADR governed mechanical 집행, recurring D4 OVERRIDE category 아님. ADR-MONO-013 byte-unchanged (PROPOSED stage 의 § History additive note 7→8 가 final; ACCEPTED stage 는 자기-ADR ADR-019 만 flip — sibling ADR-014/015/017 ACCEPTED 동형, ADR-019 PROPOSED/MONO-152 가 § 3 row 미추가했으므로 본 row 가 ADR-019 의 단일 lifecycle 기록). 후속 = § 3.3 4-step execution roadmap UNPAUSED, step 1 (GAP backward-compatible model+catalog — `tenant_domain_subscription` + Flyway + ConsoleRegistryUseCase rewrite + 하위호환 시드, Opus) 부터 dependency-correct base = 본 row PR ACCEPTED main. Rows #1–#23 byte-unchanged (append-only 절대). |
| 25 | (this PR) | 2026-05-31 | (this PR squash) | Meta-policy | ADR-MONO-020 PROPOSED publish (TASK-MONO-156 — operator↔multi-customer assignment, D3-B / AWS IAM Identity Center "user → multiple account assignments" parity + active-tenant token scoping). ADR-MONO-019 § D3 가 D3-A single-value MVP 으로 CHOSEN 하고 Option B(N:M `operator_tenant_assignment`)를 *"Deferred, not rejected — § 3.3 step 4 extension"* 으로 미룬 축 + TASK-MONO-154 런타임 조사가 **어느 ADR 에도 미명세**로 표면화한 active-tenant 토큰 스코핑(도메인-facing OIDC 토큰 `tenant_id` 가 로그인 시 `credentials.tenant_id` 고정 → multi-assignment operator 의 세션-중 고객 전환 불가)을 6-decision(D1-D6, CHOSEN-PROPOSED)로 결정 기록. D2 = RFC8693 assume-tenant exchange(AWS STS AssumeRole analog, ADR-014 확장; assignment ∩ subscription 검증 후 selected `tenant_id`+`entitled_domains` short-lived 토큰 발급). HARDSTOP-09(미명세 축 → 새 ADR) 충족. **Same pre-author Meta-policy category as rows #13/#18/#22** (ADR-003b/016/017 PROPOSED publish) — **one-off, does NOT add to § D1**; staged-child ADR(ADR-019 의 deferred D3-B 를 실현). ADR-MONO-019 amended additively in this PR (§ D3 뒤 additive supersession note; D1-D6 body byte-unchanged, HARDSTOP-04). ADR-020 ACCEPTED transition + 구현(operator_tenant_assignment + assume-tenant exchange + console switcher)은 future tasks(sibling ADR-019 → MONO-153 ACCEPTED + 실행 staged 패턴). Rows #1–#24 byte-unchanged (append-only). |
| 26 | (this PR) | 2026-05-31 | (this PR squash) | Meta-policy | ADR-MONO-020 PROPOSED → ACCEPTED transition (TASK-MONO-157 — doc-only governance flip; sibling ADR-014/MONO-110 + ADR-015/MONO-112 + ADR-017/MONO-126 + ADR-018/MONO-138 + **ADR-019/MONO-153** staged-child ACCEPTED 패턴 답습). User-explicit intent = *"진행"* 2026-05-31 (ADR-020 PROPOSED #977 머지 후 제안한 "ADR-020 ACCEPTED 승급" next-step 선택 — sibling ADR-019 *"진행해"* 동형 same-session PROPOSED→ACCEPTED). D1-D6 CHOSEN-PROPOSED direction **finalised byte-unchanged** from PROPOSED #977 squash `3be2ba51` (ACCEPTED = *finalise* not re-decide; § 1 Context + § 2 Decision tables + § 3 Consequences + § 4 Alternatives + § 5 Relationship + § 7 Provenance byte-identical; flip = Status + History ACCEPTED clause + § 6 ACCEPTED row(+`#977` 해소) + minimal past-tense). **Same one-off Meta-policy category as row #24** (ADR-019 ACCEPTED) **and #23** (ADR-017 ACCEPTED) — **does NOT add to § D1**; staged-child ADR ACCEPTED transition 은 각 fresh ADR governed mechanical 집행. ADR-MONO-019 byte-unchanged (PROPOSED stage 의 § D3 additive supersession note 가 final; ACCEPTED stage 는 자기-ADR ADR-020 만 flip — sibling ADR-014/015/017/019 ACCEPTED 동형). 후속 = ADR-020 § 3.3 execution roadmap UNPAUSED = ADR-019 step 4 extension(D3-B) 실행 entry: operator_tenant_assignment(D1) → assume-tenant exchange(D2) → console switcher(D4) future tasks, dependency-correct base = 본 row PR ACCEPTED main. Rows #1–#25 byte-unchanged (append-only 절대). |
| 27 | (this PR) | 2026-06-02 | (this PR squash) | Meta-policy | ADR-MONO-021 PROPOSED publish (TASK-MONO-164 — `account_type` OIDC 클레임 source). `platform/contracts/jwt-standard-claims.md` L46 이 `account_type`(CONSUMER\|OPERATOR)을 **Required** 로 명시하고 ecommerce gateway `AccountTypeEnforcementFilter` 가 클레임 **부재 시 모든 인증요청 403**(`"CONSUMER".equals(null)=false`) 처리하나, GAP 토큰 파이프라인이 클레임 미발급 + **어느 spec/ADR 도 source/storage 미명세**(GAP specs/·docs/adr/·repo docs/adr/ 0 matches) → HARDSTOP-09. 5-decision(D1-D5, CHOSEN-PROPOSED): D1 = per-account, `auth_db.credentials.account_type` denormalize(`tenant_id` 패턴; tenant_type 도출 기각[ecommerce 한 테넌트에 CONSUMER+OPERATOR 공존] / client·scope 도출 기각[per-account immutable 위반]); D2 = provisioning 시 설정(signup→CONSUMER / operator-provision→OPERATOR); D3 = access+id token 주입(workload 제외, assume-tenant OPERATOR 보존); D4 = staged net-positive un-break(컬럼 DEFAULT CONSUMER + operator-row 교정 → 주입 → provisioning → e2e); D5 = userinfo 보류. **Same pre-author Meta-policy category as rows #13/#18/#22/#25** (ADR-003b/016/017/020 PROPOSED publish) — **one-off, does NOT add to § D1**; staged-child ADR(별 ACCEPTED + 구현 task, sibling ADR-019/020 패턴). 어느 기존 ADR 도 amend 안 함(claim 은 net-new, `jwt-standard-claims.md` 계약 body byte-unchanged — source 만 보충). ADR-021 ACCEPTED transition + 구현(credentials 컬럼 + customizer/provider + provisioning + e2e)은 future tasks. Rows #1–#26 byte-unchanged (append-only 절대). |
| 28 | (this PR) | 2026-06-02 | (this PR squash) | Meta-policy | ADR-MONO-021 PROPOSED → ACCEPTED transition (TASK-MONO-165 — doc-only governance flip; sibling ADR-014/MONO-110 + ADR-015/MONO-112 + ADR-017/MONO-126 + ADR-018/MONO-138 + ADR-019/MONO-153 + **ADR-020/MONO-157** staged-child ACCEPTED 패턴 답습). User-explicit intent = *"진행"* 2026-06-02 (ADR-021 PROPOSED #1006 머지 후 제안한 "ADR-021 ACCEPTED 승급" next-step 선택 — sibling ADR-020 *"진행"* 동형 same-session PROPOSED→ACCEPTED). D1-D5 CHOSEN-PROPOSED direction **finalised byte-unchanged** from PROPOSED #1006 squash `20f19c26` (ACCEPTED = *finalise* not re-decide; § 1 Context + § 2 Decision tables + § 3 Consequences + § 4 Alternatives + § 5 Relationship + § 7 Provenance byte-identical; flip = Status + History ACCEPTED clause + § 6 ACCEPTED row(+`#1006` 해소) + § 1.3/§ 3.3 minimal past-tense). **Same one-off Meta-policy category as row #26** (ADR-020 ACCEPTED) **and #24/#23** (ADR-019/017 ACCEPTED) — **does NOT add to § D1**; staged-child ADR ACCEPTED transition 은 각 fresh ADR governed mechanical 집행. 어느 기존 ADR 도 flip 안 함(ADR-021 자기-ADR 만; net-new claim 이라 amend 대상 없음). 후속 = ADR-021 § 3.3 execution roadmap UNPAUSED: credentials `account_type` 컬럼+customizer/provider 주입(D1/D3) → provisioning(D2) → INT-023 e2e 단언(D4 step3) future tasks, dependency-correct base = 본 row PR ACCEPTED main. Rows #1–#27 byte-unchanged (append-only 절대). |
| 29 | (this PR) | 2026-06-10 | (this PR squash) | Meta-policy | ADR-MONO-023 PROPOSED publish (TASK-MONO-205 — Entitlement/Subscription Plane ↔ IAM Plane Separation + subscription lifecycle state machine). ADR-MONO-019 § D2 created `tenant_domain_subscription (tenant_id, domain_key, status, …)` as the entitlement authority + a backward-compatible all-`ACTIVE` seed, but left the `status` **state set/transitions** undefined, deferred its **mutation/admin surface** as § 3.3-step-2 *"(optional)"*, and never recorded what a non-ACTIVE subscription does to the **IAM plane** (operator assignments ADR-020 + RBAC ADR-002) → HARDSTOP-09 the moment a subscription is ever suspended/cancelled/admin-mutated. 6-decision (D1-D6, CHOSEN-PROPOSED): D1 = explicit lifecycle (`ACTIVE`/`SUSPENDED`/`CANCELLED`(+`PENDING`), `SUSPENDED` reversible, billing sub-states excluded); D2 = two planes one-way dependency — entitlement change affects entitlement plane ONLY (catalog + next-token `entitled_domains` drop the domain), operator assignments + RBAC **preserved**, re-activate without re-grant (**GCP billing↔IAM parity**); D3 = account-service-owned subscription admin API gated by NEW `subscription.manage` permission **distinct from `operator.manage`** (plane separation at the authz layer; separately delegable for the future tenant-admin ① axis); D4 = live-read reflection + `tenant.subscription.changed` outbox event + short-TTL natural expiry (no eager revocation); D5 = billing OUT of scope, designed in as a future *driver* of the D1 states; D6 = staged net-zero migration (state set → admin API+permission+event → plane-separation proof IT). **Same pre-author Meta-policy category as rows #13/#18/#22/#25/#27** (ADR-003b/016/017/020/021 PROPOSED publish) — **one-off, does NOT add to § D1**; staged-child ADR (별 ACCEPTED + 구현 task, sibling ADR-019/020/021 패턴). ADR-MONO-019 amended additively in this PR (§ D2 뒤 additive extension note; D1-D6 body byte-unchanged, HARDSTOP-04). ADR-020/021 byte-unchanged (orthogonal). ADR-023 ACCEPTED transition + 구현(state set / admin API+permission+event / plane-separation proof IT)은 future tasks. Rows #1–#28 byte-unchanged (append-only 절대). |
| 30 | (this PR) | 2026-06-10 | (this PR squash) | Meta-policy | ADR-MONO-023 PROPOSED → ACCEPTED transition (TASK-MONO-206 — doc-only governance flip; sibling ADR-014/MONO-110 + ADR-015/MONO-112 + ADR-017/MONO-126 + ADR-018/MONO-138 + ADR-019/MONO-153 + ADR-020/MONO-157 + **ADR-021/MONO-165** staged-child ACCEPTED 패턴 답습). User-explicit intent = *"권장 순서대로 진행"* 2026-06-10 (ADR-023 PROPOSED #1237 머지 후 제안한 A→B 권장순서 선택 — sibling ADR-021 *"진행"* 동형 same-session PROPOSED→ACCEPTED). D1-D6 CHOSEN-PROPOSED direction **finalised byte-unchanged** from PROPOSED #1237 squash `c4a30422` (ACCEPTED = *finalise* not re-decide; § 1 Context + § 2 Decision tables + § 3 Consequences + § 4 Alternatives + § 5 Relationship + § 7 Provenance byte-identical; flip = Status + History ACCEPTED clause + § 6 ACCEPTED row(+`#1237` 해소) + § 1.3 minimal past-tense). **Same one-off Meta-policy category as row #28** (ADR-021 ACCEPTED) — **does NOT add to § D1**; staged-child ADR ACCEPTED transition 은 각 fresh ADR governed mechanical 집행. ADR-MONO-019 byte-unchanged (PROPOSED stage 의 § D2 additive note 가 final; ACCEPTED stage 는 자기-ADR ADR-023 만 flip — sibling ADR-019/020/021 ACCEPTED 동형). 본 PR 은 TASK-MONO-205 close chore(ready→done, #1237 3-dim verified)도 동반. 후속 = ADR-023 § 3.3 execution roadmap UNPAUSED: subscription `status` state set(D1) → admin API+`subscription.manage`+outbox event(D3/D4) → plane-separation proof IT(D6 step3) future tasks, dependency-correct base = 본 row PR ACCEPTED main. Rows #1–#29 byte-unchanged (append-only 절대). |
| 31 | (this PR) | 2026-06-10 | (this PR squash) | Meta-policy | ADR-MONO-024 PROPOSED publish (TASK-MONO-208 — Tenant-Admin Delegation; the "①" tenant-admin delegation axis ADR-023 repeatedly foreshadowed). admin-service 의 operator-management 권한(`operator.manage`)이 platform-central `SUPER_ADMIN` 에만 시드(rbac.md Seed Matrix) → 고객이 자기 테넌트의 operator 를 자가관리 불가 + `operator_tenant_assignment` **assign/unassign 표면 부재**(SQL 시드만, org-scope 만 기존 row 수정) + 평가 알고리즘에 **target-tenant 확정 없음**(operator.manage 가 platform-wide all-or-nothing) → "tenant-admin 이 자기 사람 관리" 구현 시 권한위임·확정·에스컬레이션 모델이 묵시적으로 baked = HARDSTOP-09. 7-decision(D1-D7, CHOSEN-PROPOSED): D1 = 신규 `TENANT_ADMIN` 시드 role(`operator.manage` 보유, 부여 row 의 `admin_operator_roles.tenant_id` 로 tenant-scope; `'*'`=platform, SUPER_ADMIN net-zero); D2 = 평가기 중앙 target-tenant 확정(`target ∈ effective admin-scope`, `'*'`=all, request-time DB, no claim) — crux; D3 = 신규 assign/unassign `operator_tenant_assignment` 표면 + grant-menu 확정(자기≤권한·platform role·`TENANT_ADMIN` self-grant 금지 = 에스컬레이션 방지); D4 = SUPER_ADMIN 만 TENANT_ADMIN 부여, v1 sub-delegation 없음(B deferred); D5 = tenant-admin 은 `subscription.manage` **미포함**(entitlement/billing platform-controlled; ADR-023 separability 를 v1 제외로 소비); D6 = 토큰 claim 없음, admin-service-local request-time 해석, assume-tenant 파이프라인 불변(net-zero); D7 = staged net-zero migration(confinement net-zero[SUPER_ADMIN `'*'` 전부통과] → role+surface+menu → delegation proof e2e). **Same pre-author Meta-policy category as rows #13/#18/#22/#25/#27/#29** (ADR-003b/016/017/020/021/023 PROPOSED publish) — **one-off, does NOT add to § D1**; staged-child ADR(별 ACCEPTED + 구현 task, sibling ADR-019/020/021/023 패턴). ADR-MONO-020 amended additively in this PR (§ D1 뒤 additive note; D1-D6 body byte-unchanged, HARDSTOP-04). ADR-019/021/023 byte-unchanged(orthogonal/sibling; ADR-023 의 `subscription.manage` separability 는 *소비*만, 수정 안 함). ADR-024 ACCEPTED transition + 구현(confinement / role+assign surface+menu / delegation proof)은 future tasks. Rows #1–#30 byte-unchanged (append-only 절대). |
| 32 | (this PR) | 2026-06-10 | (this PR squash) | Meta-policy | ADR-MONO-024 PROPOSED → ACCEPTED transition (TASK-MONO-209 — doc-only governance flip; sibling ADR-019/MONO-153 + ADR-020/MONO-157 + ADR-021/MONO-165 + **ADR-023/MONO-206** staged-child ACCEPTED 패턴). **단, 순수 byte-unchanged finalise 가 아니라 ACCEPTED 게이트에서 사용자 지시 2개 조정 반영**: User-explicit intent = *"D4/D5 조정 후 ACCEPTED"* → *"예 — 자기 테넌트 내 sub-delegation 허용 (D4-B)"* + *"별도 TENANT_BILLING_ADMIN role"* 2026-06-10. **D4 A→B**(in-tenant sub-delegation 허용 — `TENANT_ADMIN` 이 신규 `tenant.admin.delegate` 권한 보유 시 자기 테넌트 한정 `TENANT_ADMIN` 임명 가능; D2 confinement 로 cross-tenant 구조적 불가) + **D5 → C**(entitlement 셀프서비스를 별도 `TENANT_BILLING_ADMIN` role[`subscription.manage`]로 — `TENANT_ADMIN` 에 번들 안 함, role 레벨 plane 분리 유지, D2 confinement 가 ADR-023 구독 admin 표면까지 확장). D1-D3/D6/D7 direction 불변(조정이 닿는 mechanics 만 갱신). flip = Status + Date History ACCEPTED clause + § 1.3 unpause + § 2 intro + D4/D5 tables + D1/D3 mechanics + § 3.1/3.2/3.3 + § 4/§ 5 + § 6 ACCEPTED row(+`#1250` 해소). **Same one-off Meta-policy category as row #30** (ADR-023 ACCEPTED) — **does NOT add to § D1**; staged-child ADR ACCEPTED 은 각 fresh ADR governed mechanical 집행. ADR-MONO-020 byte-unchanged (PROPOSED stage 의 § D1 additive note 가 final; ACCEPTED stage 는 자기-ADR ADR-024 만 flip). ADR-019/021/023 byte-unchanged. 후속 = ADR-024 § 3.3 execution roadmap UNPAUSED: D2 confinement(net-zero) → `TENANT_ADMIN`+`TENANT_BILLING_ADMIN`+`tenant.admin.delegate`+assign/unassign 표면+grant-menu → delegation proof e2e, dependency-correct base = 본 row PR ACCEPTED main. Rows #1–#31 byte-unchanged (append-only 절대). |
| 33 | (this PR) | 2026-06-14 | (this PR squash) | Meta-policy | ADR-MONO-032 PROPOSED publish (TASK-MONO-253 — Unified identity model: single account → roles set; remove the `account_type` CONSUMER/OPERATOR xor partition). `jwt-standard-claims.md` L21 이 *"A single account cannot hold both account types … must provision separate accounts"* 으로 한 사람의 customer+operator 겸직을 구조적으로 금지 + L85-92 no-cross-type-SSO → 사용자가 Google식 통합 identity 모델(한 identity + role-binding set, account_type 파티션 부재)로의 전환을 명시 요청(2026-06-14, CONSUMER/OPERATOR walk-through + AWS-vs-GCP 비교 + AskUserQuestion D1="roles 집합 클레임"/option A). 통합 모델 mechanics 를 구현 시 baking → HARDSTOP-09 + `jwt-standard-claims.md` § Change Rule(contract-first) 충족 위해 결정 기록. 6-decision(D1-D6, CHOSEN-PROPOSED): D1 = `account_type` 제거, 기존 `roles` set 이 유일 authz 축(Google "one identity + role bindings"; multi-value type 기각[redundant axis] / single-active-role-switch 기각[AWS AssumeRole shape, not Google]); D2 = aud-scoped `roles` 유지·`account_type` drop·SSO role/assignment-scoped(cross-platform flatten 기각); D3 = gateway role-based admission 이 type 파티션 대체(ecommerce path→role, `X-Account-Type` drop); D4 = isolation 은 aud-scope+role-presence 로 보존(person-level type 아님; RBAC/ABAC/conditions 불변, step-up "operator mode" deferred); D5 = backward-compatible staged(contract-first → dual-read gateways → roles-only issuance → account unify[opt-in link] → drop legacy → e2e; dual-read 로 zero mis-auth window); D6 = one account=one credential=role-grant set, provisioning 은 grant 추가, 기존 pair force-merge 안 함(opt-in). **Supersedes ADR-MONO-021**(ACCEPTED — account_type claim source; 본 ADR 이 claim 의 존재이유 제거; ACCEPTED 시점에 supersede 발효, PROPOSED 는 ADR-021 flip 안 함). `jwt-standard-claims.md` **breaking amend**(contract body 는 본 PROPOSED 에서 불변 — § Change Rule 상 rewrite 는 *구현* 선행이라 post-ACCEPTED 실행 step D5 step0). ADR-002 RBAC lineage + ADR-020/024/025/026 role-grant substrate 와 reconcile(그 set 을 sole top-level 축으로). **Same pre-author Meta-policy category as rows #13/#18/#22/#25/#27/#29** (ADR-003b/016/017/020/021/023 PROPOSED publish) — **one-off, does NOT add to § D1**; staged-child ADR(별 ACCEPTED + 구현 task, sibling ADR-019/020/021/023/024 패턴). ADR-032 ACCEPTED transition + 구현(contract rewrite / dual-read gateways / roles-only issuance / account unify / drop legacy / e2e)은 future tasks, self-ACCEPT 금지. Rows #1–#32 byte-unchanged (append-only 절대). |
| 34 | (this PR) | 2026-06-14 | (this PR squash) | Meta-policy | ADR-MONO-032 PROPOSED → ACCEPTED transition (TASK-MONO-254 — doc-only governance flip; sibling ADR-019/MONO-153 + ADR-020/MONO-157 + ADR-021/MONO-165 + ADR-023/MONO-206 + **ADR-024/MONO-209** staged-child ACCEPTED 패턴). User-explicit intent = *"추천대로 진행해줘"* 2026-06-14 (ADR-032 PROPOSED #1513 머지 후 제안한 "ADR-032 ACCEPTED 승급" next-step 선택 — sibling ADR-021 *"진행"* 동형 same-session PROPOSED→ACCEPTED). D1-D6 CHOSEN-PROPOSED direction **finalised byte-unchanged** from PROPOSED #1513 squash `82cb08c0` (ACCEPTED = *finalise* not re-decide; § 1 Context + § 2 Decision tables + § 3 Consequences + § 4 Alternatives + § 5 Relationship + § 7 Provenance byte-identical; flip = Status + History ACCEPTED clause + § 6 ACCEPTED row(+`#1513` 해소) + § 3.3 PAUSED→UNPAUSED). **단, 자기-ADR flip 외에 ADR-MONO-021 supersession 발효도 동반**: ADR-021 Status `ACCEPTED` → `SUPERSEDED by ADR-MONO-032` + History SUPERSEDED clause(ADR-021 § 1-7 body byte-unchanged — supersession 은 Status+History note 한정, now-removed claim 의 source 재결정 아님). **Same one-off Meta-policy category as row #32** (ADR-024 ACCEPTED) — **does NOT add to § D1**; staged-child ADR ACCEPTED transition 은 각 fresh ADR governed mechanical 집행. ADR-002/020/024/025/026 byte-unchanged (reconcile 대상이나 본 flip 에서 미수정 — ADR-032 가 그 role-grant substrate 를 sole top-level 축으로 *소비*만). 후속 = ADR-032 § 3.3 6-step execution roadmap UNPAUSED: contract-first `jwt-standard-claims.md` rewrite(D5 step0, breaking — § Change Rule 상 구현 선행) → dual-read gateways(5 gateway) → roles-only issuance → account/credential unify(opt-in) → drop legacy `account_type` → e2e, dependency-correct base = 본 row PR ACCEPTED main. Rows #1–#33 byte-unchanged (append-only 절대). |

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
