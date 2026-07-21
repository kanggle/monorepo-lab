# TASK-FAN-BE-025 — fan-platform dedup: @CurrentActor resolver, authorship guard, channel-adapter boilerplate

**Status:** review

**Type:** TASK-FAN-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (mechanical, behavior-preserving; N1 touches many call sites)

> Filed from the 2026-07-21 reconciliation-audit refactoring rescan of fan-platform (candidates N1 + N2 + N3). Grep-verified.
> Behavior-preserving only.
>
> ⚠️ **Coordination:** a separate in-flight worktree (`refactor/fan-membership-deadcode`, uncommitted at filing time) was
> touching `ActorContext`/`ListMembershipsUseCase`/`TenantContext` in membership-service. N1 below also touches
> membership-service `ActorContextResolver` call sites — **re-measure against `main` after that work lands and rebase around
> it** (do not clobber; AC-0 covers this).

---

## Goal

fan-platform has three grep-verified duplication clusters (rule of 3 met). Extract each with **zero behavior change** —
critically, the identical "no authenticated actor" and "author-or-operator" failure paths must be preserved exactly.

## Scope

**In scope (per-service, intra-service — each fan service already owns its own `ActorContextResolver`, consistent with the
deliberate per-service-independence pattern):**

1. **N1 — `ActorContextResolver.currentOrThrow()` call-site boilerplate, 31×** across artist (12), community (12),
   membership (5), notification (2) controllers: every method opens with
   `ActorContext actor = ActorContextResolver.currentOrThrow();`. Replace with a **per-service `@CurrentActor ActorContext`
   `HandlerMethodArgumentResolver`**, centralizing the "no actor → throw" failure path. Keep it intra-service (one resolver
   per service), do not introduce a cross-service shared module.
2. **N2 — "author-or-operator" check, 6× in community-service** (`PostAccessGuard` ×2, `GetPostUseCase`, `UpdatePostUseCase`,
   `DeleteCommentUseCase`, `GetFeedUseCase`): the same `authorAccountId.equals(actor.accountId()) || actor.isOperator()`
   is re-derived independently. Hoist to `ActorContext.owns(authorAccountId)` (or a tiny `AuthorshipGuard`). NOTE:
   `PublishPostUseCase`'s `hasRole(ROLE_ARTIST) || isOperator()` is a *role* check, NOT authorship — exclude it.
3. **N3 — notification channel-adapter boilerplate, 4×** (`HttpEmailChannelAdapter`, `HttpFcmPushChannelAdapter`,
   `LoggingEmailChannelAdapter`, `LoggingPushChannelAdapter`): the `METRIC` constant
   (`notification_channel_deliveries_total`) is duplicated verbatim ×4, the delivered/failed metric+log recording is
   duplicated ×4, and a `failed(...)` helper ×2. Hoist the constant + recording into a small shared recorder (or
   `AbstractHttpChannelAdapter` + `NotificationChannelPort` default methods).

**Out of scope / deferred (rule-of-2):** `FandomService.create/update` 9-line prologue (2×) and `ArtistManagementService`
DIVE→`StageNameConflictException` guard (2×) — extract when a 3rd appears. **Excluded (HARDSTOP-03):** `ApiEnvelope`,
`GlobalExceptionHandler`/`AbstractDomainExceptionHandler`, `*OutboxPublisher` skeletons (cross-service, intentional — each
carries an explicit "intentionally service-local" comment).

## Acceptance Criteria

- **AC-0 (gate — re-measure; code wins)** — Re-confirm at current `main` the counts (31 / 6 / 4) and near-identity, AND
  reconcile with any landed `refactor/fan-membership-deadcode` change to membership `ActorContext`. Adjust the call-site set
  to reality.
- **AC-1** — Each fan service has a `@CurrentActor` argument resolver; controllers use `@CurrentActor ActorContext actor`
  instead of the manual `currentOrThrow()` line; the "no actor" failure (same exception, same status) is unchanged.
- **AC-2** — community authorship check lives in one place (`ActorContext.owns(...)` / `AuthorshipGuard`); the 6 sites use it;
  the role check in `PublishPostUseCase` is untouched.
- **AC-3** — notification `METRIC` constant + delivered/failed recording are single-sourced; all 4 adapters use it; the exact
  metric name/tags (`channel`, `outcome`) and log lines are preserved.
- **AC-4 (behavior-preserving)** — No change to authz outcomes, emitted metrics/tags, or delivery behavior. Existing
  fan-platform tests stay GREEN unchanged; the per-service Testcontainers `:integrationTest` lanes are the round-trip proof.

## Related Specs
- `projects/fan-platform/specs/services/{artist,community,membership,notification}-service/architecture.md`

## Related Contracts
- None (behavior-preserving; event/HTTP contracts unchanged).

## Edge Cases
- N1 is a large mechanical change (31 sites across 4 services) — do it per-service and keep each service's resolver behavior
  identical to its current `currentOrThrow()` (same thrown exception type/status on missing actor).
- N2: do not fold the `Comment`-typed site into a `Post`-typed helper — parameterize on `authorAccountId`, not on `Post`.

## Failure Scenarios
- **F1 — the `@CurrentActor` resolver throwing a different exception/status than `currentOrThrow()`** silently changes the
  401/403 surface. Guarded by AC-1/AC-4.
- **F2 — folding the role check (`PublishPostUseCase`) into the authorship guard** changes authz semantics. Guarded by AC-2.
- **F3 — clobbering the in-flight membership dead-code refactor.** Guarded by AC-0's rebase-around requirement.
