# Task ID

TASK-MONO-054

# Title

ADR-MONO-005 — Saga Timeout / Escalation / Dead-Letter Policy (per-saga audit + 4-category taxonomy + per-service spec edits)

# Status

ready

# Owner

backend / monorepo

# Task Tags

- spec
- adr
- saga
- cross-cutting

---

# Goal

Establish a **monorepo-wide policy** for saga / long-running flow timeout,
attempt-cap, escalation event, and dead-letter terminal — closing the
operational-contract gap that the publish-side `ADR-006` (ecommerce,
2026-05-11) explicitly left open.

The most mature reference (outbound-service `SagaSweeper`, TASK-BE-050)
co-exists with bare synchronous flows (`PaymentConfirmService` — no
Resilience4j wrap) and a choreographed flow with no stuck-detector
(ecommerce `order → payment → order`). The status quo is "every saga
invents its own conventions"; this ADR records the policy so future
sagas inherit it and the existing seven can be audited against a single
checklist.

This is a **cross-cutting design narrative**, not an implementation
task — the ADR PR ships specs + ADR only. Real code changes
(Resilience4j wrap for Toss Payments adapter) land in separate
follow-up tasks (TASK-BE-138 / 139 / 140) per the standard PR
Separation Rule.

---

# Scope

## In Scope (this task / impl PR)

1. **ADR-MONO-005** at `docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md`
   carrying:
   - Audit table of the 7 in-scope flows (outbound saga, ecommerce order
     saga, ecommerce refund saga, scm procurement, payment confirm,
     notification delivery, inventory reservation TTL) + fan-platform
     `N/A v1`.
   - **D1** — 4-category taxonomy (A multi-step saga, B synchronous
     external, C single-step retry+DLT, D TTL expiry sweep).
   - **D2** — generic policy across all categories (timeout declaration,
     Resilience4j wrap for external sync calls, idempotent re-emission,
     OL on saga rows).
   - **D3–D5** — category-specific sub-rules (sweeper cap = 5, grace
     ≥ 60 × p99 step latency floor 60 s default 300 s, escalation event
     `<service>.alert.saga.recovery.exhausted`, metric naming
     convention).
   - **D6** — per-saga current-state decisions (Scenario A compliant /
     B gap-needs-follow-up / N/A v1) matching ADR-006 pattern.
   - **D7** — declared spec surface (per-service `architecture.md`
     "Saga / Long-running Flow" section + pointers into
     `rules/traits/transactional.md` + `platform/event-driven-policy.md`).
     The narrative IS in the ADR; the surface edits land in a separate
     follow-up bundle (see Outstanding follow-ups below).
   - Outstanding follow-ups: **TASK-BE-138** (DEFERRED — ecommerce
     order stuck-detector), **TASK-BE-139** (READY — Toss Payments
     adapter Resilience4j wrap, gates ADR ACCEPTED), **TASK-BE-140**
     (DEFERRED — `inventory.reservation.expiry.swept.total` metric),
     **TASK-MONO-055** (READY — spec surface bundle: 6 architecture.md
     edits + 2 rule pointer edits per D7).
2. `docs/adr/INDEX.md` row added (status PROPOSED, date 2026-05-11).

## Out of Scope (defer to follow-up tasks)

- **Per-service `architecture.md` edits** — declared in D7 but bundled
  into a separate **TASK-MONO-055** so the policy-narrative ADR PR
  stays focused on the decision. The follow-up touches 6 service spec
  files across 3 projects.
- **Rule-pointer edits** in `rules/traits/transactional.md` and
  `platform/event-driven-policy.md` — bundled into the same
  **TASK-MONO-055** since they sit on the same D7 surface.
- **Production code changes** (Resilience4j wrap for
  `TossPaymentsAdapter`, order stuck-detector cron, reservation
  metric) — **all in TASK-BE-138 / 139 / 140**.
- Library abstraction `libs/java-messaging.saga` — explicitly rejected
  in ADR § 3 (Alternatives Considered).
- Temporal / Camunda runtime adoption — explicitly rejected in ADR § 3.
- ecommerce ADR-007 (project-internal mirror) — rejected in favour of
  the monorepo-level location (saga semantics span 3+ projects).

---

# Related Specs

- `platform/event-driven-policy.md` (§ Consumer Rules, § DLQ Policy,
  § Retry Policy)
- `platform/error-handling.md` (existing `EXTERNAL_SERVICE_UNAVAILABLE`,
  `DELIVERY_RETRY_EXHAUSTED`)
- `rules/traits/transactional.md` §T2 / T5 / T6 / T8
- `rules/traits/integration-heavy.md` §I1 / I2 / I3 / I5 / I9
- `projects/ecommerce-microservices-platform/docs/adr/ADR-006-at-least-once-delivery-policy.md` —
  publish-side at-least-once (this ADR is the consume-side analog)
