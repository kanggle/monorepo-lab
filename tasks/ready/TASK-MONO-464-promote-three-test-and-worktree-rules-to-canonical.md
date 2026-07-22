# TASK-MONO-464 — Promote three durable rules from agent memory into canonical platform docs

**Status:** ready

**Type:** TASK-MONO
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (three doc insertions into existing platform files; no code, but each needs an AC-0 re-measurement against the current destination text)

> Surfaced by the 2026-07-22 `/audit-memory` sweep. Three rules currently live **only** in the agent's private
> memory but are repo-wide, host-agnostic, and would apply identically to any developer or AI session. Memory is
> not a source of truth (`CLAUDE.md` § Source of Truth Priority does not list it) and is not loaded for humans,
> so a rule that lives only there is, per `rules/README.md` § Index File Rule, effectively a rule that does not
> exist for anyone but this one agent. This task moves them to where they are authoritative.
>
> **Root-level task** because every destination is a shared path (`platform/`), which forces root per
> `CLAUDE.md` § Task Rules.

---

## Goal

Three rules become part of the canonical, human-and-agent-visible instruction set:

1. **A green isolation/uniqueness test proves nothing if its fixture inputs cannot co-occur in production** →
   `platform/testing-strategy.md`, beside the existing "a test that bypasses the enforcement layer" material.
2. **Cross-service Kafka consumer integration tests must not block on `waitForAssignment`** →
   `platform/testing-strategy.md` § Event Consumer / Producer Tests (today a thin bullet list).
3. **`gh pr merge --delete-branch` (and `--squash --delete-branch`) half-succeeds when the branch is checked
   out in a worktree** — it merges but silently skips the local + remote ref delete →
   `platform/git-workflow-policy.md` § Post-Merge Branch Hygiene.

After this task, none of the three depends on the agent's memory to be enforced.

---

## Scope

### In Scope

- Insert rule (1) into `platform/testing-strategy.md`. It must say: before asserting isolation / uniqueness /
  separation, verify the two distinguishing fixture values can **actually co-occur in production** — trace who
  populates each value to its source (token issuer, seed SQL, OAuth client config). A claim name (e.g.
  `tenant_id`) does not imply the value varies. Include the second face: an "impossible input" is also created
  by a **tool configuration production never uses** (a bare `new ObjectMapper()` with strict unknown-property
  handling when the injected Spring Boot mapper has it off) — assert against the *wired* parser/mapper, not a
  hand-built one. Worked incidents: `TASK-MONO-368` (constant `tenant_id` → per-tenant isolation was a costume
  over a global throttle) and `TASK-BE-545` (strict-mapper fixture "proved" a DLQ outage that production
  wiring did not have).
- Insert rule (2) into `platform/testing-strategy.md` § Event Consumer / Producer Tests: drop blocking
  `ContainerTestUtils.waitForAssignment(...)` in `@BeforeEach`; use `auto-offset-reset=earliest`, Awaitility DB
  polling for positive assertions, a downstream good-event **barrier** for negative (absence) assertions, and
  `RangeAssignor` for the test consumer. Name the canonical reference pattern
  (`projects/wms-platform/apps/outbound-service/.../OutboundServiceIntegrationBase.java`).
- Insert rule (3) into `platform/git-workflow-policy.md` § Post-Merge Branch Hygiene: under the mandated
  worktree-per-task convention the branch being merged is often checked out in a worktree, so
  `--delete-branch` silently no-ops the ref deletion with no signal — after such a merge, verify the local and
  remote refs are actually gone (`git branch -a | grep <branch>` / `git fetch --prune`) and delete them
  explicitly if not.
- Update the cross-reference surface so each promoted rule is discoverable from the same index that already
  routes readers (e.g. `platform/testing-strategy.md`'s own section list; `git-workflow-policy.md`'s hygiene
  catalog). No new file is created.

### Out of Scope

- Any code, test, or CI change. This is documentation only.
- Rewriting or restructuring the destination sections beyond the inserted rule + its cross-reference.
- The ~5 **borderline** audit candidates deliberately left in memory (recount-population discipline,
  HARDSTOP-05 lifecycle edit procedure, zero-CI-runs diagnosis, live-dev-server-before-worktree-teardown, the
  Spring Boot diagnostics catalog). They are agent-judgment or reference material, not repo rules; do not
  promote them here.
