# Task ID

TASK-BE-291

# Title

Ecommerce deprecated in-tree auth-service residual flow cleanup (G6 / E6) — feature & use-case prose still narrate the retired auth-service as a live actor

# Status

done

# Owner

backend

# Task Tags

- adr

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

The ecommerce in-tree `auth-service` was retired and account/auth delegated to
`global-account-platform` (GAP). The migration is only **half applied**: the
`auth-service-deprecated/` subtree and a couple of file-top deprecation banners
exist, but several **feature and use-case prose flows still narrate
`auth-service` as a live, current actor** with no strikethrough / deprecation
guard — so a reader following those flows is misled about runtime behavior.

After this task: every residual `auth-service` reference in ecommerce
`features/` and `use-cases/` either (a) reflects the GAP-delegated reality, or
(b) carries an explicit deprecation/strikethrough guard consistent with the
already-applied banners. No flow narrates the retired in-tree auth-service as
current.

Project-internal — all paths under
`projects/ecommerce-microservices-platform/specs/`.

---

# Scope

## In Scope

Reconcile the **unguarded** residual references (verified by two independent
audit agents):

- `specs/features/user-management.md:22` — "auth-service publishes UserSignedUp
  event upon successful registration"
- `specs/features/user-management.md:50` — "auth-service consumes event and
  invalidates all authentication credentials/sessions"
- `specs/features/user-management.md:66` — "Profile email and name are sourced
  from auth-service via UserSignedUp event…"
- `specs/features/authentication.md:37-94` — flow steps + event table still
  name `auth-service` as publisher without strikethrough (the file HAS a
  top-of-file deprecation banner at L6-18, but the body steps are not guarded —
  banner says "no longer reflect runtime behavior" yet steps read as current)
- `specs/use-cases/signup-and-login.md:23` — "auth-service가 계정을 생성한다."
- `specs/use-cases/signup-and-login.md:24` — "auth-service가 `UserSignedUp`
  이벤트를 발행한다."
- `specs/use-cases/user-profile-and-address.md:200` — "auth-service가 이벤트를
  수신하여 해당 사용자의 모든 세션을 무효화한다."
- `specs/services/batch-worker/overview.md:21`, `specs/services/user-service/overview.md:46-47`
  — residual actor mentions; verify and guard/rewrite.

For each: rewrite to the GAP-delegated reality where the spec is meant to
describe current behavior, OR add the same explicit deprecation/strikethrough
guard already used in `features/user-management.md:12` (strikethrough + REMOVED
annotation) and the `authentication.md:6` banner — whichever the surrounding
section's intent requires. Prefer **rewrite-to-current** for flow narration
that is meant to be normative; use **guard** only for intentionally-retained
historical context.

## Out of Scope

- The `auth-service-deprecated/` subtree itself (README, redis-keys,
  observability, architecture, dependencies, overview) — already clearly
  labeled DEPRECATED; not re-touched.
- Any `apps/` production code, test, or event-contract change (spec-only).
- Re-deriving the GAP delegation design (it is settled — see GAP import
  memory / ADR; this task only aligns ecommerce prose to it).
- GAP-side files (this is the ecommerce-side half; the GAP-side reconcile is
  separate and was found STALE for G1/G5).

---

# Acceptance Criteria

- [ ] `grep -rn "auth-service" specs/features specs/use-cases specs/services/*/overview.md`
      returns **0** lines that narrate the in-tree auth-service as a current
      actor without a deprecation/strikethrough guard (deprecated-subtree and
      explicitly-guarded historical lines excluded).
- [ ] `features/authentication.md` body steps/event table are either rewritten
      to GAP reality or struck through to match its own L6-18 banner (no
      contradiction between banner and body).
- [ ] `use-cases/signup-and-login.md` and `use-cases/user-profile-and-address.md`
      flows reflect GAP delegation or carry an explicit guard.
- [ ] No new broken links / orphans (re-run `validate-rules`).
- [ ] No `apps/` file modified (spec-only task).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 —
> read `projects/ecommerce-microservices-platform/PROJECT.md` and load
> `rules/common.md` + the domain/trait files it declares. Unknown tags = Hard
> Stop.

- `specs/features/user-management.md` (L12 guard pattern reference; L22/50/66
  edit targets)
- `specs/features/authentication.md` (L6-18 banner vs L37-94 unguarded body)
- `specs/use-cases/signup-and-login.md` (L23-24)
- `specs/use-cases/user-profile-and-address.md` (L200)
- `specs/services/batch-worker/overview.md` (L21),
  `specs/services/user-service/overview.md` (L46-47)
- `specs/services/auth-service-deprecated/` — the canonical "this is retired"
  source; mirror its guard wording for consistency.

# Related Skills

- `.claude/skills/refactor-spec/SKILL.md` — primary (consistency, no meaning
  change beyond aligning to already-decided GAP delegation).
- `.claude/skills/validate-rules/SKILL.md` — post-check.

---

# Related Contracts

- No contract envelope change. `UserSignedUp` / session-invalidation event
  semantics are GAP-owned now; ecommerce prose only describes consumption.

---

# Target Service

- `user-service`, `batch-worker` (consumers of GAP-delegated identity);
  feature/use-case specs (not service-scoped)

---

# Architecture

No architecture change. Documentation-reality alignment to the
already-accepted GAP delegation.

---

# Implementation Notes

1. The guard *pattern* already exists in-repo (`user-management.md:12`
   strikethrough+REMOVED; `authentication.md:6` banner). Reuse it verbatim in
   style — do not invent a new deprecation notation.
2. Decide rewrite-vs-guard per section *intent*: normative current-behavior
   flows → rewrite to GAP reality; deliberately-historical context → guard.
3. Spec-only; "(writing) → ready" stage — this spec PR adds the task to
   `ready/` + ecommerce INDEX only. Implementation is a separate impl PR.

---

# Edge Cases

- A line that is BOTH historical context and currently-true for the *consumer*
  side (ecommerce still consumes the event, just from GAP not in-tree
  auth-service) → rewrite the producer attribution to GAP, keep the consumer
  behavior.
- Korean and English prose both present — apply the guard/rewrite in the
  source language of each file (do not switch languages).

# Failure Scenarios

- Guarding `authentication.md` body while leaving the event table unguarded →
  banner still contradicts a sub-table; the whole file must be internally
  consistent.
- Rewriting to "GAP" without checking the GAP delegation spec → introduces a
  new inaccuracy; cross-check the GAP-side contract/feature before rewriting.

---

# Test Requirements

- Spec-only; verification = `grep` for unguarded `auth-service` actor lines == 0
  + `validate-rules` clean + no `apps/` diff.

---

# Definition of Done

- [ ] All listed residual references rewritten-to-GAP or guarded; banner/body
      consistency in `authentication.md`
- [ ] `grep` shows 0 unguarded in-tree-auth-service-as-current lines
- [ ] `validate-rules` no new broken-link/orphan; no `apps/` diff
- [ ] Branch: `task/be-291-deprecated-auth-residual` (substring `master` 금지)
- [ ] Spec PR adds this file to `ready/` + ecommerce INDEX ready list only
- [ ] Ready for review