- `projects/ecommerce-microservices-platform/docs/adr/ADR-002-saga-over-distributed-transaction.md`
- `projects/wms-platform/tasks/done/TASK-BE-050-outbound-saga-sweeper.md` —
  Category A reference impl history
- `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` — outbox /
  publisher transport (this ADR sits on top)

# Related Contracts

- New per-service `<service>.alert.saga.recovery.exhausted` events
  (already exists for `outbound`; named in the policy for future
  Category A flows). Schema fields: `sagaId`, `aggregateId`,
  `lastState`, `attemptCount`, `lastTransitionAt`, `failureReason`.
  Published under each owning project's
  `specs/contracts/events/<aggregate>.md`.

---

# Acceptance Criteria

- AC-01: `docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md`
  exists with Status: PROPOSED, D1–D7 sections, per-saga decision
  table, Outstanding follow-ups section referencing
  TASK-BE-138 / 139 / 140 + TASK-MONO-055.
- AC-02: `docs/adr/INDEX.md` carries a row for MONO-005 with the
  agreed status (PROPOSED) + date 2026-05-11.
- AC-03: `./gradlew check` is unchanged from main baseline (spec /
  docs only — zero production code touched, zero spec / rule files
  touched beyond `docs/adr/`).
- AC-04: Conventional Commit scope: `feat(adr)` — the ADR is the
  only deliverable in the impl PR for this task. Per-service
  `architecture.md` edits live in TASK-MONO-055 and use
  `feat(rules)` / `feat(<project>)` scopes there.

---

# Edge Cases

- **EC-01**: A future saga that doesn't cleanly fit one of the 4
  categories. Resolution: declare the closest category in
  `architecture.md` and add a note explaining the deviation. If three
  or more deviations accumulate, propose an ADR amendment introducing
  a fifth category.
- **EC-02**: An existing Category A flow whose current metric names
  drift from the D3 catalog (e.g. uses `outbound.sweeper.*` instead of
  `outbound.saga.sweeper.*`). Resolution: this ADR documents the
  catalog as forward-looking; existing names are grandfathered
  until a dedicated rename task. Outbound's existing names already
  match the catalog, so no rename is needed today.
- **EC-03**: ADR-MONO-003 D4 churn-clock impact. This PR edits
  `rules/traits/transactional.md` and `platform/event-driven-policy.md`,
  which under a strict reading would reset the churn clock. The
  pointers being added are 1-line cross-references, not structural
  rule changes; the active D4 OVERRIDE precedent (PR #328) covers this
  shape of edit. Phase 5 re-evaluation date stays on the existing
  target.

---

# Failure Scenarios

- **FS-01**: Reviewer prefers ecommerce ADR-007 (project-internal)
  over monorepo-level ADR-MONO-005. Resolution: document the
  cross-project saga reach in the ADR Context (already done in
  § 1.1); if rejected, split into 3 per-project ADRs as the
  fallback — but this multiplies the spec surface and creates the
  drift this ADR exists to prevent.
- **FS-02**: Spec edits in 6 service `architecture.md` files conflict
  with concurrent in-progress feature work. Resolution: rebase the
  conflicting branches against this spec PR after merge; the new
  section is additive (no existing content rewritten).
- **FS-03**: TASK-BE-139 (Toss Payments Resilience4j wrap) discovers
  a deeper PG gateway design issue (e.g. Toss Payments has its own
  idempotency-key constraint that conflicts with the wrap). Mitigation:
  the ADR explicitly notes Status: PROPOSED until TASK-BE-139 lands;
  if the impl task reveals a blocker, the ADR is revised before
  ACCEPTED.

---

# Notes / Open Questions

- Whether to emit a structured escalation event for Category C
  (notification) or rely on the existing `notification.delivered.v1`
  outbox event with `outcome=FAILED_RETRY_EXHAUSTED`. ADR chooses the
  latter (cosmetic — the existing event already carries the right
  data). Reviewer may prefer a dedicated `notification.alert.*` event
  for uniformity with Category A; flag in review.
- Whether to formalise the **5-attempts cap** as MUST or SHOULD.
  Current ADR draft uses MUST with default override via `@Value`.
  Pre-existing flows (outbound = 5, notification = 5) already match.
- The audit table snapshot rots — `architecture.md` per-service
  entries are the durable source. The ADR explicitly states this in
  § 4.2 so future maintainers don't trust the snapshot.

---

# Recommendation

진행 권장 (분석=Opus 4.7 / 구현 권장=Opus — cross-cutting design
narrative, 7-saga audit + policy taxonomy, ADR-grade narrative
spanning 4 projects)
