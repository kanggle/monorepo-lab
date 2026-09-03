# Git Workflow Policy

Normative git / branch / worktree procedures for AI agents and developers working in this monorepo. This file is **read on-demand** (Source-of-Truth Priority layer 5, `platform/` remaining files) — `CLAUDE.md` carries only the one-line catalog form of each rule and points here for the full procedure.

This file is **self-contained**: a fresh clone needs nothing but this file to follow the rules. Worked-incident forensics belong **here** (or in agent personal memory), never re-inlined into `CLAUDE.md` — that is what caused the catalog drift this file corrects. Project-agnostic: no service names, API paths, or domain entities.

---

## Branch Naming

**Never include the substring `master` in branch names.** The sandbox `--force` regex matches `master` as a substring and blocks `git push` even on feature branches.

- Rename around the noun: `task/be-161-database-design-...` (not `...-master-service-...`).
- Or use the abbreviation: `ms-`, `mst-`.
- Workaround if you hit it: `git push -u origin HEAD` (renaming the branch is cleaner).
- Encountered repeatedly across multiple tasks — treat as a standing constraint, not a one-off.

---

## Concurrent-Session Worktree Isolation

Multiple interactive agent sessions (or routines) running at the same time must **never share the main checkout**. Each concurrent task gets its own `git worktree add` directory; the main checkout stays parked on a stable branch (ideally `main`) and is not used for task work.

