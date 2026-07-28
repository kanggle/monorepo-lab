# TASK-MONO-486 — `/validate-rules` (2026-07-29) platform spec Warning fixes

**Status:** ready

**Type:** TASK-MONO
**Analysis model:** Sonnet 5 / **Recommended impl model:** Sonnet 5 (cross-reference additions between existing
platform docs — no new rules, no design decisions)

> Surfaced by the 2026-07-29 `/validate-rules` sweep, Warning tier. Sibling of `TASK-MONO-485` (Critical tier,
> merged #3009). Every item here is "same rule stated in two places with no cross-link" or "a reachability gap
> in the spec-reading catalog" — the fix is always a pointer, never new normative content (this repo's own
> `rules/README.md` § Index File Rule: a rule that lives in only one place a reader can find is effectively a
> rule that does not exist for the other reader).

---

## Goal

Six independent cross-reference fixes across `platform/`, `rules/common.md`, and `CLAUDE.md`.

## Scope

### In Scope

1. **`platform/entrypoint.md` § Auxiliary (Read When Relevant) and `rules/common.md`'s matching Auxiliary
   table** — neither routes to `platform/abac-data-scope.md` or `platform/access-conditions.md`. Both are
   ADR-ACCEPTED (ADR-MONO-025/026), "every domain MUST use" contracts per `platform/README.md`, currently
   reachable only incidentally via `security-rules.md § Related Authorization Contracts`. Add both to the
   Auxiliary routing table in both files (same row shape as neighboring entries).
2. **`platform/coding-rules.md` § Logging ↔ `platform/observability.md` § Logging → Rules** — both
   independently state the INFO/WARN/ERROR log-level policy in different wording, no cross-reference. Pick
   `observability.md` as the fuller/authoritative statement (it already owns the Health Checks / metrics
   surface this policy lives beside) and make `coding-rules.md`'s bullet a short summary + pointer, matching
   the pattern `object-storage-policy.md § Configuration` already uses correctly elsewhere in this repo.
3. **Same two files, "never log sensitive data" line** — near-byte-identical duplicate sentence, no
   cross-reference. Same treatment: `observability.md` keeps the full statement, `coding-rules.md` points to it.
4. **`platform/coding-rules.md` § General Rules, `platform/security-rules.md` § Sensitive Data,
   `platform/deployment-policy.md` § Configuration** — "hard-coded secrets forbidden" restated independently
   in all three with no cross-link. `platform/object-storage-policy.md § Configuration` already does this
   correctly ("Hard-coded credentials are forbidden — see security-rules.md"). Make `coding-rules.md` and
   `deployment-policy.md` follow that precedent: keep a one-line statement, point to `security-rules.md` as
   the owning spec.
5. **`CLAUDE.md`'s "Git / branch / worktree discipline" catalog** — the `.claude/` self-modification bullet
   cites `TASK-MONO-409 / #2616` (agents/) and `TASK-MONO-396 / #2525` (commands/) but drops the third citation
   `platform/git-workflow-policy.md`'s own table carries for `config/` (`TASK-MONO-167 / #1021`). Add it so the
   three paths in the sentence each have their citation.
6. **`platform/naming-conventions.md` § Files → Test Files ↔ `platform/testing-strategy.md` § Naming
   Conventions + § Test Types** — naming-conventions.md's one-line rule (`{TestedClass}Test.java` or
   `{Feature}IntegrationTest.java`) was never updated for the richer table `testing-strategy.md` carries
   (Unit/Slice/Integration/Event/E2E) or the documented rename precedent (`*ControllerTest` →
   `*ControllerSliceTest`, `TASK-MONO-461`). Make naming-conventions.md's line a pointer to
   `testing-strategy.md`'s table rather than a competing, thinner statement.

### Out of Scope

- Critical-tier findings from the same sweep (`TASK-MONO-485`, merged).
- Skill-level and agent-level Warning findings (`TASK-MONO-487`, `TASK-MONO-488`).
- Info-tier findings from the same sweep (not filed; left as backlog awareness per the report).
- Any change to which document is normatively correct — every fix here is "add a pointer", not "change a rule".

---

## Acceptance Criteria

- **AC-0 (gate)** — Before editing, re-confirm each of the 6 items is still present as described (grep/read
  the live file). Drop any already fixed and say so in the PR body.
- **AC-1** — `entrypoint.md` and `rules/common.md` Auxiliary tables both list `abac-data-scope.md` and
  `access-conditions.md`.
- **AC-2** — `coding-rules.md` § Logging is a summary + pointer to `observability.md § Logging`; no
  independently-restated INFO/WARN/ERROR policy remains in `coding-rules.md`.
- **AC-3** — Same treatment for the "never log sensitive data" line.
- **AC-4** — `coding-rules.md` and `deployment-policy.md`'s hard-coded-secrets lines both point to
  `security-rules.md`, matching `object-storage-policy.md`'s existing pattern.
- **AC-5** — `CLAUDE.md`'s catalog sentence carries all three citations (`TASK-MONO-409/#2616`,
  `TASK-MONO-396/#2525`, `TASK-MONO-167/#1021`) matching `git-workflow-policy.md`'s table exactly.
- **AC-6** — `naming-conventions.md`'s test-naming line points to `testing-strategy.md`'s table instead of
  restating a thinner version.
- **AC-7** — No broken anchors/links introduced by any edit.

## Related Specs

- All six files listed above, plus `platform/object-storage-policy.md § Configuration` (the precedent pattern
  items 2–4 follow) and `rules/README.md § Index File Rule` (why cross-references matter here).
- Prior art: `tasks/done/TASK-MONO-484-promote-three-more-rules-to-canonical.md` (same "pointer, not
  restatement" discipline, different direction — promoting memory into canon rather than de-duplicating canon).

## Related Contracts

- None. No API or event contract is touched.

## Edge Cases

- Items 2–4 must not delete any information a reader currently gets from `coding-rules.md` — trim to a
  one-line summary + pointer, not a bare "see X" with zero context (match the `object-storage-policy.md`
  precedent's phrasing style).
- `platform/`, `rules/`, and `CLAUDE.md` are not classifier-blocked paths.

## Failure Scenarios

- **F1** — collapsing a summary into "see X" with no content, forcing every reader to open a second file for
  even the gist. Guarded by the Edge Cases note and the `object-storage-policy.md` precedent.
- **F2** — fixing an item already resolved elsewhere, producing churn. Guarded by AC-0.
