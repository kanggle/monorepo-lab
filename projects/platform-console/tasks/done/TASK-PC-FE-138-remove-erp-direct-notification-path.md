# TASK-PC-FE-138 — Remove the bell-unused erp-direct notification path (ADR-MONO-043 P3b follow-up close-chore)

**Status:** done

**Type:** TASK-PC-FE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet (mechanical dead-code removal + a route-count test delta; no behaviour change)

---

## Goal

P3b follow-up close-chore (PC-FE-137 DoD last item): now that the console
notification bell reads the **console-bff aggregator** (`/api/console/notifications/**`),
the legacy **erp-direct** path is dead code. Remove it: the server client
`notification-api.ts`, the three `/api/erp/notifications/**` proxy routes, the
`NotificationListResponseSchema` + `NotificationListResponse` type, the unused
`notificationDetailKey`, and the two legacy tests. This also retires the
sourceDomain-optional legacy-compat shim's *justification* (the base schema stays
tolerant but the comment no longer cites the now-deleted erp-direct path).

## Scope

`projects/platform-console/apps/console-web/`:

**Delete (6 files + the now-empty `src/app/api/erp/notifications/` tree):**
- `src/app/api/erp/notifications/route.ts` (GET inbox)
- `src/app/api/erp/notifications/[id]/route.ts` (GET detail)
- `src/app/api/erp/notifications/[id]/read/route.ts` (POST mark-read)
- `src/features/notifications/api/notification-api.ts` (erp-direct server client)
- `tests/unit/notification-api.test.ts`
- `tests/unit/notification-proxy.test.ts`

**Edit:**
- `features/notifications/api/notification-types.ts` — remove `NotificationListResponseSchema` + `NotificationListResponse`. **Keep** `NotificationDetailResponseSchema`/`NotificationDetailResponse` (the aggregator mark-read response in `use-notifications.ts` parses it). Reframe the base `NotificationSchema.sourceDomain` comment: it stays optional (the mark-read detail doesn't require attribution; the aggregator feed re-asserts it required in `AggregatedNotificationSchema`) — drop the deleted-erp-direct-path reference.
- `features/notifications/api/notification-keys.ts` — remove the unused `notificationDetailKey` (no detail query hook exists post-rewire); update the header comment (no longer references the deleted `notification-api.ts`, but keep the dependency-free principle for the client hooks).
- `features/notifications/index.ts` — drop the `NotificationListResponse` + `notificationDetailKey` re-exports (barrel only consumed for `NotificationBell`).
- `tests/unit/erp-api.test.ts` — route-count deltas: GET `20 → 18` (−2 notification GET), POST `21 → 20` (−1 notification POST), with the comment cumulative-sums renumbered.

## Out of Scope

- The aggregator path (`/api/console/notifications/**`, `use-notifications.ts`, `NotificationBell.tsx`) — untouched.
- Re-tightening `NotificationSchema.sourceDomain` to required — deliberately kept optional on the base shape (the mark-read detail response isn't guaranteed to be consumed for attribution; lower parse-fragility risk). `AggregatedNotificationSchema` keeps it required for the bell.

## Acceptance Criteria

- [x] All 6 erp-direct files deleted; `src/app/api/erp/notifications/` tree gone.
- [x] No remaining import of `notification-api.ts` or `NotificationListResponseSchema` anywhere.
- [x] `NotificationDetailResponseSchema` retained (aggregator mark-read still parses it).
- [x] `erp-api.test.ts` route-count assertions updated (GET 18, POST 20) and pass.
- [x] Bell + aggregator path unchanged; `NotificationBell.test.tsx` unaffected.
- [x] tsc clean (no unused/dangling references); CI Frontend unit + lint&build GREEN.

## Related Specs

- [ADR-MONO-043](../../../../docs/adr/ADR-MONO-043-notification-architecture-unification.md) — D2/D5/D6.
- TASK-PC-FE-137 (P3b) — the rewire whose DoD this chore completes.

## Related Contracts

- None (pure removal; the aggregator consumption contract is unchanged).

## Edge Cases / Failure Scenarios

- **Dangling type/key export** → tsc/lint catches an unused re-export; verified clean before push.
- **Route-count drift** → `erp-api.test.ts` is the guard; counts updated in lock-step with the deletions (it would RED otherwise — the deliberate churn the P3b deferral named).

## Definition of Done

- [x] 6 files removed + 4 files edited; tsc clean.
- [x] commit + push (branch `task/pc-fe-138-remove-erp-direct-notification-path`) + PR + CI GREEN + merge (3-dim verify).
