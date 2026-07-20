# TASK-BE-545 — Audit whether real users are stuck withdrawn-but-not-cancelled from the `UserWithdrawnEvent` deserialization bug TASK-BE-533 just fixed

**Status:** ready

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (a DB/topic audit + the repair runbook; no new code expected)

> **This is an audit/repair ticket, not a fix ticket.** The code defect is already fixed
> (`TASK-BE-533`). What is unknown is the **blast radius**: how many real withdrawals, across how
> long a window, actually hit it. Do not open by assuming the backlog is large or that it is empty
> — measure it.

---

## Goal

While verifying `TASK-BE-533`'s AC-5 ("the runbook must be executable as written"), driving the
runbook's own repair payload — the real `user.user.withdrawn` envelope shape, which always carries
`tenant_id` per `specs/contracts/events/user-events.md` § Event Envelope — through
`order-service`'s `UserWithdrawnEvent` record threw `UnrecognizedPropertyException` (proven with a
unit test, not inferred). That record was missing `@JsonIgnoreProperties(ignoreUnknown = true)`,
which every sibling inbound event record in `order-service` (`PaymentRefundedEvent`,
`AccountDeletedEvent`) already carries. `git log` shows the record unmodified since the initial
monorepo merge — this was not a recent regression.

The practical consequence: `com.fasterxml.jackson.core.JsonProcessingException` (the supertype of
`UnrecognizedPropertyException`) is registered in `order-service`'s `KafkaConsumerConfig` as
**not retryable**, so every real `UserWithdrawn` message went straight to
`user.user.withdrawn.dlq` on first attempt, for as long as this record has existed. The counter this
same ticket restores an alert on, `event_publish_failure_total{service=user-service,
event_type=UserWithdrawn}`, could not have seen this — it counts **publish**-side failures
(broker rejection, serialization at the producer), and this failure was entirely on the
**consume** side. There has been no signal of this failure mode at all until now.

TASK-BE-533 fixed the code defect (annotation added, regression test added, order-service GREEN)
and shipped the runbook (`knowledge/runbooks/user-withdrawn-not-cancelled.md`) that this ticket
uses as its repair procedure. What TASK-BE-533 did **not** do — deliberately, to keep its own PR
scoped to ADR-006's mitigations — is check whether any deployed environment actually has affected
users sitting in this state right now, or whether the `.dlq` topic actually has a backlog.

## Scope

**In scope:**

1. **Check whether the DLQ actually has anything in it**, per
   `knowledge/runbooks/notification-dlq-replay.md` §1's method adapted to
   `user.user.withdrawn.dlq` (same `GetOffsetShell` approach) — in whichever environments this
   project has a persistent Kafka log (local demo, AWS PoC if still up per
   `project_ondemand_demo_aws_poc`, any other deployed instance). A dev/demo environment with
   `auto.offset.reset` / short retention may have already lost the evidence — say so if so, rather
   than reporting a false negative.
2. **Run the ops query** from `knowledge/runbooks/user-withdrawn-not-cancelled.md` §2 against every
   environment checked in (1): `user_profiles.status = 'WITHDRAWN'` joined against `orders.status IN
   ('PENDING','CONFIRMED')` for those user ids.
3. **If real affected users are found**, follow the runbook's §3/§4 repair procedure (now that the
   underlying consumer accepts the contract-compliant envelope) and verify with §4's checks.
4. **Record the finding** — how many users, over what window, in which environment(s) — in this
   task's closing note, whether the count is zero or not. A zero count in a demo/local environment
   with little real traffic is an expected, useful result, not a non-finding.

**Out of scope:**

- Re-opening the code fix itself (`@JsonIgnoreProperties` on `UserWithdrawnEvent`) — already landed
  in `TASK-BE-533`. If AC-0 finds it missing or reverted, stop and report; do not silently redo it
  here under a different ticket.
- Auditing other inbound event records for the same missing-annotation pattern across the fleet.
  `AccountDeletedConsumer`/`OrderPlacedEventConsumer`/etc. were not checked for this specific gap;
  if this ticket's AC-0 recount turns up a reason to suspect them, report it as a separate
  candidate rather than widening this ticket's scope.
- Any production (non-demo) environment this monorepo does not itself operate. If this project has
  no real user traffic anywhere, say that plainly and close with that as the finding.

## Acceptance Criteria

- **AC-0 (gate — re-measure; the code wins)** — Independently re-verify at start: (a)
  `UserWithdrawnEvent`/`UserWithdrawnPayload` still carry `@JsonIgnoreProperties(ignoreUnknown =
  true)` (`TASK-BE-533`'s fix landed and is still in place); (b) which environments this project
  actually has running with a persistent `user.user.withdrawn` topic history. If neither exists,
  report that plainly rather than fabricating an audit.
- **AC-1** — The `user.user.withdrawn.dlq` topic's message count (or "topic does not exist" /
  "retention already expired the window") is reported for every environment checked.
- **AC-2** — The withdrawn-but-not-cancelled ops query result (row count, not just "some/none") is
  reported for every environment checked, per the runbook's §2 method.
- **AC-3** — If AC-1 or AC-2 finds real affected users, they are repaired via the runbook's §3/§4,
  and §4's verification (orders reach `CANCELLED`, consumer lag returns to 0) is shown to pass.
- **AC-4** — The closing note states the total finding across all environments checked, even if it
  is zero everywhere — a negative result is a valid, useful deliverable here (mirrors
  `TASK-BE-537`'s framing).

## Related Specs

- `projects/ecommerce-microservices-platform/knowledge/runbooks/user-withdrawn-not-cancelled.md`
  (the repair procedure this ticket executes)
- `projects/ecommerce-microservices-platform/knowledge/runbooks/notification-dlq-replay.md`
  (the DLQ-inspection method this ticket adapts to a different topic)
- `projects/ecommerce-microservices-platform/specs/contracts/events/user-events.md`

## Related Contracts

- `UserWithdrawn` (`user.user.withdrawn`) — no contract change; this ticket is an operational audit
  against the existing contract, not a schema change.

## Edge Cases

- **Demo/local environments may have already lost the evidence** — short Kafka retention or a
  `docker compose down -v` between the bug's lifetime and this audit means AC-1 can legitimately
  come back "topic absent" even though the bug was real. Report that as what it is, not as "no
  impact was ever caused".
- **A withdrawn user with no active orders at withdrawal time** is not an instance of this bug —
  the ops query's row count is the actual measure, not the DLQ message count (a DLQ'd event for a
  user with no active orders caused no visible harm, even though it was still silently dropped).
- **Repairing changes production state** (cancels real orders) — confirm each candidate user
  actually intended to withdraw (check `user_profiles.status` is still `WITHDRAWN`, not reactivated)
  before running §3's repair, per the runbook's own guidance.

## Failure Scenarios

- **F1 — reporting "no impact" from an environment where the evidence already expired.** Guarded by
  AC-1's requirement to report retention/absence explicitly rather than treating it as a clean
  negative.
- **F2 — re-fixing the already-fixed code defect under this ticket**, duplicating `TASK-BE-533`'s
  change and creating two "who owns this" commits. Guarded by AC-0 and the explicit out-of-scope
  note.
- **F3 — skipping the repair step because the count is inconvenient.** If AC-2 finds affected users,
  AC-3 requires the repair to actually be run and verified, not just reported.
