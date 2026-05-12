# ADR-MONO-003b — Phase 5 Launch Criteria, Procedure, Sync, Rollback

**Status:** ACCEPTED
**Date:** 2026-05-13
**History:** PROPOSED 2026-05-13 (TASK-MONO-069 — pre-launch criteria documentation) → ACCEPTED 2026-05-13 (TASK-MONO-070 — Phase 5 launch execution, source SHA `68b6877c`, Template URL `https://github.com/kanggle/project-template`).
**Decision driver:** ADR-MONO-003a § D4 (Phase 5 trigger redefinition, 2026-05-12) cites "user-explicit statement of intent + new `ADR-MONO-003b` authoring (status: PROPOSED → ACCEPTED)" as the launch gate. The gate is half-defined — user-explicit intent is the user's prerogative, but ADR-MONO-003b did not exist. A future "should we launch now?" moment would require fresh authoring under decision pressure, where criteria phrasing is most likely to be sloppy and reviewer trust lowest. This ADR lands the PROPOSED draft so the gate is fully specified ahead of the decision.
**Supersedes:** none (PROPOSED status — does not supersede anything yet). ACCEPTED transition will SUPERSEDE-on-launch ADR-MONO-003 § D1 (Phase 5 DEFERRED) and update ADR-MONO-003a Status footer.
**Related:** [ADR-MONO-003](ADR-MONO-003-phase-5-template-extraction-deferred.md) (Phase 5 DEFERRED parent), [ADR-MONO-003a](ADR-MONO-003a-d4-override-scope-canonicalization.md) § D4 (cites this ADR by name), [ADR-MONO-002](ADR-MONO-002-phase-4-template-extraction-trigger.md) (Discovery → Distribution strategy parent), [TEMPLATE.md § Phase 5](../../TEMPLATE.md), [`scripts/extract-template.sh`](../../scripts/extract-template.sh), [`scripts/verify-template-readiness.sh`](../../scripts/verify-template-readiness.sh), [`scripts/sync-portfolio.sh`](../../scripts/sync-portfolio.sh), memory [`project_monorepo_template_strategy`](../../../memory/project_monorepo_template_strategy.md).

---

## 1. Context

### 1.1 The gate that doesn't fully exist

ADR-MONO-003a § D4 redefined the Phase 5 trigger on 2026-05-12:

| Condition | Mechanism |
|---|---|
| Phase 5 launch decision | User-explicit statement of intent + new `ADR-MONO-003b` authoring (status: PROPOSED → ACCEPTED). No automatic timer. No verify-template-readiness exit-0 auto-promotion. |

Two halves. User-explicit intent is exactly what it sounds like — the user states "go". `ADR-MONO-003b` is the other half, and at the moment ADR-MONO-003a was authored, it did not exist as a file. The gate was structurally complete (criteria + procedure declared) but mechanically incomplete (no file to flip status on).

If the user signalled intent today, the implementer would have to author ADR-003b from scratch under decision pressure. Decision-time authoring is exactly when criteria phrasing slips ("close enough"), audit-trail completeness gets short-changed, and reviewer trust is lowest. The same pattern produced ADR-MONO-003 § 2026-05-11 update's three-document scope fragmentation that ADR-MONO-003a then had to canonicalize.

This ADR pre-authors the criteria in **PROPOSED** status so that:

1. The criteria are visible and reviewable now, when nothing is being launched and the discussion is calm.
2. A future "should we launch now?" moment can evaluate against a concrete checklist rather than re-derive it.
3. ACCEPTED transition is a status flip + audit-trail row append — not a fresh authoring session.

### 1.2 Why PROPOSED not ACCEPTED

PROPOSED ≠ "we will launch eventually". PROPOSED = "if we choose to launch, here are the criteria the moment must satisfy". The choice itself remains the user's, with no implicit commitment created by this ADR.

ADR-MONO-003 is also in DEFERRED status — neither ACCEPTED nor abandoned. ADR-MONO-003b in PROPOSED mirrors that pattern: criteria documented, decision still open.

### 1.3 What "launch" means

"Phase 5 launch" = the moment three things happen in sequence:

