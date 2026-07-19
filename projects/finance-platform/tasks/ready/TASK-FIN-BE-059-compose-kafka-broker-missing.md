# TASK-FIN-BE-059 (finance) — finance-platform compose has no Kafka broker: account→ledger event chain is dead in the local stack

**Status:** ready

**Type:** TASK-FIN-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet (compose + application.yml wiring; no domain logic, no schema change — but the live bring-up verification in AC-4 is the substance of the task, not the edit)

**Service:** finance-platform (docker-compose topology) — affects account-service + ledger-service

> **Origin.** Ad-hoc topology audit (2026-07-20) while answering "is Kafka one broker or one per platform?". Answer: **one KRaft broker per platform, 6 of them** (`infra/demo/README.md` counts "kafka×6" itself). finance-platform is the odd one out — its `docker-compose.yml` defines **no broker at all**, while both its services run Kafka in production code paths. This is not a design choice: `ledger-service` is wired to a hostname that does not resolve, and `account-service` is not wired at all.

---

## Goal

Make finance-platform's local stack actually carry its own domain events, by adding the missing KRaft broker and wiring both services to it — bringing finance in line with the 6 sibling platforms (ecommerce / wms / iam / scm / fan / erp).

The defect is that **the platform's core derivation chain is severed while the stack reports healthy**:

```
account-service ──outbox──▶ finance.transaction.{completed,reversed}.v1 ──▶ ledger-service
        │                              │                                          │
   sends to localhost:9092      (no broker exists)                    consumes from kafka:9092
   (no bootstrap-servers                                              (host does not resolve)
    configured at all)
```

`ledger-service` is described in `docker-compose.yml:143-147` as a terminal consumer that "consumes account-service transaction events (`finance.transaction.{completed,reversed}.v1`) and posts balanced journal entries". In the local stack it receives **zero** events, so the general ledger stays empty while every container reports `healthy`.

## Scope

**In scope (`projects/finance-platform/`):**

