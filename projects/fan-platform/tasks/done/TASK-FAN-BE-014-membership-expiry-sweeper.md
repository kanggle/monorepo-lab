# TASK-FAN-BE-014 — membership expiry sweeper + notification (close the lifecycle loop)

Status: done
Type: backend (TASK-FAN-BE)
Project: fan-platform
Apps: membership-service (producer) + notification-service (consumer) + fan-platform-web (label)

---

## Goal

Close the **last forward-declared leg** of the membership lifecycle. Today expiry
is computed only at read-time (`now > validTo` → `active=false`); the
`fan.membership.expired.v1` topic is declared in the contract but **never
emitted**, and notification-service does not subscribe to it. This task ships the
**expiry sweeper**: a scheduled job in membership-service that detects newly-past
windows and emits `fan.membership.expired.v1` exactly once per membership;
notification-service consumes it into a new `EXPIRY_REMINDER` notification; the
header bell / inbox (FAN-FE-004) surface it via a new label. This makes the
membership → notification event loop complete (activate / cancel / **expire**).

## Design decision — Option B (marker column, NOT a stored EXPIRED status)

The sweeper emits the event **without** introducing a stored `EXPIRED` status. A
nullable `expiry_notified_at` marker column on `memberships` records that the
one-time expiry event was emitted; the stored `status` stays `ACTIVE` (read-time
`active=false` already handles the window). Rationale:

- **Faithful to the existing contract** — `fan-membership-events.md` + both
  architecture.md state "expiry is read-time, no stored EXPIRED". Option B keeps
  that invariant; the event is a one-time *notification trigger*, not a lifecycle
  state change.
- **Minimal ripple** — no change to `ck_membership_status`, `membership-api.md`
  list/detail `status` enum, or the FE `MembershipStatus` type. (A stored EXPIRED
  would ripple into all three.)