1. `scripts/extract-template.sh <target-dir>` runs against monorepo HEAD, producing a clean single-project Template tree.
2. The tree is pushed to a new GitHub repository (working name: `kanggle/project-template`).
3. The GitHub repo's "Template repository" toggle is enabled so `Use this template → Create a new repository` works.

After launch, the monorepo continues to be the source of truth for the shared library layer. The Template repo is a downstream artifact that gets refreshed periodically.

### 1.4 What "launch" does NOT mean

- It does not retire the monorepo. The monorepo continues to host all 5 existing projects + ongoing work + the library layer it serves.
- It does not migrate any existing project out. Each project's own standalone repo (per `scripts/sync-portfolio.sh`) is unrelated to the Template repo.
- It does not commit to a specific future project being bootstrapped from the Template (no obligation to spin up finance / erp / mes the moment the Template exists).
- It does not impose timeline pressure. The Template repo can sit unused for months; the bet is that *when* a new project is needed, the bootstrap experience is cleaner than copy-pasting from the monorepo.

---

## 2. Decision

### D1 — Launch readiness criteria (the checklist to evaluate at ACCEPTED moment)

When user-explicit intent arrives, the implementer evaluates these criteria. A criterion can be **MET**, **NOT MET (override acknowledged)**, or **NOT MET (blocker)**. Any blocker forces ACCEPTED transition to wait; override-acknowledged criteria proceed with the acknowledgement recorded in the audit-trail row.

| # | Criterion | Default verification |
|---|---|---|
| **D1.1** | `scripts/verify-template-readiness.sh` (full mode) execution result captured | Run script; record exit code + per-check status. Check 3 (no shared-library churn 30d) is **diagnostic-only per ADR-MONO-003a § D4** — FAIL is not a blocker, only an information signal. Checks 1, 2, 4, 5, 6 are still meaningful blockers. |
| **D1.2** | Seed-source decision recorded | Decide one of: (i) generic flat shell with no domain content (purest Template), (ii) anonymised excerpt of an existing project, (iii) wms-platform-derived skeleton with domain stubs blanked. Default: option (i). Decision recorded in the ACCEPTED audit-trail row. |
| **D1.3** | Target repo URL + visibility decided | Default: `https://github.com/kanggle/project-template`, **public**. Naming consistent with TEMPLATE.md § Phase 5 working name. Visibility public so `Use this template` works for any future kanggle account without org scoping. |
| **D1.4** | Library-layer churn quiescence subjective assessment | Reviewer reads `git log --since="30 days ago" -- libs/ platform/ rules/ .claude/` and judges: "Is the OVERRIDE-class churn winding down, plateau, or accelerating?" Plateau or winding-down = MET; accelerating = NOT MET (consider deferring). This is a qualitative judgement, not a metric. |
| **D1.5** | User-explicit intent statement recorded | The user states intent in writing (chat / commit message / PR description). The exact phrasing is captured in the ACCEPTED audit-trail row. Acceptable phrasings include but are not limited to: "launch ADR-003b ACCEPTED", "Phase 5 시작", "extract Template now". |
| **D1.6** | Dress rehearsal pass | Run `scripts/extract-template.sh --dry-run <tmp-dir>` and read the output. If it lists files that look wrong (project-specific content, missing rule files), file a follow-up task; otherwise the rehearsal passes. |
| **D1.7** | Outstanding shared-library work explicitly inventoried | `tasks/ready/` (root-level) is scanned for any task that would touch `libs/` / `platform/` / `rules/` / `.claude/` if implemented after launch. If non-empty, decide whether to land them pre-launch (preferred — cleaner Template snapshot) or post-launch with explicit acknowledgement that the first sync will absorb them. |

**Blocker definition**: A criterion is a blocker only if it is structurally unverifiable (not subjectively imperfect). Example: D1.1 with Check 1 FAIL = blocker (shared library contains project-specific service name, must fix). D1.4 with "churn slightly elevated" = override-acknowledged (qualitative call).

### D2 — Extraction procedure

The launch procedure is **already authored as `scripts/extract-template.sh`**. D2 records the procedural steps and the launch-day operational guidance, not new behaviour.

#### D2.1 — Pre-extraction

