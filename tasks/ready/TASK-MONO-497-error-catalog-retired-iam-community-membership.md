# TASK-MONO-497 — Error catalog stale sections: `Community`/`Membership [domain: saas]` never updated after TASK-MONO-394 retirement

**Status:** ready

**Type:** TASK-MONO
**Analysis model:** Sonnet 5 / **Recommended impl model:** Sonnet 5 (documentation correction with an already-settled precedent — no new decision, just applying the repo's existing RETIRED convention)

> Root-level because the fix touches `platform/error-handling.md` (shared platform file, § Task Rules). Filed as a byproduct of
> `TASK-MONO-496` — while re-verifying that task's `PERMISSION_DENIED` promotion, the actual emitters of IAM's claimed
> `community-service`/`membership-service` turned out not to exist in the current build at all.

---

## Goal

`platform/error-handling.md` still documents two services as live, present-tense emitters that were deleted 16 days ago.

**What happened (verified against git history, not assumed):** IAM's `community-service` and `membership-service` were real,
shipping, FROZEN-labeled product-layer demo services (per `iam-platform/PROJECT.md`'s own description: "a product-layer consumer
calling account-service's internal API, as a portfolio integration example"). `TASK-MONO-052` (merged 2026-05-11, PR #372)
correctly cataloged their real, then-live exception classes into `platform/error-handling.md` as `## Community [domain: saas]`
and `## Membership [domain: saas]`. `TASK-MONO-394` (merged 2026-07-14, commit `2063027ce4`, PR #2527) then deliberately retired
both services — 269 files, 2 gradle modules, 16 spec files, DBs, Kafka topics — because `fan-platform` now performs the same
role for real. That retirement correctly updated `iam-platform/PROJECT.md` and `docs/project-overview.md` (both use a
`~~struck-through~~` + `**RETIRED 2026-07-14** (TASK-MONO-394)` marker, matching the convention `scripts/check-service-map-drift.sh`
already recognizes), and even fixed one line inside `platform/error-handling.md` itself (the fan-platform `Community` section's
`POST_STATUS_TRANSITION_INVALID` row, which now correctly reads "Was cross-shared with IAM's community-service until
TASK-MONO-394 retired it — this code is now fan-only").

**What it missed:** the retirement task's own scope enumeration of spec files to delete never named
`platform/error-handling.md`'s own `## Community [domain: saas]` / `## Membership [domain: saas]` section headers, so those two
sections (currently around lines 664–688) still read, present tense, "Owned by IAM `community-service`" / "Owned by
`membership-service`" with a full code table (`MEMBERSHIP_REQUIRED`, `ALREADY_FOLLOWING`, `NOT_FOLLOWING`,
`POST_STATUS_TRANSITION_INVALID`, `SUBSCRIPTION_ALREADY_ACTIVE`, `ACCOUNT_NOT_ELIGIBLE`, `ACCOUNT_STATUS_UNAVAILABLE`,
`SUBSCRIPTION_NOT_FOUND`, `SUBSCRIPTION_NOT_ACTIVE`, `PLAN_NOT_FOUND`) as if actively emitted today. They are not — no gradle
module, no source tree, no spec directory for either service exists anywhere in the repo (confirmed via `settings.gradle`,
`git ls-files`, and `projects/iam-platform/specs/services/`).

**Also missed:** three rows in `## Community [domain: fan-platform]` (currently `MEMBERSHIP_REQUIRED` line ~712,
`ALREADY_FOLLOWING` line ~716, `NOT_FOLLOWING` line ~717) still say "Cross-project alias — see `Community [domain: saas]`" /
"Cross-project — see `Community [domain: saas]`" as if that section still describes a live counterpart — the same class of stale
reference the retirement task already fixed for their sibling row (`POST_STATUS_TRANSITION_INVALID`, same section) but missed for
these three.

This task closes the gap the retirement task's scope left open — it is **not** re-litigating whether the retirement itself was
correct (settled, human-decided, `TASK-MONO-394`), only bringing `platform/error-handling.md` in line with what `PROJECT.md` and
`docs/project-overview.md` already correctly say.

## Scope

### In Scope
- Re-verify (AC-0) that no gradle module, source tree, or spec directory for IAM `community-service` / `membership-service`
  exists on current `main` before editing anything (things may have changed since this task was filed).
- Mark `## Community [domain: saas]` and `## Membership [domain: saas]` as retired, using the same
  `~~struck-through~~` + `**RETIRED 2026-07-14** (TASK-MONO-394)` marker convention already used in `iam-platform/PROJECT.md`
  and `docs/project-overview.md` — do **not** invent a new convention.
- Keep each section's code table (do not delete it outright) — `fan-platform`'s `Community [domain: fan-platform]` section
  explicitly cross-references these codes' provenance, and the table is the historical record of what strings were shared and
  why. Add a one-line note at the top of each section stating no service emits these codes today.
- Fix the three stale fan-platform cross-reference rows (`MEMBERSHIP_REQUIRED`, `ALREADY_FOLLOWING`, `NOT_FOLLOWING`) to match
  the wording pattern already used on their sibling row `POST_STATUS_TRANSITION_INVALID` in the same section — past tense,
  naming `TASK-MONO-394`, stating the code is now fan-only.

### Out of Scope
- Deleting the two saas sections outright — the fan-platform section's cross-references would then dangle, and the repo's own
  precedent (`PROJECT.md`, `project-overview.md`) is to mark-retired, not delete.
- Any other retired-service reference elsewhere in the repo not named above.
- Re-opening or reversing the `TASK-MONO-394` retirement decision itself (out of scope, already human-decided).
- Any `*.java` / `*.gradle` / spec change — `platform/error-handling.md` only.

## Acceptance Criteria

- [ ] **AC-0 (re-measure gate)** — Confirm on current `main`: `settings.gradle` still has no `community-service`/`membership-service`
      entries under `projects:iam-platform:apps:`; `projects/iam-platform/specs/services/` still has no directory for either;
      `git ls-files` for both still returns zero tracked files.
- [ ] **AC-1** — `## Community [domain: saas]` and `## Membership [domain: saas]` section headers/owner lines are marked
      retired using the `~~strike~~` + `**RETIRED 2026-07-14** (TASK-MONO-394)` convention, with a one-line note that no
      service emits these codes today.
- [ ] **AC-2** — Each section's code table is preserved unchanged below the retirement marker (historical record, not deleted).
- [ ] **AC-3** — The three stale fan-platform cross-reference rows (`MEMBERSHIP_REQUIRED`, `ALREADY_FOLLOWING`,
      `NOT_FOLLOWING` in `Community [domain: fan-platform]`) are reworded to match the already-corrected
      `POST_STATUS_TRANSITION_INVALID` sibling row's past-tense, TASK-MONO-394-naming pattern.
- [ ] **AC-4** — No `*.java`/`*.gradle`/spec file touched; diff confined to `platform/error-handling.md`.

## Related Specs
- `platform/error-handling.md` § Community `[domain: saas]`, § Membership `[domain: saas]`, § Community `[domain: fan-platform]`
- `projects/iam-platform/PROJECT.md` (RETIRED marker precedent, lines ~54, ~86-87)
- `docs/project-overview.md` (RETIRED marker precedent, lines ~49, ~59-60)
- `scripts/check-service-map-drift.sh` (recognizes `~~strike~~` / `RETIRED` / `FROZEN` as an intentional-drift marker)
- `tasks/done/TASK-MONO-394-iam-community-membership-ci-live-deploy-dead.md` (the retirement task whose scope missed these
  two sections)
- `tasks/done/TASK-MONO-052-error-handling-catalog-wave-3.md` (original, then-accurate registration of these sections)

## Related Contracts
- None — no live contract references IAM's retired `community-service`/`membership-service`. `fan-platform`'s own
  `specs/contracts/http/community-api.md` (if present) is unaffected; it documents the live, separate fan-platform service.

## Edge Cases
- If AC-0 finds either service has since been rebuilt or a new spec directory added, STOP and re-scope — this task assumes
  the TASK-MONO-394 retirement is still in effect on `main`.
- Don't conflate IAM's retired `community-service` with `fan-platform`'s live, separately-owned `community-service` — they
  share code strings by deliberate design (documented cross-reference), not by being the same service.

## Failure Scenarios
- **F1 — deleting the saas sections outright.** Breaks the fan-platform section's cross-references and loses the historical
  record of why fan-platform's codes look the way they do. Guarded by AC-2.
- **F2 — fixing only the section headers and missing the three fan-platform footnote rows.** The retirement task already made
  this exact mistake for the neighboring `POST_STATUS_TRANSITION_INVALID` row two months ago and missed the other three in
  the same section — AC-3 exists specifically so this task doesn't repeat that partial fix.
