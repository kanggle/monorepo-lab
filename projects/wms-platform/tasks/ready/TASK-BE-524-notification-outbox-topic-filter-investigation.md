# TASK-BE-524 — notification-service: outbox may publish `notification.delivery.scheduled` to Kafka contrary to contract — VERIFY

- **Type**: TASK-BE (INVESTIGATION-first — possible contract violation, needs runtime confirmation)
- **Status**: ready
- **Service**: notification-service (wms-platform)
- **Domain/traits**: wms / [event-driven, transactional]
- **Analysis model**: Opus 4.8 · **Impl model**: TBD after AC-0
- **⚠️ INVESTIGATION-FIRST**: this is a CODE-READING finding, NOT yet runtime-confirmed. The reserve/onion
  lessons apply — static reading of a poller can mislead; a live/IT probe is authoritative. Confirm before
  fixing.

## Observed (2026-07-19 wms audit — code-reading hypothesis)

`OutboxPollingScheduler.publishPending()` publishes **every** unpublished `notification_outbox` row to the
single configured topic, with (per the audit's read) **no filter on `event_type`**. But `OutboxWriterAdapter`
writes rows for BOTH `notification.delivery.scheduled` (on PENDING creation) AND `notification.delivered`
(terminal). The contract `notification-events.md` § Out of Scope states `notification.delivery.scheduled`
is NOT published to Kafka in v1 — "the outbox writes the row but the publisher's topic resolver only forwards
`notification.delivered`." If no such topic resolver/filter exists in the poller, every `.scheduled` row is
being forwarded too — a contract violation. AND there is currently **no test (unit/slice/IT) asserting what
actually lands on `wms.notification.delivered.v1`**, so the mismatch (if real) is invisible.

## Acceptance Criteria

- **AC-0 (gate, runtime-confirmed)**: with a real Kafka (Testcontainers), drive a delivery through the outbox
  and observe the actual topic + the actual set of `event_type`s that land. Confirm whether `.scheduled` rows
  reach Kafka. Do NOT trust the static read — the poller may have a filter the audit missed, or `.scheduled`
  rows may never be written in the exercised path.
- **AC-1 (if confirmed a violation)**: add the topic-resolver/event-type filter so only `notification.delivered`
  is forwarded, per the contract; ADD the missing IT that asserts exactly `notification.delivered.v1` lands and
  `.scheduled` does not (the coverage whose absence hid this). RED→GREEN.
- **AC-2 (if refuted)**: record the refutation (the filter exists / `.scheduled` is never written on the live
  path) and ADD the missing end-to-end IT anyway (the published-event contract is currently unobserved).

## Related

- 2026-07-19 wms audit (notification-service section — this + the circuit-breaker / DeliveryRepo-persistence / SKIP-LOCKED CRITICAL coverage gaps).
- `OutboxPollingScheduler.java`, `OutboxWriterAdapter.java`, `notification-events.md` § Out of Scope.
- Memory: `project_finance_forbidden_onion_sweep_2026_07_18` (static read misleads; runtime log/IT is the judge), `env_empty_detector_output_is_not_absence`, `project_testcontainers_docker_desktop_blocker` (CI wms lane authoritative).