- The three memory files themselves. Whoever runs `/audit-memory` next can trim them to canonical pointers once
  these land; that is a memory-maintenance action, not a repo change, and is not part of this task.

---

## Acceptance Criteria

- **AC-0 (gate — re-measure each destination; the doc wins)** — Before inserting anything, read the current
  text of each destination section and confirm the rule is **not already stated there**. Rule (2)'s
  destination is known to already carry a thin consumer/producer-test bullet list and rule (3)'s destination
  already discusses post-merge hygiene and stacked-PR deletion — so for each, the deliverable may be an
  *augmentation of existing text*, not a fresh block. If any rule turns out to be already fully present, **drop
  that one and say so in the PR body** rather than duplicating it. A promotion candidate is a hypothesis, not a
  fact (`feedback_recount_population_dont_inherit_scope`).
- **AC-1** — Rule (1) is present in `platform/testing-strategy.md` with both faces (data co-existence *and*
  tool-configuration), the "trace who populates the value to its source" instruction, and at least the
  `TASK-MONO-368` incident named as the anchor. It must be findable from the file's section index.
- **AC-2** — Rule (2) is present in § Event Consumer / Producer Tests with all four mechanics (earliest / DB
  poll / good-event barrier for negatives / RangeAssignor) and the named canonical reference file.
- **AC-3** — Rule (3) is present in `platform/git-workflow-policy.md` § Post-Merge Branch Hygiene, explicitly
  tying the half-success to the *worktree-checked-out* case (the condition the repo's own
  worktree-per-task rule makes the common case) and prescribing the post-merge ref-existence verification.
- **AC-4 (shared-file agnosticism)** — Every insertion stays project-agnostic per `CLAUDE.md` § Shared vs
  project boundary and HARDSTOP-03: state the rule generally; a service/project name may appear only as a
  *worked-incident citation* (e.g. "as in `TASK-MONO-368`"), never as a load-bearing part of the rule. A
  reviewer must not be able to Hard-Stop this on project-specific content in a shared file.
- **AC-5** — `platform/` doc lint / link check (whatever the repo runs for shared-doc changes) passes; no
  broken anchors introduced by the new cross-references.

---

## Related Specs

- `platform/testing-strategy.md` — destinations for rules (1) and (2); § "a test that bypasses the enforcement
  layer" and § Event Consumer / Producer Tests
- `platform/git-workflow-policy.md` § Post-Merge Branch Hygiene — destination for rule (3)
- `rules/README.md` § Index File Rule — the "a rule in only one place is a rule that does not exist" principle
  that motivates the promotion
- `CLAUDE.md` § Source of Truth Priority (memory is absent) and § Shared vs project boundary (HARDSTOP-03)

## Related Contracts

- None. No API or event contract is touched.

## Edge Cases

- **`platform/` is not classifier-blocked** (unlike `.claude/hooks/` and `.claude/settings.json`), so these
  edits + commit + push should proceed normally; do not pre-emptively hand off.
- **Rule (2)'s existing thin bullets may partially overlap the new mechanics.** AC-0 requires reconciling
  rather than appending a second, competing list.
- **Rule (3) sits next to the existing `--delete-branch` / stacked-PR guidance**; the new sentence must
  compose with it, not contradict the stacked-PR "retarget before deleting a base" rule.
- **Over-promotion.** Only these three were judged repo-wide by the audit; the borderline five were explicitly
  held back. Adding them here would be scope creep and would risk pushing host-specific detail into a
  host-agnostic file.

## Failure Scenarios

- **F1 — inserting a rule that is already present** (especially (2) and (3)), producing a duplicate that then
  drifts from the original. Guarded by AC-0.
- **F2 — stating rule (1) or (2) with a project name as part of the rule** (e.g. "in ecommerce, …"), tripping
  HARDSTOP-03 on shared-file project-specificity. Guarded by AC-4.
- **F3 — promoting the borderline candidates too**, diluting the canonical files with agent-judgment prose.
  Guarded by § Scope out-of-scope and AC-0.
- **F4 — a broken cross-reference anchor** from the new section links. Guarded by AC-5.
