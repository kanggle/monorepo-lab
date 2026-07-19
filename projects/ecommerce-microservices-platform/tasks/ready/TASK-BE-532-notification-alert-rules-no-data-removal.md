# TASK-BE-532 — Remove (or explicitly disable) the notification alert rules that watch metrics no code emits

**Status:** ready

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Haiku 4.5 (delete/annotate a YAML rule group; no application code)

> Split from an ADR↔implementation drift audit (2026-07-20). This is the **stop-the-false-signal half**. The
> build-the-metrics half is [`TASK-BE-533`](TASK-BE-533-notification-delivery-metrics-and-runbook.md). They are
> deliberately separate so that a delay on 533 does not leave the false coverage standing.

---

## Goal

`projects/ecommerce-microservices-platform/infra/prometheus/alert-rules.yml` defines a `notification_delivery`
rule group whose expressions divide by `notification_failed_total` and `notification_sent_total`:

```yaml
# alert-rules.yml:214-252
- alert: NotificationDeliveryFailureRateHigh
  expr: sum(rate(notification_failed_total[5m])) / (… + sum(rate(notification_sent_total[5m]))) > 0.1
```

**No code emits either metric.** As measured on 2026-07-20, the two names appear in exactly three places in
the repo — this alert file, `specs/services/notification-service/observability.md`, and
`tasks/done/TASK-BE-117.md` — and `notification-service/src/main` contains **zero** occurrences of
`MeterRegistry`, `Counter.builder`, or any Micrometer registration.

These alerts are therefore permanently **no-data**: they can never fire, at any failure rate. That is worse
than having no rule at all, because a reader opening `alert-rules.yml` concludes that notification-delivery
failure is monitored. It is the repo's known "a skipped job reports green" failure mode wearing an
observability costume.

This task removes the false signal. It does **not** build the metrics (that is 533).

## Scope

**In scope:**

1. `infra/prometheus/alert-rules.yml` — for every rule in the `notification_delivery` group whose expression
   references a metric with no emitter, either:
   - **(preferred)** delete the rule, leaving a short comment naming `TASK-BE-533` as the task that will
     restore it once the metrics exist; or
   - if the reviewer prefers to preserve the expression, comment the rule out **in full** with the same pointer.
   Either way the deployed rule set must stop asserting coverage that does not exist.
2. `specs/services/notification-service/observability.md` — mark the affected metrics as **not yet emitted**
   (planned, `TASK-BE-533`) rather than describing them as live.

**Out of scope:**

- Writing the Micrometer counters, the runbook, or any `notification-service` application code → `TASK-BE-533`.
- Any other rule group in `alert-rules.yml` (`high_error_rate`, `slow_response_time`, `service_down`,
  `kafka_consumer_lag`, `db_connection_pool`) — unless AC-0's recount finds the same no-emitter defect there,
  in which case report it and let the reviewer decide whether to widen scope.
- Amending `docs/adr/ADR-006-at-least-once-delivery-policy.md` itself. The ADR gated its own ACCEPT on these
  mitigations; recording that the gate was missed is a decision for the ADR owner, not this cleanup.

## Acceptance Criteria

- **AC-0 (gate — re-measure; the code wins)** — Do not inherit this ticket's numbers. Independently recount, at
  start of work: (a) grep `notification-service/src/main` for `MeterRegistry|Counter|meterRegistry|@Timed|
  micrometer` — sanity-check the pattern against a service that *does* emit metrics (e.g. `user-service`
  `UserMetrics.java`) so an empty result is proven to mean absence and not a broken pattern; (b) enumerate
  **every** rule in the `notification_delivery` group and, for each referenced metric name, grep the whole
  `projects/ecommerce-microservices-platform` tree for an emitter. If any rule turns out to have a live
  emitter, **it stays** — this task only removes rules that are provably no-data. If *all* of them turn out
  to have emitters, **STOP and report**: the premise is wrong.
- **AC-1** — Every rule confirmed no-data in AC-0 is deleted (or fully commented out) and carries a pointer to
  `TASK-BE-533`. No rule remains that references a metric with no emitter.
- **AC-2** — `observability.md` no longer presents the un-emitted metrics as live; they are marked planned with
  the `TASK-BE-533` pointer.
- **AC-3** — The rule file still parses: `promtool check rules infra/prometheus/alert-rules.yml` passes (or, if
  `promtool` is unavailable in the environment, a YAML parse + a note in the PR body saying which check was run).
- **AC-4** — The PR body states the AC-0 recount explicitly: how many rules were in the group, how many were
  confirmed no-data, how many were kept, and the sanity-check used to prove the empty greps.

## Related Specs

- `projects/ecommerce-microservices-platform/specs/services/notification-service/observability.md`
- `projects/ecommerce-microservices-platform/docs/adr/ADR-006-at-least-once-delivery-policy.md`
  (§ notification-service Scenario B "Required mitigations" — the origin of the never-built counter)

## Related Contracts

- None. This task touches no API or event contract.

## Edge Cases

- **A rule references a metric emitted by a *different* service** — `sum(rate(...))` without a `service` label
  selector would still be satisfied by another service's series. AC-0's grep is repo-wide, not
  notification-service-only, precisely to catch this; a metric emitted anywhere is not no-data.
- **Recording rules / dashboards depend on the deleted alerts** — grep Grafana dashboard JSON and any
  recording-rule files for the alert names before deleting, so a dashboard panel does not silently break.
- **`NotificationSenderDown` and any other rule in the same group** may be legitimately backed by
  `up{}`-style metrics that Prometheus itself provides rather than application code. Do not delete a rule
  merely because its metric is absent from application source — AC-0 asks for an emitter *anywhere*, including
  the scrape layer.

## Failure Scenarios

- **F1 — over-deletion** — removing a rule that actually works leaves a real gap. Guarded by AC-0's
  per-rule, repo-wide emitter check and by AC-1's "only rules confirmed no-data".
- **F2 — the empty grep lies** — a mistyped pattern returns zero hits and manufactures a false finding. Guarded
  by AC-0's mandatory sanity-check against a known-emitting service.
- **F3 — the false signal is merely relocated** — deleting the rule but leaving `observability.md` describing
  the metrics as live moves the untrue sentence rather than removing it. Guarded by AC-2.