1. **Add the broker to `docker-compose.yml`** — single-node KRaft `apache/kafka:3.7.0`, `container_name: finance-platform-kafka`, on `finance-platform-net`, `expose:`-only (no host port, per Local Network Convention). Mirror the sibling definition in `projects/erp-platform/docker-compose.yml:381-409` (closest recent shape), **with the heap sizing correction in Edge Cases below — do not copy a 512M sibling verbatim.**
2. **Wire `account-service`** — add `KAFKA_BOOTSTRAP: ${KAFKA_BOOTSTRAP:-kafka:9092}` to its compose `environment` (currently absent entirely), and add `spring.kafka.bootstrap-servers: ${KAFKA_BOOTSTRAP:kafka:9092}` to `apps/account-service/src/main/resources/application.yml` (currently **no `bootstrap-servers` key exists anywhere in the service's resources** — it silently falls back to Spring's `localhost:9092` default, which inside the container points at itself).
3. **Verify `ledger-service` wiring** — it already has `KAFKA_BOOTSTRAP` (`docker-compose.yml:171`) and `spring.kafka.bootstrap-servers` (`application.yml:41`). Add `depends_on: kafka: condition: service_healthy` to both services, and a broker healthcheck.
4. **Confirm the demo harness** — check whether `infra/demo/projects.sh` / `infra/demo/README.md` need the new container registered (the README's "kafka×6" generic-key collision note becomes "kafka×7"; per-project `-p <slug>` namespacing should already absorb it, but the count/comment is now stale).

**Out of scope:**
- Any change to event payloads, topics, envelope shape, outbox logic, or consumer group config — this is pure infrastructure wiring.
- Cross-project event flow (finance does not participate in ADR-MONO-022's ecommerce↔wms loop).
- Adding a `kafka-ui` container (only wms + iam have one; consistency here is not a goal of this task).
- Dropping/retaining decisions on the v1 outbox tables (TASK-FIN-BE-045 Edge Case owns that).

## Acceptance Criteria

- **AC-1 (broker exists)** — `docker compose config` on `projects/finance-platform/` resolves a `kafka` service on `finance-platform-net`, `expose:`-only, no host port binding.
- **AC-2 (both services wired)** — `account-service` and `ledger-service` each resolve a non-default `bootstrap-servers` pointing at the in-network broker. Proven by `docker compose config` + `grep bootstrap-servers` over `apps/*/src/main/resources/` returning a hit for **both** services (today account returns zero).
- **AC-3 (no silent-UP regression)** — with the broker deliberately stopped, `account.outbox.pending.count` climbs and the failure is visible; document in the PR whether the service healthcheck should reflect Kafka reachability (see Edge Case "healthcheck masks it"). **Decision required, not necessarily implementation** — if the answer is "leave healthcheck as-is", say why.
- **AC-4 (live end-to-end — the real acceptance)** — bring the stack up, perform one account transaction that emits `finance.transaction.completed.v1`, and observe **(a)** the `account_outbox` row transitions to non-null `published_at`, **(b)** the topic exists on the broker, **(c)** a balanced journal entry appears in `finance_ledger_db`. Paste the three observations into the PR. A green build is **not** acceptance here — the entire defect class is "tests pass because Testcontainers supplies a broker the compose stack lacks".
- **AC-5 (no test-lane change)** — the Testcontainers integration suites are untouched and remain GREEN on the CI Linux runner (they were never affected; this AC guards against incidental edits to test config).
- **AC-6 (demo harness consistent)** — `infra/demo/` starts finance without generic-key collision, and any stale "kafka×6" count/comment is corrected.

## Related Specs / Contracts

- `projects/finance-platform/specs/contracts/events/finance-account-events.md` (§Topics — the 11 `finance.*` topics the outbox publishes).
- `projects/finance-platform/specs/contracts/events/finance-ledger-events.md`.
- `projects/finance-platform/specs/services/ledger-service/architecture.md` (consumer group `finance-ledger-v1`, terminal consumer stance).
- `platform/event-driven-policy.md` (Kafka as default broker; topic naming `{domain}.{aggregate}.{version}`).
- `TEMPLATE.md § Local Network Convention` (backing services are `expose:`-only on `<project>-net`; no host ports; no `PORT_PREFIX`).
- **Reference impl:** `projects/erp-platform/docker-compose.yml:381-409` (KRaft single-node broker), `projects/wms-platform/docker-compose.yml:107-139`.

## Edge Cases

- **🔴 Do not copy a 512M sibling heap.** A 512MiB Kafka container survives idle and dies only once a real service fleet attaches — this exact failure was diagnosed and fixed under TASK-MONO-397 (512M → 1G). Size the new broker at **1G** from the start. Note also that a JVM inside a container does not necessarily observe the cgroup limit (a 512MiB container has been observed reporting a 3998MiB heap), so an in-container "how much heap do I have" probe is **not** evidence — set the limit explicitly and assert the compose value statically.
- **The healthcheck masks the defect.** Both services healthcheck `/actuator/health`, which returns UP without Kafka reachability. This is *why* the gap survived: the stack looks fully healthy with a severed event chain. Whether to add a Kafka health indicator is a real trade-off (it makes the stack fail-closed on broker restart) — AC-3 requires a documented decision either way.
- **Failure is accumulate-silently, not crash.** The outbox pattern means no data is lost — rows sit in `account_outbox` with `published_at IS NULL`. The relay retries with exponential backoff (1s→30s cap) forever. So "nothing appears broken" is the expected symptom, and the only signal is the `account.outbox.pending.count` gauge, which nothing currently watches.
- **`account-service` default is worse than `ledger-service`'s.** ledger points at `kafka:9092` — a host that simply does not resolve. account has no config at all, so it points at `localhost:9092` — *inside its own container*. Both fail, but only the second would keep failing even after the broker is added, if step 2 is done in compose only and not in `application.yml`. **Do both.**
- **Generic container keys.** `kafka` as a service key already collides across 6 projects (`infra/demo/README.md` documents this); the demo harness handles it via `docker compose -p <slug>`. Adding a 7th must not introduce a host-port binding, which is what would actually collide.
- **Is this a gap or unfinished work?** The evidence says gap, not "not built yet": the production code, the Flyway outbox migrations (`V2__account_outbox_v2.sql`, `V3__create_ledger_outbox.sql`), the consumer config, the event contracts, and the compose comment describing the consumption relationship all exist and are complete. Only the broker is absent. If implementation surfaces evidence to the contrary (e.g. finance was never intended to run full-stack locally), **stop and report** rather than proceeding — the ticket's framing would be wrong.

## Failure Scenarios

- **F1 — declaring victory on `docker compose up` alone.** Containers going healthy proves nothing here; that is precisely the pre-existing state. Only AC-4's three observations (published_at, topic exists, journal row) close this task.
- **F2 — fixing compose but not `application.yml`.** account-service would still resolve `localhost:9092` and stay silently broken, with the stack now *looking* even more correct. The most likely way to half-fix this.
- **F3 — copying a sibling broker block verbatim including a 512M limit.** Passes idle, dies under the service fleet, and reproduces TASK-MONO-397 in a new project.
- **F4 — treating CI green as verification.** The Testcontainers suites supply their own per-service broker and have always been green *through this entire defect*. They cannot detect it and will not detect a regression of it. No CI job starts `projects/*/docker-compose.yml` at all.
- **F5 — scope creep into the outbox/consumer code.** The production Kafka code is correct and tested. Any diff under `src/main/java/` in this task is a signal that the diagnosis drifted.
