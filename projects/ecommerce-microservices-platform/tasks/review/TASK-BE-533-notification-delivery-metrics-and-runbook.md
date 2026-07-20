# TASK-BE-533 — Build the notification delivery metrics + DLQ runbook that ADR-006 gated its own ACCEPT on

**Status:** review

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (Micrometer wiring across a small service + a runbook doc)

> Split from an ADR↔implementation drift audit (2026-07-20). This is the **build-the-mechanism half**;
> [`TASK-BE-532`](TASK-BE-532-notification-alert-rules-no-data-removal.md) removes the false alert coverage
> first and independently. 532 does not depend on this task; **this task should land after 532** so the alert
> rules are restored against metrics that provably exist.

---

## Goal

`docs/adr/ADR-006-at-least-once-delivery-policy.md` lists, under notification-service Scenario B, a set of
**"Required mitigations"** — items the ADR text gates its own acceptance on:

> "Add an `email_send_failure_total` Micrometer counter (currently the failure path is logged-only)."
> "Document the DLQ replay procedure … in `knowledge/runbooks/`."

and, under user-service Scenario B:

> "Confirm a Grafana alert on `user_event_publish_failure_total{event_type="UserWithdrawn"} > 0` in any
> 5-minute window." / "Add a withdrawn-but-not-cancelled ops query in `knowledge/runbooks/`."

The ADR is **ACCEPTED (2026-05-11)**. As measured on 2026-07-20 none of the four had landed:
`notification-service/src/main` has no Micrometer registration at all (not even the "logged-only" failure path
the ADR describes — `EmailNotificationSender.java` has no try/catch), `knowledge/runbooks/` does not exist
anywhere in the project, and `alert-rules.yml` has no `event_publish_failure` / `UserWithdrawn` rule group.

The gate was declared and then passed through. This task closes it.

Note the asymmetry to preserve: user-service **does** already emit the generic
`event_publish_failure_total{service=…}` tap (`UserMetrics.java`) — there the missing piece is the *alert*
and the *runbook*, not the metric.

## Scope

**In scope:**

1. **notification-service delivery metrics** — wire Micrometer counters covering send success and send failure,
   labelled by channel and failure reason, such that the failure-rate expression ADR-006 intends is
   computable. Align the metric names with whatever `TASK-BE-532` left recorded in
   `specs/services/notification-service/observability.md` (and with the names the previously-removed alert
   rules used, unless AC-1 finds a reason to rename — if renaming, update the spec in the same PR).
2. **The failure path itself** — `EmailNotificationSender` (and sibling senders, if any) must actually observe
   failures, i.e. the exception path has to be reachable and counted rather than escaping uninstrumented.
3. **Restore the alert rules** removed by `TASK-BE-532`, now backed by real emitters.
4. **user-service alert** — the `UserWithdrawn` publish-failure alert rule ADR-006 requires, over the existing
   `event_publish_failure_total` tap.
5. **`knowledge/runbooks/`** — create the directory and the two documents ADR-006 names: the notification DLQ
   replay procedure, and the withdrawn-but-not-cancelled ops query.

**Out of scope:**

- Changing the at-least-once delivery semantics or any consumer's idempotency mechanism. The audit sampled 20+
  of the 34 live consumers and found idempotency genuinely enforced in every sampled case — do not "fix" it.
- The separate ecommerce idempotency-key finding (ADR-002 D3, `Idempotency-Key` optional on the order path,
  absent on product-service stock endpoints). That is its own investigation with its own population count.
- Amending ADR-006 itself, or back-dating its ACCEPT gate.

## Acceptance Criteria

- **AC-0 (gate — re-measure; the code wins)** — Do not inherit this ticket's claims. At start of work,
  independently re-verify: (a) which of the four required mitigations are still missing (some may have landed
  between audit and start); (b) whether `notification-service` has acquired any metrics; (c) whether
  `knowledge/runbooks/` now exists. Sanity-check every "absent" grep against a known-present sibling
  (`user-service` `UserMetrics.java` emits metrics; use it to prove the pattern works) before concluding
  absence. Implement only what is genuinely still missing; report anything already done.
- **AC-1** — notification-service emits send-success and send-failure counters, and a failing send provably
  increments the failure counter — demonstrated by a test that drives the failure path, not merely by the
  bean's existence. A registered `MeterRegistry` is not the mechanism being active.
- **AC-2** — The failure path is instrumented at the point where a real send failure surfaces; a send that
  throws does not escape uncounted.
- **AC-3** — The alert rules removed by `TASK-BE-532` are restored, and each references a metric with a
  demonstrated emitter. State in the PR body which metric each restored rule depends on and where it is emitted.
- **AC-4** — A `UserWithdrawn` publish-failure alert exists over `event_publish_failure_total`, matching the
  window ADR-006 specifies.
- **AC-5** — `knowledge/runbooks/` contains the DLQ replay procedure and the withdrawn-but-not-cancelled ops
  query. Each is executable as written: a reader following it needs no knowledge that is not in the document.
- **AC-6** — `promtool check rules` (or the equivalent parse check noted in the PR body) passes; the affected
  services build and their tests are GREEN.

## Related Specs

- `projects/ecommerce-microservices-platform/specs/services/notification-service/observability.md`
- `projects/ecommerce-microservices-platform/docs/adr/ADR-006-at-least-once-delivery-policy.md`
  (§ notification-service Scenario B, § user-service Scenario B — "Required mitigations")
- `platform/observability-policy.md` (metric naming / label conventions — read before choosing names)

## Related Contracts

- None directly. Metric names are an operational surface, not a published contract, but they ARE depended on by
  `alert-rules.yml` and any dashboard — treat a rename as a breaking change to those consumers.

## Edge Cases

- **Metric-name divergence** — the previously-removed alert rules used `notification_failed_total` /
  `notification_sent_total`. If `platform/observability-policy.md` mandates a different convention, the policy
  wins, but then the spec, the restored rules, and any dashboard must all move together in one PR.
- **Cardinality** — labelling failures by raw exception message or recipient address would explode series
  cardinality. Label by channel and a bounded reason enum.
- **Fail-soft senders** — if a sender swallows exceptions to avoid blocking a flow, the counter must be
  incremented inside the swallow, or the failure becomes invisible again in a new way.
- **The alert restored, but never exercised** — a rule that parses is not a rule that fires. Where practical,
  state in the PR how the rule was observed to evaluate against real series.

## Failure Scenarios

- **F1 — the counter exists but nothing increments it** — a `Counter` bean registered and never called
  reproduces the original defect one layer deeper: the alert now has a series, permanently at zero. Guarded by
  AC-1's requirement that a test drive the failure path.
- **F2 — restoring the alert against a renamed metric** — rule and emitter disagree, and the alert is no-data
  again. Guarded by AC-3's "state which metric each rule depends on and where it is emitted".
- **F3 — runbook written but not executable** — a runbook that assumes tribal knowledge fails at 3am, which is
  the only time it is read. Guarded by AC-5.
- **F4 — scope creep into consumer idempotency** — ADR-006 is a delivery-semantics ADR and it is tempting to
  "harden" consumers while here. The audit found them sound; unrequested changes there risk regressions in
  code that is currently correct.
