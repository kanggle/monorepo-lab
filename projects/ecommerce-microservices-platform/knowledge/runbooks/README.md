# Runbooks

On-call recovery procedures for ecommerce-microservices-platform.

Created by TASK-BE-533 to close the `knowledge/runbooks/` gap that
[`ADR-006`](../../docs/adr/ADR-006-at-least-once-delivery-policy.md) named as a required
mitigation for both of its Scenario B decisions.

---

# Contents

| Runbook | Triggered by | Covers |
|---|---|---|
| [`notification-dlq-replay.md`](notification-dlq-replay.md) | `NotificationDeliveryFailureRateHigh` / `Critical`, or a consumer error spike | Draining and replaying `<topic>.dlq` for notification-service |
| [`user-withdrawn-not-cancelled.md`](user-withdrawn-not-cancelled.md) | `UserWithdrawnEventPublishFailure` | Finding users withdrawn in user-service whose orders were never cancelled, and repairing them |

---

# Writing rule

A runbook is read at 3am by someone who did not write it. Every command must be
**copy-pasteable as written** — no placeholder that the reader is expected to already know how to
fill, no "connect to the DB" without saying which container and which credentials, no step whose
success criterion is left implicit. If a step needs a value the reader must supply, the runbook
says exactly where to get it.

State the environment each command assumes. The commands here assume the local
`docker-compose.yml` stack (container names `ecommerce-*`); for a deployed environment substitute
the equivalent host but keep the arguments.

---

# Priority

`knowledge/` is lower priority than all `specs/` documents (see [`../README.md`](../README.md)).
A runbook describes how to recover; it does not define policy. If a runbook conflicts with a spec,
the spec wins and the runbook is the thing that needs fixing.