- **Idempotent** — `expiry_notified_at IS NULL` in the sweep predicate guarantees
  one event per membership; a partial index keeps the scan cheap. Optimistic
  `@Version` guards concurrent sweepers; multi-instance `SKIP LOCKED` is deferred
  (single-instance demo, mirrors the existing outbox poller's recorded gap).

## Scope

**membership-service (producer):**

1. `Membership` — add `expiryNotifiedAt` (`Instant`, nullable) + `markExpiryNotified(Instant)`
   (no state-machine transition; `status` unchanged).
2. `V2__expiry_sweeper.sql` — `ALTER TABLE memberships ADD COLUMN expiry_notified_at
   TIMESTAMPTZ NULL` + partial index `(valid_to) WHERE status='ACTIVE' AND
   expiry_notified_at IS NULL`.
3. `MembershipRepository.findExpirable(Instant now, int limit)` (port) + JPA derived
   query `findByStatusAndValidToLessThanAndExpiryNotifiedAtIsNullOrderByValidToAsc(...,
   Pageable)`. **Cross-tenant** by design (system background job, like the outbox
   poller); the emitted event carries `tenantId` in its payload.
4. `MembershipEventPublisher.publishExpired(...)` + `EVENT_EXPIRED` constant
   (payload per contract: membershipId / tenantId / accountId / tier / validTo /
   occurredAt). `MembershipOutboxPollingScheduler` → add `TOPIC_EXPIRED` mapping.
5. `SweepExpiredMembershipsUseCase` (`@Transactional`) — `findExpirable(now, batch)`
   → for each: `markExpiryNotified(now)` + `publishExpired(...)` (outbox, same TX);
   returns the swept count.
6. `MembershipExpirySweepScheduler` (`@Scheduled`, infrastructure) — invokes the
   use case each tick; increments `membership_expiry_swept_total`. Interval +
   batch size via config (`fan.membership.expiry-sweep.*`), default 60s / 100.

**notification-service (consumer):**

7. `NotificationType` — add `EXPIRY_REMINDER` + `EVENT_EXPIRED` + `fromEventType`
   case.
8. `MembershipEventParser` — add the `EVENT_EXPIRED` case (reads `validTo`; other
   fields stay null).
9. `MembershipEventConsumer` — add `TOPIC_EXPIRED` `@KafkaListener` (same group).
10. `NotificationTemplate.expiry(tier, validTo)` + the `EXPIRY_REMINDER` branch in
    `HandleMembershipEventUseCase.render`.
11. `V2__expiry_reminder_type.sql` — extend `ck_notification_type` to include
    `'EXPIRY_REMINDER'` (§16 CHECK allow-list change = migration).

**fan-platform-web:**

12. `entities/notification` — add `'EXPIRY_REMINDER'` to the `NotificationType`
    union. `features/notification/ui/labels.ts` — add `EXPIRY_REMINDER` to
    `TYPE_LABEL` ("멤버십 만료") + `TYPE_ACCENT`. (Bell / inbox already render any
    type generically.)

**Tests:** unit (sweep use case once-only + marker + re-sweep no-op; publisher
expired payload; parser expired; template expiry; `NotificationType` mapping;
handle EXPIRY_REMINDER) + Testcontainers IT (membership: a past-`validTo` ACTIVE
row → one `expired.v1` + marker set + second tick no-op; notification: consume
`expired.v1` → `EXPIRY_REMINDER` inbox row) + FE labels/vitest. `:test` unit +
FE `tsc`/`vitest`/`lint`/`build` run locally; Docker ITs delegated to CI.

## Acceptance Criteria

- **AC-1** A membership whose `validTo` has passed and `expiry_notified_at IS NULL`
  is swept: exactly one `fan.membership.expired.v1` is emitted (outbox), and
  `expiry_notified_at` is set.
- **AC-2** A re-run of the sweeper does NOT re-emit for an already-swept membership
  (marker predicate); a CANCELED membership is never swept (status filter).
- **AC-3** The stored `status` stays `ACTIVE` (no stored EXPIRED); the list API
  `active` flag is unchanged (already `false` post-window). No `membership-api.md`
  / FE `MembershipStatus` change.
- **AC-4** notification-service consumes `expired.v1` → an `EXPIRY_REMINDER`
  in-app notification (idempotent via `processed_events` + unique `source_event_id`),
  fanned out to the mock channels.
- **AC-5** The `expired.v1` event + `EXPIRY_REMINDER` type round-trip through the
  canonical envelope; an unsupported schemaVersion / malformed payload is still
  non-retryable → DLQ.
- **AC-6** `ck_notification_type` accepts `EXPIRY_REMINDER` (V2 migration); the
  IT is the authoritative gate for the CHECK (§16).
- **AC-7** The header bell / `/notifications` inbox render an `EXPIRY_REMINDER`
  with its label ("멤버십 만료").
- **AC-8** membership + notification `:test` (unit) green; FE `tsc --noEmit`,
  `next lint --dir src`, `vitest run`, `next build` green; Docker ITs green on CI.

## Related Specs

- `specs/contracts/events/fan-membership-events.md` (`expired.v1` — flip
  forward-declared → **emitted**; sweeper trigger).
- `specs/services/membership-service/architecture.md` (§ State Machine /
  § Outbox — add the Expiry Sweeper subsection; Option B rationale).
- `specs/services/notification-service/architecture.md` (§ Subscribed Topics /
  § Event → Notification mapping — add `expired.v1` → `EXPIRY_REMINDER` + V2).

## Related Contracts

- `fan-membership-events.md` § `fan.membership.expired.v1` (payload: membershipId,
  tenantId, accountId, tier, validTo, occurredAt). Topic `fan.membership.expired.v1`.
- No HTTP contract change (inbox API + membership API unchanged).

## Edge Cases

- A membership canceled before its window ends → `status=CANCELED` → excluded from
  the sweep (no expiry event after cancel).
- A membership re-read after expiry but before the sweep tick → `active=false`
  (read-time) but no event yet; the event follows on the next tick. Read-time and
  event-time are intentionally decoupled.
- Sweeper batch larger than the ready set → emits for all ready, no error.
- Two sweeper ticks overlapping on the same row → optimistic `@Version` makes the
  loser's save fail; the row is picked again next tick (marker still null) — at
  most-once net effect preserved by the marker on the winner.
- Expiry payload lacks `planMonths` / `validFrom` / `canceledAt` / `reason` — the
  parser's `EVENT_EXPIRED` case reads only `validTo`; the others stay null.

## Failure Scenarios

- **Double emission** — would create two EXPIRY_REMINDER notifications. Mitigation:
  `expiry_notified_at` marker set in the same TX as the outbox append (AC-1/AC-2);
  consumer-side `processed_events` + unique `source_event_id` are the second guard.
- **Stored-EXPIRED ripple** — avoided entirely by Option B (no status enum change).
- **CHECK violation on the new type** — a Docker-free `:check` slice would NOT catch
  a missing `EXPIRY_REMINDER` in `ck_notification_type` (§16); the V2 migration +
  Testcontainers IT are the authoritative gate.
- **Partition stall on a bad expired event** — the consumer rethrows; the
  `DefaultErrorHandler` routes unsupported/malformed straight to `<topic>.dlq`
  (emit-not-throw §18).

## Completion

Implemented + merged as **PR #1298** (squash `2a925c17`). Verified in an isolated
git worktree before merge: membership + notification `:test` (unit) BUILD
SUCCESSFUL; FE `tsc` 0, `vitest` 50/50, `next lint` 0, `next build` OK. CI
all-green — crucially **Integration (fan-platform) Testcontainers** (the
authoritative gate for the two V2 migrations §16 + the `ExpirySweepIntegrationTest`
once-only/re-sweep-no-op + the `expired.v1 → EXPIRY_REMINDER` consume IT); 3-dim
merge verified: state=MERGED, `origin/main` tip == `2a925c17`, pre-merge 0 failing
required checks.

Closes the last forward-declared leg of the membership lifecycle — the
activate / cancel / **expire** event loop is complete, and the FAN-FE-004 bell /
inbox surface expiries via the new `EXPIRY_REMINDER` label. Deferred: multi-instance
`SKIP LOCKED` claim; renewal flow; real push channels.
