# Event Contracts — platform-console

Index of this project's event contracts and the project's declared choices for the five decisions [`platform/event-driven-policy.md`](../../../../../platform/event-driven-policy.md) delegates to `specs/contracts/events/README.md`. This file states what the code does today; it does not restate the platform rule body.

**Source of this census**: `PROJECT.md`, `specs/contracts/console-integration-contract.md`, live code search across `apps/**` for Kafka producer/consumer patterns, TASK-MONO-415 (2026-07-15).

---

## This project does not publish domain events.

`platform-console` is an explicitly stateless REST-orchestrating BFF (per `PROJECT.md`, ADR-MONO-013, ADR-MONO-017). It server-side fans out to each domain's existing read APIs (iam, wms, scm, erp, finance, ecommerce) and returns aggregated results; write operations are delegated to the domain APIs themselves. This is an architecturally-confirmed negative, not an oversight:

- No `events/` subdirectory previously existed under `specs/contracts/`, and no event contract files exist to index.
- No `@KafkaListener`, no `KafkaTemplate`, no Kafka gradle dependency, no Kafka docker-compose service, and no Kafka config keys exist anywhere in `apps/`. Every "kafka" string match in the codebase is a comment or Javadoc explicitly stating the *absence* of Kafka, e.g.:
  - `application.yml`: *"No datasource / JPA / Flyway / Redis / Kafka configuration (stateless BFF)"*
  - `ConsoleBffApplication.java` Javadoc: *"No persistence (no JPA / Flyway / Redis / Kafka)"*
  - `AbstractConsoleBffIntegrationTest.java` Javadoc: *"console-bff is stateless (no DB / Kafka / Redis)"*
- This project's declared invariants (ADR-MONO-017 D1–D8) forbid introducing persistence or messaging into console-bff; it is a synchronous REST-only aggregator by design.

The five delegated decisions (topic naming, `eventType` naming, serialization, schema registry, contract index) therefore have no applicable answer for this project — there is nothing to declare. If a future ADR changes console-bff's stateless-BFF architecture to include event publication or consumption, this file should be replaced with a real declaration at that time.

0 is a result.