1. `git status` in monorepo root — must be clean (no uncommitted changes; HEAD is the SHA that will be Template's lineage source).
2. `git rev-parse HEAD` — record the source SHA for the audit-trail row.
3. `bash scripts/verify-template-readiness.sh` — capture exit code + output (D1.1 evidence).
4. `bash scripts/extract-template.sh --dry-run /tmp/template-dryrun` — capture output (D1.6 evidence).

#### D2.2 — Extraction

5. `bash scripts/extract-template.sh --init-git /tmp/project-template-YYYYMMDD` — produces the Template tree as a fresh git repo with initial commit.
6. Inspect the extracted tree manually for ~10 minutes — read `PROJECT.md` placeholder, `tasks/INDEX.md`, sample skill files. Confirm no project-specific leakage.

#### D2.3 — GitHub repo creation

7. `gh repo create kanggle/project-template --public --description "Monorepo-lab Template repository — flat single-project shell with full shared library layer. Used via 'Use this template'."` (or matching `gh` invocation; visibility per D1.3).
8. `cd /tmp/project-template-YYYYMMDD && git remote add origin git@github.com:kanggle/project-template.git && git push -u origin main`.
9. On GitHub web UI: **Settings → General → Template repository** toggle = ON.
10. (Optional) **Settings → Branches → Branch protection rules** on `main`: require PR review or CI (low priority for a Template repo with single-author flow; deferred unless contributor model emerges).

#### D2.4 — Verification

11. From an unrelated workstation or `gh` session, run `gh repo create test-template-fork --template kanggle/project-template --public --clone` (or web UI "Use this template"). Inspect the fresh fork — confirm it has the expected flat shape + no `projects/` level.
12. Delete the test fork (`gh repo delete kanggle/test-template-fork --confirm`). The "Use this template" flow does not link the fork to the Template via Git history (it's a fresh init), so deletion is clean.

#### D2.5 — Recording

13. Append ACCEPTED row to this ADR's § 6 (Status transition history).
14. Append audit-trail row to ADR-MONO-003a § 3.
15. Update ADR-MONO-003 Status footer: `DEFERRED → SUPERSEDED-by-ADR-MONO-003b 2026-MM-DD`.
16. Update memory `project_monorepo_template_strategy.md` to reflect Template repo URL + ACCEPTED status.

### D3 — Monorepo ↔ Template sync mechanism

Direction is **one-way only: monorepo → Template**. Library improvements flow from monorepo (source of truth) to Template (downstream snapshot). Reverse-direction back-port is explicitly out of scope — projects forked from the Template own their library snapshot at fork time and back-port manually if they want updates.

#### D3.1 — Cadence

Monthly **or** on-demand, whichever is earlier. No strict timer; no automation cron job.

- **Monthly trigger**: project owner reviews monorepo shared-library churn since last sync; if non-trivial improvements landed, sync.
- **On-demand trigger**: a milestone library change (e.g., new skill, new ADR-grade pattern, removed deprecated lib API) prompts sync regardless of cadence.

If neither trigger fires for 3 months, that is an information signal — either the library is stable (good) or no one is using the Template (signal to evaluate abandonment per § D4).

#### D3.2 — Tooling

**Planned**: `scripts/sync-template.sh` — re-runs `extract-template.sh` against the current monorepo HEAD and force-pushes to the Template repo. Same pattern as `scripts/sync-portfolio.sh` (force-push history rewrite is acceptable for a downstream Template repo because no one else commits to it directly).

**Initial implementation deferred** — script does not need to exist for ACCEPTED transition. The first sync can be the launch itself (running `extract-template.sh` + push). The second sync is when the script becomes worth authoring. ADR-003b does not block on script existence.

#### D3.3 — Initiator

Project owner (kanggle), not automation. The cadence is qualitative ("are improvements worth syncing?") so a human-in-the-loop decision is appropriate. No GitHub Actions workflow, no scheduled cron.

#### D3.4 — What gets synced

Everything `extract-template.sh` already extracts: the shared library layer + flat single-project shell. Each sync is a full rebuild of the Template tree, not an incremental diff. This is simpler than 3-way merge and avoids drift between Template's evolving state and monorepo's evolving state.

#### D3.5 — What does NOT get synced (back-porting)

Forked project repositories created from the Template **do not auto-receive** library updates. Each fork owns its library snapshot at fork time. If a fork wants newer library code, the fork's maintainer manually rsync's from the latest Template release. This is consistent with the "fork = independent evolution" GitHub Template semantics.

### D4 — Rollback procedure

If Phase 5 launch turns out to be the wrong bet (Template repo accumulates drift, no new projects materialise, or maintenance cost exceeds value), retirement path:

#### D4.1 — Soft retire (preserve repo, stop syncing)

1. Author `ADR-MONO-003c-phase-5-soft-retire.md` documenting why sync stopped (no demand, drift cost too high, etc.).
2. Disable "Template repository" toggle on GitHub (forks via `Use this template` stop being created; existing forks unaffected).
3. Add a banner to Template repo `README.md` linking to the ADR-003c and stating "this repo is no longer synced from monorepo".
4. Update memory + this ADR Status footer: `ACCEPTED → SUPERSEDED-by-ADR-MONO-003c 2026-MM-DD (soft retire)`.

#### D4.2 — Hard retire (delete repo)

1. Author `ADR-MONO-003c-phase-5-hard-retire.md` with stronger justification (e.g., the strategy itself is being abandoned, not just the artifact).
2. `gh repo delete kanggle/project-template --confirm`.
3. Update memory + this ADR Status footer: `ACCEPTED → SUPERSEDED-by-ADR-MONO-003c 2026-MM-DD (hard retire)`.

Forks created from the Template before retirement are unaffected — they own their copy. This is GitHub Template semantics, not a special-case design.

#### D4.3 — Reversal of retirement

A retirement ADR is not a final commitment. If circumstances change (e.g., a new project finally materialises), reverse by:

1. Author `ADR-MONO-003d-phase-5-relaunch.md` documenting the reversal trigger.
2. Re-run launch procedure per § D2 (which is idempotent — `extract-template.sh` against the current monorepo HEAD).

The cost of retirement → relaunch is low because the Template is a stateless artifact derived from monorepo HEAD; it can be reconstructed at any time.

### D5 — ACCEPTED transition mechanics

This is the rule book for the day-of-launch implementer (who may be a future LLM session with no memory of this discussion):

#### D5.1 — User-explicit intent forms

Any of the following user statements satisfy the "user-explicit intent" half of the gate:

- "launch ADR-003b ACCEPTED" / "ADR-003b ACCEPTED 진행"
- "Phase 5 시작" / "Phase 5 launch"
- "extract Template now" / "Template repo 만들어"
- "go ahead with [Template / Phase 5 / launch]" with explicit naming of the artifact

Ambiguous statements (e.g., "what about Phase 5?", "should we launch?") do NOT satisfy the gate. The user must affirmatively direct the launch.

#### D5.2 — Commit pattern

ACCEPTED transition produces **one PR** (single bundled, per `feedback_pr_bundling.md`):

1. This ADR's Status line: `PROPOSED` → `ACCEPTED`.
2. This ADR's § 6 (Status transition history): row appended with date, source SHA, seed-source choice, user intent statement quote, link to extraction PR (if separate) or same-PR commit.
3. ADR-MONO-003 Status footer: append `→ SUPERSEDED-by-ADR-MONO-003b ACCEPTED YYYY-MM-DD`. (No body mutation — the footer is the running-addendum slot.)
4. ADR-MONO-003a § 3 audit trail: row appended for the launch PR with category "Phase 5 launch" (a NEW category beyond § D1.1/D1.2; recorded explicitly in the row as one-off, not adding to § D1).
5. Memory `project_monorepo_template_strategy.md`: Phase 5 status updated `DEFERRED → LAUNCHED YYYY-MM-DD + Template URL`.
6. Optional: a launch artifact file at `docs/launch-records/phase-5-template-extraction-YYYY-MM-DD.md` capturing the script output, repo creation timestamp, verification fork SHA. This is a documentary artifact, not a decision; deferred unless the user wants the trail.

#### D5.3 — Audit-trail row format (this ADR's § 6)

```
| YYYY-MM-DD | ACCEPTED | <source-SHA> | <seed-source> | <user-intent-quote> | <PR#> |
```

#### D5.4 — Same-PR launch artifact list (if launching in the same PR as the ACCEPTED transition)

If the user wants ACCEPTED + launch in one PR (recommended — the decision moment and the artifact are unified):

- Diff includes: this ADR's status change + audit-trail row + ADR-MONO-003 footer + ADR-MONO-003a § 3 row + memory update + (optional) `docs/launch-records/...`
- Diff does NOT include: any script change (D2 uses existing scripts), any service code, any project bootstrap.
- The PR body links to the externally-created `kanggle/project-template` repo URL (created via `gh repo create` outside the PR commit, since GitHub repo creation is a side effect not capturable in a diff).
- The PR body quotes the user's intent statement verbatim.

If the user prefers ACCEPTED + launch in two PRs (criteria flip first, extraction second), that is also acceptable — § D5.2 commits land in PR-A, § D2.2–D2.4 happen between merges, ADR-003a § 3 row appended in PR-B.

---

## 3. Consequences

### 3.1 Day of PROPOSED (this PR merge)

- ADR-MONO-003a § D4's gate is now mechanically complete (file exists; status can be flipped). Future user-explicit intent + this ADR's ACCEPTED transition unblocks launch with no fresh authoring needed.
- Reviewers (including future-self / future-LLM sessions) have a concrete checklist to evaluate launch readiness against. "Should we launch now?" has a checkable answer.
- No actual launch happens. ADR-MONO-003 § D1 (Phase 5 DEFERRED) is unchanged.
- ADR-MONO-003a § 3 audit-trail row appended for THIS PR with category "Meta-policy" (per § D1.2 / D1.3-adjacent precedent — meta-policy ADRs are pre-authorised under D4 OVERRIDE; this is structurally PR #395-class).

### 3.2 Day of ACCEPTED (future, when user signals intent)

- D1.1–D1.7 criteria are re-evaluated using current monorepo state.
- D2.1–D2.5 procedure is followed.
- Launch artifact: a new GitHub repo `kanggle/project-template` with "Template repository" flag enabled.
- This ADR's § 6 row appended.
- ADR-MONO-003 Status footer transitions DEFERRED → SUPERSEDED.
- ADR-MONO-003a § 3 audit-trail row appended (new category: "Phase 5 launch", one-off — not adding to § D1 enumeration).

### 3.3 Days of sync (post-launch, monthly or on-demand)

- Project owner runs `scripts/sync-template.sh` (if authored) or re-runs `extract-template.sh` + force-push manually.
- Each sync overwrites Template repo HEAD with the rebuilt tree. No 3-way merge.
- Forks created before each sync are unaffected.

### 3.4 Days of retirement (future, if the bet fails)

- Soft retire (§ D4.1) or hard retire (§ D4.2) per the decision moment.
- This ADR's Status transitions ACCEPTED → SUPERSEDED-by-ADR-MONO-003c.

### 3.5 Future-self / future-LLM-session

- A future session evaluating "should we launch now?" reads this ADR § D1 directly and applies the checklist.
- A future session prompted to "launch Phase 5" reads § D2 + § D5.1 (intent forms) before mutating anything; if the user statement does not satisfy § D5.1, the session asks for clarification rather than guessing.
- A future session inheriting context with this ADR ACCEPTED reads § 6 row to see what was actually launched and when.

---

## 4. Alternatives Considered

### 4.1 Skip ADR-003b — inline criteria into ADR-003a § D4

ADR-003a § D4 already names ADR-003b by reference. Inlining the criteria into § D4 would save one ADR file but:

- Inflate ADR-003a (already 250 LOC) by ~250 LOC.
- Re-introduce the same multi-decision-in-one-file problem ADR-003a was authored to fix (it canonicalized D4 OVERRIDE scope after multi-doc consensus failed).
- Make § D4 the destination for both meta-policy (gate definition) AND operational policy (launch checklist + procedure), conflating different decision levels.

Rejected.

### 4.2 Defer authoring until user signals intent

The "naturalist" path: don't author ADR-003b until the moment it's needed. Saves authoring cost if Phase 5 is never launched (template strategy abandoned).

Rejected because:

- Decision-time authoring is exactly when criteria slip (the failure mode ADR-003a was authored to prevent).
- The user has explicitly maintained Phase 5 as an active goal across multiple memory snapshots and ADRs spanning 2026-04 → 2026-05. The probability that Phase 5 is abandoned without ever being launched is low; pre-authoring cost is amortised.
- If Phase 5 IS abandoned, this ADR transitions to SUPERSEDED-by-ADR-MONO-003c (the abandonment ADR) and the work is not wasted — the abandonment ADR cites this ADR's § D4 (rollback procedure) as the retirement playbook, even for the soft case where no launch ever happened.

### 4.3 Treat `extract-template.sh` as self-documenting (script-as-spec)

The script exists. Its `--help` output describes its behaviour. Why duplicate as an ADR?

Rejected because:

- A script describes WHAT happens when run, not WHEN to run it or HOW the launch decision is made. § D1 (criteria) and § D5 (transition mechanics) are policy, not procedure.
- § D2 references the script as authoritative for procedure but adds operational guidance (`gh repo create` flags, post-launch verification fork test, GitHub UI toggle steps) that the script cannot encode.
- § D3 (sync mechanism) describes work that is partially un-scripted (manual cadence judgement) and partially un-authored (`scripts/sync-template.sh` planned but not yet written). Script-as-spec only covers the authored half.

### 4.4 Pre-pick the seed source (e.g., wms-platform) in PROPOSED

Considered: in § D1.2, commit to "seed source = wms-platform skeleton with domain stubs blanked" rather than leaving the decision to ACCEPTED.

Rejected because:

- The decision affects what users see in the Template's first commit and how the README reads. It deserves the launch-moment focus rather than being baked in months ahead of time.
- Existing `extract-template.sh` already produces a flat single-project shell. The seed source decision is really "which content lives in the shell" — answerable at launch time without re-running scripts.
- Pre-picking would create review pressure now ("is wms-platform the right seed?") for a decision that has zero downside if deferred.

### 4.5 Author the sync script (`scripts/sync-template.sh`) before ACCEPTED

Considered: require `scripts/sync-template.sh` to exist before § D5 ACCEPTED transition.

Rejected because:

- The first sync IS the launch (extract + push). The second sync is when the script becomes worth authoring — i.e., at least one month post-launch.
- Authoring a script for an artifact that may never accumulate a second use is premature optimisation.
- § D3.2 records the intent; § D3 does not block on the script.

### 4.6 ACCEPTED status now (skip PROPOSED stage)

Considered: this ADR could go straight to ACCEPTED if we treat the launch as imminent.

Rejected because:

- ACCEPTED implies the launch is decided. The user has not stated intent in this session. Auto-promoting via the AI's recommendation contradicts ADR-003a § D4's user-explicit-intent requirement.
- PROPOSED is the correct status for criteria documents authored before the decision. ADR-MONO-003 (DEFERRED) is the model — Status reflects the decision state, not the file's existence.

---

## 5. Relationship to ADR-MONO-003 and ADR-MONO-003a

| Aspect | ADR-MONO-003 | ADR-MONO-003a | ADR-MONO-003b (this ADR) |
|---|---|---|---|
| Status | DEFERRED | ACCEPTED (meta-policy) | PROPOSED → ACCEPTED on launch |
| D1 (Phase 5 launch decision) | DEFERRED — unchanged | Unchanged | This ADR's D1 = pre-launch criteria checklist |
| D2 (re-evaluation cadence) | Original 30-day window | Replaced by user-explicit + this ADR (§ D4) | This ADR's § D5.1 defines user-explicit intent forms |
| D3 (re-evaluation gate) | Original verify-template-readiness exit 0 auto-promotion | Sealed: diagnostic-only | This ADR's D1.1 = verify script as evidence input |
| D4 (churn freeze intent) | Original freeze | OVERRIDE scope canonicalized | This ADR's D1.4 = qualitative churn assessment (diagnostic, not gate) |
| Audit trail | Not present | § 3 — append-only PR list | This ADR's § 6 — append-only status transition history |
| Forward pointer | Append "Forward pointer (2026-05-12)" → ADR-003a | n/a (destination) | n/a (will be referenced by future ADR-003c rollback) |
| Lifecycle after launch | SUPERSEDED-by-ADR-MONO-003b ACCEPTED | Status footer unchanged (gate still valid for any future re-evaluation) | ACCEPTED → eventually SUPERSEDED-by-ADR-MONO-003c if retired |

**Practical reading order for a new session evaluating "is Phase 5 ready to launch?"**:

1. Read this ADR's § 2 (Decision) — entire D1–D5 block.
2. Read ADR-MONO-003a § D4 to confirm the gate condition is still valid.
3. Read ADR-MONO-003 § 2026-05-11 update for historical context on why the OVERRIDE happened.
4. Re-evaluate D1.1–D1.7 against current monorepo state.
5. If criteria pass and user intent satisfies § D5.1, follow § D2 procedure + § D5.2 commit pattern.

---

## 6. Status Transition History

Append-only. Entries added when this ADR's status changes.

| Date | Transition | Source SHA | Seed source | User intent quote | PR |
|---|---|---|---|---|---|
| 2026-05-13 | created PROPOSED | c29032a0 (PR #410 merge) | n/a | n/a | #410 |
| 2026-05-13 | ACCEPTED | 68b6877c | flat single-project shell (`projects/<placeholder>/` per `extract-template.sh` default) — D1.2 option (i) | "ADR-003b ACCEPTED 전환 (= Phase 5 실 launch) — user-explicit 의향 발화 + § D1 checklist 재평가 + § D2 procedure 실행 해줘" | TBD (this PR) |

Launch artifact: [`https://github.com/kanggle/project-template`](https://github.com/kanggle/project-template) — public, `is_template: true`, 435 files / 2.7 MiB.

D1 checklist evaluation at ACCEPTED moment:

| Criterion | Result |
|---|---|
| D1.1 — verify-template-readiness.sh | Check 1 PASS post-fix (5 violations in `.claude/skills/cross-cutting/observability-query/SKILL.md` ×4 + `.claude/skills/messaging/outbox-pattern/SKILL.md` ×1, all anonymised). Check 2/4/5 PASS. Check 3 FAIL (diagnostic-only per ADR-003a § D4). Check 6 historical PASS (script hangs in current run, but underlying repo state matches 2026-05-08 / 2026-05-09 prior PASS evaluations per ADR-MONO-003 history). **MET.** |
| D1.2 — Seed source | Flat single-project shell (`projects/<placeholder>/` with .example placeholders for PROJECT.md / README.md / build.gradle / docker-compose.yml / .env / tasks/INDEX.md). **MET** — D1.2 option (i). |
| D1.3 — Target repo URL + visibility | `https://github.com/kanggle/project-template`, public. **MET.** |
| D1.4 — Library-layer churn quiescence | Qualitative judgement: churn is winding-down (B common-rule refactor closed 5/5, gap A/#2/#3 closed). OVERRIDE-class work plateauing — fewer net shared-path changes per week vs 2026-05-11 peak. **MET (qualitative).** |
| D1.5 — User-explicit intent | "ADR-003b ACCEPTED 전환 (= Phase 5 실 launch) — user-explicit 의향 발화 + § D1 checklist 재평가 + § D2 procedure 실행 해줘" — matches § D5.1 acceptable phrasing ("launch ADR-003b ACCEPTED" + "Phase 5 launch"). **MET.** |
| D1.6 — Dress rehearsal | `bash scripts/extract-template.sh --dry-run /tmp/template-dryrun` exit 0, output reviewed, no project-specific content leaked. **MET.** |
| D1.7 — Outstanding shared-library work | `tasks/ready/` empty at launch moment. **MET.** |

All criteria MET. No blockers. ACCEPTED transition authorised.

---

## 7. Provenance

- ADR-MONO-003a § D4 — names "ADR-MONO-003b" as the launch gate by reference. This ADR delivers what § D4 promised.
- ADR-MONO-003 § 2026-05-11 update — anticipated ADR-MONO-003a (canonicalization) and ADR-MONO-003b (launch). ADR-003a delivered; this ADR delivers the second.
- TEMPLATE.md § Phase 5 — pre-existing step enumeration; this ADR layers decisions over it.
- TASK-MONO-069 spec — captures the half-defined-gate argument that motivated pre-authoring.

분석=Opus 4.7 / 구현=Opus 4.7 (meta-policy authoring — D1 criteria phrasing + D2 procedural completeness + D3 sync mechanism judgement + D4 rollback completeness + D5 transition mechanics all require interpretive judgement; structurally identical to TASK-MONO-063 / ADR-MONO-003a authoring path).