- A `git checkout` / `checkout -b` in a directory another live session is using moves the **single shared HEAD + index** of that directory — the other session's uncommitted WIP gets stranded on the wrong branch, and its next `git commit` lands on *your* branch. ("branch per task" alone is insufficient; the isolation unit is the **worktree (directory)**, not the branch.)
- The `protect-main-branch.ps1` hook only blocks commits/pushes **on `main`** — it does NOT catch two sessions sharing the main checkout across two *feature* branches. There is no automated guard for this case; it is a discipline rule. (`docs/guides/monorepo-workflow.md` documents the worktree convention but is human-reference-only and assumes the harness-managed `.claude/worktrees/agent-<id>/` dispatch model, not manual multi-session worktrees.)
- **Symptom**: `git worktree list` shows the main checkout on an unexpected feature branch, or `git status` surfaces files from a task you are not working on.
- **Recovery**: commit only your own files by **explicit path** (leave the other session's WIP untouched), then `git checkout <their-branch>` to restore the shared HEAD — uncommitted WIP travels along and re-lands on the correct branch (0 path overlap = no conflict). Afterward move your work to its own `git worktree add` directory.
- Worktree-add Windows pitfalls (DWIM remote-branch resolution, failed-remove, prune side effects) — always pass **absolute** worktree-add paths; a relative `../x` from a drifted shell cwd can nest a stray directory inside the main checkout and lose files. (Agent personal-memory detail, this host: `env_git_worktree_verify_windows`, `env_concurrent_git_branch_switch_hazard`.)
- Worktree teardown on Windows with junctioned `node_modules` — when a frontend worktree shares the main checkout's `node_modules` via a directory junction (reparse point), tearing the worktree down naively (`Remove-Item -Recurse` / `git worktree remove`) follows the junction and **corrupts the main checkout's `node_modules`** (e.g. a missing `.pnpm` store then breaks the main tree's `tsc`/`vitest`). Remove the junction reparse points **first** (`cmd /c rmdir <junction>`, which unlinks without following), then delete the worktree directory. Recovery if corrupted: re-run the affected app's install (`pnpm install --force`). (Agent personal-memory detail, this host: `env_worktree_node_modules_junction_cleanup_hazard`.)

### Shared-File Task Series — Reuse One Worktree, Serialize the Merges

A series of tasks that each edit the **same shared file** (a nav config, a barrel `index.ts`, a shared
`types.ts`) is a special case even under the isolation rule above: running the series across **separate,
parallel** worktrees does not just risk cross-contamination — it guarantees a merge conflict on that shared file,
because task N+1's branch is cut from a base that does not yet contain task N's edit to it.

- **Reuse a single worktree across the whole series and serialize the merges**: implement, verify, and merge
  task N to completion first; only then, in the **same** worktree, `git fetch origin main && git checkout -b
  <task N+1 branch> origin/main`. Task N's shared-file change is now in the base, so N+1 lands on top of it
  without conflict.
- This is strictly cheaper than parallelizing anyway — reusing the worktree avoids a full re-checkout and
  re-populating any junctioned `node_modules` between tasks in the series.
- This does not relax § Concurrent-Session Worktree Isolation above — it describes how a **single** session
  should sequence a series it owns; other concurrent sessions still need their own separate worktree.

### Dispatching a subagent into a worktree

When delegating implementation to a subagent (the Agent tool) that must edit *inside* a worktree, pass **absolute worktree paths** in the prompt and instruct it to use them. A relative-looking path resolves against the **session cwd** (the parked main checkout), so the subagent's edits silently land in the protected main checkout instead of the worktree — the same contamination this section guards against, via a different route.

- **Guard**: immediately after dispatch, run `git status --porcelain -- <path>` in the main checkout; any modification there is a leak.
- **Recover**: `git restore <path>` (tracked) + remove only the **named** untracked stray files (leave the working tree otherwise untouched).
- The subagent may self-correct by re-applying with absolute paths, but the orphaned copy in the main checkout persists until cleaned, and the classifier blocks the *subagent* from cleaning the main checkout — the **orchestrator** must. (Worked incident: TASK-MONO-241, 2026-06-13.)

---

## Post-Merge Branch Hygiene

The repo squash-merges PRs; feature/chore refs are not auto-pruned and accumulate.

- After a PR squash-merges, delete its feature + close-chore refs immediately. Stacked work uses a single tip-only PR (the tip contains its base; the base ref becomes squash-residue → delete it too).
- **`gh pr merge --delete-branch` (and `--squash --delete-branch`) half-succeeds when the branch is checked out in a worktree.** Under the mandated worktree-per-task convention (§ Concurrent-Session Worktree Isolation) the branch being merged is usually still checked out in a worktree, so the merge lands but the `--delete-branch` ref deletion **silently no-ops — no error, no warning**. Do not trust the flag: after the merge, remove the worktree, then confirm the refs are actually gone (`git branch -a | grep <branch>` and `git fetch --prune`) and delete any survivors explicitly. This composes with the stacked-PR base-ref rule below — retarget a child before deleting its base regardless.
- A ref is **squash-merge-stale** (safe to delete) when its task is in `origin/main`'s `tasks/done/` (or its squash commit is in `git log origin/main`).
- The auto-mode classifier is a context-sensitive higher safety layer over the permission allowlist — it **may** gate mass `git push origin --delete`, but not as a hard rule (it allowed a confirmed-merge-stale batch on 2026-06-15). **Attempt the deletion first** when every target ref is confirmed merge-stale (per the bullet above) and not worktree-occupied / OPEN; `gh pr create` / `gh pr merge --squash` pass; local `git branch -D` is fine for the agent. Only on an **actual** block: STOP and hand the user the exact command — do not reformulate to bypass.
- **`git branch -r` is a stale local cache, not `origin` truth.** Before concluding remote-branch state or recommending a mass remote deletion, run `git fetch --prune` (or `git remote prune origin`). `git fetch origin main` updates only `main` and does NOT prune — so refs already deleted on `origin` linger locally as stale tracking refs that falsely read as "needs cleanup". Prune first, confirm the real residue, then hand over only what genuinely remains (often nothing — avoid an unnecessary user `push --delete`).
- **Stacked-PR base-ref-deletion auto-close hazard.** Deleting a PR's base ref auto-closes that PR on GitHub, and `gh pr reopen` is then rejected — so `gh pr merge <base> --squash --delete-branch` is destructive-in-disguise for any child PR stacked on it. Prevention: retarget the child first (`gh pr edit <child> --base main`), or merge the base without `--delete-branch`. Recovery: `git rebase --onto origin/main <base-squash-sha>` the child, `--force-with-lease`, open a fresh PR.
- **`Agent(isolation:'worktree')` leaves a scaffold branch behind.** The harness creates the dispatched agent's worktree on its own `worktree-agent-<id>` branch; if the agent then creates its own task branch inside that worktree and merges it via PR, `git worktree remove` only deletes the directory — both the task branch and the `worktree-agent-<id>` scaffold branch remain as local refs (fanning out fast when several agents are dispatched in parallel). After the PR merges: `git worktree remove <path> --force`, then delete **both** refs explicitly with `git branch -D` (confirm each is already in `origin/main` first — squash-merge usually rejects the safer `-d`).

(Agent personal-memory detail, this host: `project_branch_hygiene_policy`, `env_agent_worktree_isolation_leftover_scaffold_branch`.)

---

## Local-Only Override File Markers

Before staging a broad set of session changes (`git add` across everything touched, especially after a long multi-project session), grep modified/untracked files for a self-declared "LOCAL DEMO ONLY — DO NOT COMMIT" / "(uncommitted)" header marker. These appear on ad-hoc local override files — docker-compose overlays with hardcoded local secrets/ports, scope/config tweaks tied to a locally-seeded DB row — that a user or prior session deliberately kept out of shared history.

Confirm include/exclude explicitly with the user rather than assuming either way: silently staging everything risks pushing local-only secrets/config into `main`; silently dropping files risks losing real changes the marker doesn't actually apply to.

(Agent personal-memory detail, this host: `feedback_check_do_not_commit_headers_before_staging`.)

---

## A PR whose base is not `main` receives **zero** checks — and merges unblocked

`.github/workflows/ci.yml` is triggered by `pull_request: branches: [main]`. That filter matches on the PR's
**base**. A PR opened against any other base therefore **matches no workflow at all** — GitHub does not run,
skip, or queue anything. It reports **0 checks**.

This is the dangerous case precisely because it looks like the safe one:

- "0 failing checks" and "CI ran and approved this" are **indistinguishable at the merge button**.
- There is no red X, no pending spinner, and nothing to block the merge — branch protection has no required
  check to wait for when no workflow ever matched.
- The code reaches `main` through the **normal, green-looking path**, having never been compiled or tested.

**0 checks is not a flake, and it is not a merge conflict** (a conflicting PR still *matches* the workflow;
it just cannot run — see the distinction below). It means the workflow never applied.

**Therefore**: open every PR — spec and impl alike — with **`base=main`**, and merge them sequentially.
Where the work genuinely stacks, follow § Post-Merge Branch Hygiene: land it as a single tip-only PR whose
base is `main`, not as a chain of PRs pointed at each other.

> The invariant is about the **PR base**, not about local branch topology. Stacked *branches* are fine.
> A PR whose `base != main` is not.

Before trusting a PR's check state, confirm the count is non-zero — an empty check list is a signal, not an
absence of problems.

### Retargeting the base does not retrigger CI

If a PR was nonetheless opened against a non-`main` base, **fixing the base is not enough**. `gh pr edit
--base main` fires an `edited` event and `gh pr ready` fires `ready_for_review` — neither is in the default
`pull_request` activity types (`opened` / `synchronize` / `reopened`), so neither starts a run. Any
force-push made *while* the base was still a feature branch was likewise filtered out by `branches: [main]`.
The PR therefore sits at **0 checks even after it correctly targets `main`**, wearing the same green-looking
face described above.

**Force a run** with `gh pr close <#> && gh pr reopen <#>` (a `reopened` event, now matching `base=main`) —
this is cleanest, as it churns no commit hashes — or push a new commit to the head branch (`synchronize`).
Then re-confirm the check count is non-zero before merging. (Worked incident: TASK-MONO-270, PR #1649.)

---

## `gh pr create` / `gh pr merge` Body Hook False-Match

The `protect-main-branch` hook inspects the command string for direct-to-`main` pushes. A `gh pr create` / `gh pr merge` whose **inline body text** (`--body "…"`) contains literal tokens such as `push origin --delete`, `push --delete`, or `reset … to main` can trip a false-match and be **blocked** even though the command only opens/merges a PR. Workaround: pass the body via a file — `gh pr create --body-file <path>` (the hook matches the inline command string, not file contents) — or reword the body to avoid those literal tokens. (Agent personal-memory detail, this host: `project_branch_hygiene_policy`.)

---

## `git commit && git push` Chained in One Bash Call Is Blocked Whole

Chaining `git commit … && git push …` in a **single** Bash-tool invocation lets the `protect-main-branch` hook match the `push` half and block the **entire** call — so the commit never lands either. Run them as **two separate Bash calls**: commit first, confirm it succeeded, then push. (Agent personal-memory detail, this host: `project_console_web_ecommerce_ops_bug_class`.)

---

## Agent Self-Modification — What the Classifier Actually Keys On

**The axis is the edit, not the path.** The auto-mode classifier (the same higher-safety layer as mass
`push --delete`) gates edits that **change what is permitted** — the hook machinery that constrains the agent,
and, separately, authorization data anywhere in the tree:

- **Permission-changing edits bite outside `.claude/` too.** An edit to a role catalog, a permission matrix,
  or an authorization constant in ordinary domain code has been blocked as privilege escalation
  (TASK-MONO-522).
- **The same file's prose passes.** Editing the comments or documentation around that table is not a
  permission change and goes through (TASK-MONO-528). The classifier reads what the edit *does*, not where it
  lives.
- Reading a path table alone, an agent that gets blocked outside `.claude/` cannot find the cause — and will
  wave through a genuine privilege edit because "the path isn't on the list".

### Observation log — not a guarantee

The rows below are **the last observed result for each path, with when and where it was observed**. This is an
external policy that can change, and it has been seen to change **inside a single session**: `gh pr merge` was
blocked on PR #3319 and passed on #3322 in the same run. Treat a row as evidence about a moment, not as a
property of the path.

| Path | Last observed | When / where |
|---|---|---|
| `.claude/hooks/` | ✅ passed — edit + commit + push | TASK-MONO-530 (2026-08-14). Supersedes an earlier observation that it was blocked even under explicit instruction. |
| `.claude/settings.json` | ❓ **not re-verified** | Observed blocked before 2026-08-14; **not** re-tested since `hooks/` was seen to pass. Unknown — which is not the same as blocked. |
| `.claude/commands/` | ✅ passed — edit + commit + push + merge | TASK-MONO-396, PR #2525 |
| `.claude/agents/` | ✅ passed — edit + commit + push + merge | TASK-MONO-409, PR #2616 |
| `.claude/config/` | ✅ passed | TASK-MONO-167, PR #1021 |
| `.claude/skills/` | ⚠️ passed with **explicit per-action** authorization | vague agreement is not enough (TASK-MONO-234) |
| `platform/` | ✅ never observed to trip the path axis | — (the permission axis above still applies) |

**How to update this table.** After any attempt, replace that row with: passed/blocked, the date, and the task
or PR that observed it. A table nothing can fail on will drift — and this one **did**: it asserted `hooks/`
was hard-blocked and intent-resistant long after that stopped reproducing.

If an edit **is** actually blocked: hand the exact patch to the user to apply + commit; **do not attempt a
shell-write bypass** — that dodges the denial's intent, and a real block is real.

**For every row: do not pre-emptively hand off on assumption.** Attempt the edit once; hand it over only if it
is **actually** blocked. The asymmetry is the argument — a wasted attempt costs one round-trip, while a wrong
assumption costs a needless human hand-off **and files a false completion note in the ticket**.

That is not hypothetical, and it has now happened **twice with the same wording in this very section**:

1. It asserted `agents/` and `commands/` were blocked; TASK-MONO-409's own preamble repeated the claim, and the
   agent that tried anyway landed PR #2616 directly.
2. It then asserted `.claude/hooks/` was hard-blocked *and intent-resistant*; TASK-MONO-530 measured edit +
   commit + push passing.

Both times the over-broad wording propagated unchallenged, and both times the correction was recorded only in
a task's DONE note — a place nobody greps when asking "am I allowed to edit this?". **The recurrence is why
the table above records observations with dates instead of verdicts**: fixing one row's value leaves the next
row free to make the same claim. (Agent personal-memory detail, this host:
`env_classifier_claude_self_mod_block`.)

---

## Self-Merge and Force-Push Require Explicit Authorization

These get gated by the auto-mode classifier independent of, and via a different trigger than, the
self-modification rule above:

1. **Self-merging a PR the agent itself just authored** (`gh pr merge <n> --squash`). A general "proceed to
   completion / don't stop for confirmation" instruction is read as generic autonomy, not as naming
   merge-without-review specifically — it does not, by itself, authorize merging your own unreviewed PR. Get an
   **explicit, specific** confirmation (e.g. ask directly whether to merge) before doing so.
2. **`git push --force-with-lease`** — blocked as history-rewriting unless the user explicitly named a force
   push. If a rebase needs re-pushing and the user hasn't asked for a force push, push to a **new ref** instead
   (`git push origin HEAD:<branch>-v2`) rather than forcing over the existing one.
3. **`docker volume rm`** — gated as destructive-and-irreversible, on the same axis and with no relation to
   any path. Named here so a block on it is not mis-diagnosed as a self-modification block.

---

## Post-Merge Nightly Check for Route/Nav/testid Changes

Two full-stack e2e suites (the console E2E job and the frontend E2E job) run only in `nightly-e2e.yml`, not in
`ci.yml` — moved there by `TASK-MONO-045` for cost reasons. This is an intentional trade-off, not a bug, so the
gap persists: a PR that changes a console/web-store route, nav entry, testid, or screen heading can merge green
having never exercised the spec that asserts on it, then redden `main` overnight with nothing surfacing the
failure until someone notices independently.

- **Before merging** a route/nav/testid/heading change: grep the e2e spec directories (console, web-store) for
  a spec asserting on the changed testid/URL, and fix it in the same PR if found.
- **When unsure**, fire the nightly suite directly against the branch — `gh workflow run nightly-e2e.yml --ref
  <branch>` — cheaper than discovering a red `main` after the fact. (Worked incident: `TASK-PC-FE-240`, a route
  move that merged green and left `main` red for four days before anyone noticed.)
- **After merging such a change**, check the next nightly run on `main` once — nothing else currently does.

---

## CI Path-Filter Constraint

When editing `.github/workflows/` `dorny/paths-filter` configuration: never use negation patterns (the `predicate-quantifier: 'some'` negation misclassifies a file as "in"); use a pure-positive `code-changed` filter composed with the original via an outputs-layer AND; backfill new code extensions into the positive filter; add an entry per new project. (Agent personal-memory detail, this host: `project_ci_path_filter_074_075_quirk`.)

**A correct filter is worthless if the consuming `if:` ignores it.** A job guarded by
`github.event_name == 'push' || <filter condition>` short-circuits on the first term, so on `main` the filter
verdict is overridden and every heavy lane runs regardless. Gate the push branch too.

**When you do, the comparison must be `!= 'false'`, never `== 'true'`.** If the paths-filter step itself
fails, every output is the empty string. `'' != 'false'` is **true** → the lanes all run → an unverified
change is still gated (fail-safe). `'' == 'true'` is **false** → the lanes all skip → **a regression lands on
`main` having been checked by nothing, and the skip reports green.** The two spellings are equivalent on
every path except the one that matters. (Worked incident: TASK-MONO-343.)

---

## Merge-Verification Worked Incident

The four-dimension objective merge verification before any close chore (defined in `CLAUDE.md` § Task Rules) exists because a "merged it" statement is not proof. Worked incident: a PR was squash-merged while a required integration check was still failing → `main` went RED four times in a row → recovery required a separate top-priority fix-task to restore `main` GREEN. CI-RED-at-merge time creates a main regression; the `statusCheckRollup` of the merged PR is the authoritative record. If any of the four dimensions fails, STOP and open a fix-task before the close chore. Dimension (d) is the exception to "open a fix-task": see § The Fourth Dimension below — it means do not move the file at all.

> 🔴 **Read "required" literally, and know how small the set is** (`TASK-MONO-598`, 2026-08-28). The incident above says *a required integration check* — that was the language of the day, not the current configuration. `main` now requires exactly four contexts — 🔴 **written with their parentheses, because the parentheses are part of the registered string, not decoration** (`TASK-MONO-599`, 2026-08-28): `changes`, `INDEX queue drift (INDEX.md tables vs queue directories)`, `Task ID collision (duplicate IDs in active queues)`, `Walkthrough limitation ledger drift (§ 6 rows vs task queues)`. Only the first is short, and only because that job carries no `name:` so its job id becomes the context verbatim. They were chosen because they measured SUCCESS in **24 of 24** sampled PRs, so requiring them cannot deadlock on a permanent pending; the reusable-workflow children (`… / integration`, `… / e2e`) appear in only **4 of 24** and would. **So integration and e2e are NOT required, and dimension (c) does not assert they were green.** Until 598 the required set was empty, which made (c) true in every state — the guard named in this very section could not bite.

> 🔴🔴 **Four are required; on most PRs fewer than four actually run** (`TASK-MONO-601`, 2026-08-29). Three of the four — `INDEX queue drift (INDEX.md tables vs queue directories)`, `Task ID collision (duplicate IDs in active queues)`, `Walkthrough limitation ledger drift (§ 6 rows vs task queues)` — carry `if: needs.changes.outputs.<filter> == 'true'` and every one of those filters lists only `tasks/**` paths. A PR that touches no task file leaves all three `SKIPPED`, and GitHub does not count `SKIPPED` as failing, so **dimension (c) is satisfied with only `changes` having actually executed.**
>
> Measured 2026-08-29 over the last **70 merged PRs**: exactly **2 were code-only** — [#3523](https://github.com/kanggle/monorepo-lab/pull/3523) (1 file) and [#3479](https://github.com/kanggle/monorepo-lab/pull/3479) (9 files) — and both show `changes=SUCCESS` with the other three `SKIPPED`. The remaining 68 touched a task file and ran all four.
>
> 🔵 **The skipping is correct, and widening the gates would be wrong.** Each filter follows that guard's defect *arrival path* (`TASK-MONO-389`/`451`), and a code-only PR is not on it. The defect is in how the rule reads: "four contexts are required" is easily read as "four contexts ran".
>
> 🔴 It also re-frames 598's own evidence. `24 of 24` is true, but it was measured on a population that is roughly **97% task-touching** — a sample in which "always SUCCESS" and "frequently SKIPPED" produce the same observation. The census measured where it looked. 598's conclusion (these four cannot deadlock on a permanent pending) still holds; what does not follow is that they are four *executed* signals.
>
> 🔵 `scripts/check-required-check-names.sh` cell (4) asserts the gating shape, so this sentence fails a build if someone ungates one of the three. 🔴 What no repo-side guard can see is a **single PR's** actual conclusions — that is runtime, not repo state. Read the rollup.

> 🔴🔴 **A guard that bites is not a document that is right** (`TASK-MONO-599`, 2026-08-28). 598's AC-4 proved the gate bites — probe PR `BLOCKED`, non-required-red PR `UNSTABLE` and merged — by reading the **live configuration**. The names it wrote into three documents on the same day were never compared against it, and **three of the four were wrong** (every parenthetical dropped). Re-registering protection from those names would make three contexts permanently pending and deadlock `main` — the exact failure 598 excluded tier 3 to avoid. It survived a day because the string is true or false *depending on the tool*: substring `grep` matched, equality did not. **When you turn a mechanism on and then describe it, the description is a second artifact and needs its own check.** The pin is `scripts/required-check-names.txt` (sourced from the API, never retyped) and `scripts/check-required-check-names.sh` compares it against `ci.yml` and the three documents. 🔴 It cannot see changes made on the protection side — if the owner changes the required set, the pin must be rebuilt from the API.

## The Fourth Dimension — (d) Did the AC Actually Close?

> `TASK-MONO-620`, 2026-09-03. Surfaced by `/audit-memory` as a promotion candidate: the rule
> lived in an agent's private memory, so every other session walked into it unguarded.

🔴🔴 **Dimensions (a), (b) and (c) all measure the same object — the pull request.** (a) and (b)
ask *did it merge*; (c) asks *did merging break `main`*. **None of them asks whether the ticket's
own Acceptance Criteria closed.** That is dimension **(d)**, and it is the only one that measures
the *ticket* rather than the *PR*.

The cost of missing it is asymmetric: `done/` is a **frozen** stage. A remainder filed under
`review/` is still on somebody's queue; the same remainder filed under `done/` is read by nobody,
ever again. So (d) failing is not "close it and note the gap" — it is **do not move the file.**

### How to read (d)

- **Open the ticket body's `# Acceptance Criteria` section.** Its `INDEX.md` row cannot answer
  this — the row records queue position, which is exactly what (a)(b)(c) already measured.
- **Read the AC's verb, not its topic.** An AC that says *"명시한다 — 남길지 지울지"* is closed by
  a decision, not by a recommendation.
- 🔵 **The reverse also holds.** A ⚪ that records *"could not be measured, and here is why"* is
  the **answer** to an AC that asked for exactly that. Reading every ⚪ as "open" fails in the
  opposite direction.

### Worked evidence — three tickets already in this repository

| Ticket | What (a)(b)(c) said | What (d) would have said |
|---|---|---|
| `projects/iam-platform/tasks/done/TASK-BE-582-…md` § `AC-4` | all three passed | the AC's own heading reads **"🔴 절반만 충족됐다. 나머지를 적는다."**, and its 「기존 볼륨」 row is `⏳ 미수행` |
| `tasks/done/TASK-MONO-605-…md` § `AC-3` | all three passed | the AC (`:127`) requires *"판정 후 스냅샷 처분을 **명시한다** — 남길지 지울지"*; the result (`:254-257`) is *"🙋 스냅샷 처분 = 소유자 결정. **추천: 삭제**"* under a ✅ heading. **A recommendation is not a choice** |
| `tasks/done/TASK-MONO-574-…md` § `AC-2` | all three passed | ⚪ *"미측정, 그리고 측정 불가였다"* — and `AC-3` asked for *"판정하지 못한 것을 함께 적는다"*, so **the ⚪ is the closure**, not a gap |

🔴 **No repo-side guard can measure (d), and none should pretend to.** AC satisfaction is a prose
judgement about whether a described outcome occurred; a guard that greps for ✅ would pass
`TASK-MONO-605` — the ✅ was there. This dimension is enforced by the agent or human doing the
close chore, which is why it has to be written down where they will read it.

🔴 **Where the count lives.** The number of dimensions is stated in four files —
`CLAUDE.md` § Task Rules, this section, `.claude/commands/review-task.md` (twice) and
`.claude/commands/process-tasks.md`. They must move together; a count that is right in one
home and stale in another is the failure mode this repository has paid for repeatedly. The
**evidence** above, by contrast, lives only here — `CLAUDE.md` carries the catalog line and
points at this anchor.
