# Event Contracts — platform-console

Index of this project's event contracts and the project's declared choices for the five decisions [`platform/event-driven-policy.md`](../../../../../platform/event-driven-policy.md) delegates to `specs/contracts/events/README.md`. This file states what the code does today; it does not restate the platform rule body.

**Source of this census**: `PROJECT.md`, `specs/contracts/console-integration-contract.md`, live code search across `apps/**` for Kafka producer/consumer patterns, TASK-MONO-415 (2026-07-15); re-stated for the single remaining app after the former BFF service was retired (ADR-MONO-081, TASK-MONO-757).

---

## This project does not publish domain events.

`platform-console` is an explicitly stateless console: its `console-web` server fans out to each domain's existing read APIs (iam, wms, scm, erp, finance, ecommerce) and returns composed results (per `PROJECT.md`, ADR-MONO-013, ADR-MONO-017, ADR-MONO-081); write operations are delegated to the domain APIs themselves. This is an architecturally-confirmed negative, not an oversight:

- No `events/` subdirectory previously existed under `specs/contracts/`, and no event contract files exist to index.
- No Kafka client, producer or consumer, no Kafka docker-compose service, and no Kafka config keys exist anywhere in `apps/`. The word "kafka" appears in `apps/console-web` only as guide/label text describing the *domains'* messaging (`features/global-guide/data.ts`, `shared/sample/label-rule.ts`) and in the lock file (a transitive OpenTelemetry instrumentation package) — never as a client of this project.
- This project's declared invariants (ADR-MONO-017 D1–D8, carried into the console-web server by ADR-MONO-081 D2) keep cross-domain composition a synchronous, stateless REST read aggregation; it introduces no persistence and no messaging.

The five delegated decisions (topic naming, `eventType` naming, serialization, schema registry, contract index) therefore have no applicable answer for this project — there is nothing to declare. If a future ADR changes the console's stateless architecture to include event publication or consumption, this file should be replaced with a real declaration at that time.

0 is a result.
