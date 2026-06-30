# TASK-BE-461 — settlement-service fails to boot: duplicate `processedEventJpaRepository` bean (BE-415 outbox lib collides with the local processed-event repo)

**Status:** ready

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (outbox/dedupe wiring + a small design judgment on which `processed_event` owner survives)

---

## Goal

settlement-service's Spring `ApplicationContext` **fails to load** — it cannot boot. This was surfaced by TASK-MONO-319's first settlement integration-test CI run (all 16 `@Tag("integration")` ITs fail at context load):

```
org.springframework.beans.factory.support.BeanDefinitionOverrideException:
Invalid bean definition with name 'processedEventJpaRepository' defined in
com.example.messaging.outbox.ProcessedEventJpaRepository defined in @EnableJpaRepositories declared on OutboxJpaConfig:
Cannot register bean definition [...] since there is already
[... com.example.settlement.infrastructure.persistence.ProcessedEventJpaRepository
defined in @EnableJpaRepositories declared on SettlementServiceApplication] bound.
```

**Root cause.** settlement-service owns a *local* dedupe repository
`com.example.settlement.infrastructure.persistence.ProcessedEventJpaRepository`
(consume-path `processed_event` table), picked up by
`SettlementServiceApplication`'s `@EnableJpaRepositories(basePackages = "com.example.settlement.infrastructure.persistence")`.
TASK-BE-415 then made settlement a producer for `settlement.period.closed.v1` and
added `libs:java-messaging`, whose `OutboxAutoConfiguration → OutboxJpaConfig`
declares its own `@EnableJpaRepositories` over `com.example.messaging.outbox` —
registering `com.example.messaging.outbox.ProcessedEventJpaRepository`. Both
interfaces share the simple name `ProcessedEventJpaRepository` → the same default
bean name `processedEventJpaRepository`. Spring Boot disables bean-definition
overriding by default, so the second registration throws and the context never
starts.

This is **not a test-harness issue** — the production context wires the same two
`@EnableJpaRepositories` and would fail identically. The Docker-free `:check`
never boots the full context (ITs are `@Tag("integration")`-excluded), which is
why it stayed latent until MONO-319 added a Testcontainers `@SpringBootTest` lane.
This is the same class of defect as the inventory-service outbox boot collision
resolved by TASK-BE-432.

Fix settlement so it boots, then add its `integrationTest` CI lane (the MONO-319
pattern) so this can never regress silently.

## Scope

### In scope

1. **Resolve the duplicate `processedEventJpaRepository` bean** so the context
   loads. Pick the correct option after reviewing settlement's consume-path
   dedupe vs the lib's inbound-dedupe semantics (do not guess):
   - **(a)** Rename the local repository (e.g. `SettlementProcessedEventJpaRepository`)
     + its entity/usages so the bean name no longer collides — keeps settlement's
     own `processed_event` ownership intact (the comment in `SettlementServiceApplication`
     states the dedupe table is "owned locally"). Lowest-risk if the two repos map
     to different tables/semantics.
   - **(b)** Delete the local repo and consume the lib's `ProcessedEventJpaRepository`
     if it is functionally equivalent (same `processed_event` dedupe contract) —
     removes the shadow entirely.
   - **(c)** Exclude the lib's outbox JPA repo registration if settlement only
     *produces* (never uses the lib's inbound dedupe) — mirror the TASK-BE-432
     `OutboxAutoConfiguration` exclude approach.
   - ❌ **Not** `spring.main.allow-bean-definition-overriding=true` — that masks the
     collision and lets an arbitrary repo win (fragile; reject).
2. **Verify two `@Entity` classes do not both map to one `processed_event` table**
   in a conflicting way (settlement's entity vs the lib's). Reconcile if needed.
3. **Add settlement's `integrationTest` CI lane** once it boots green: a dedicated
   `tasks.register('integrationTest', Test)` block in
   `settlement-service/build.gradle` (copy the MONO-319 stanza from a sibling, e.g.
   `review-service`) + wire `:projects:ecommerce-microservices-platform:apps:settlement-service:integrationTest`
   into the `ecommerce-integration-tests` CI job in `.github/workflows/ci.yml`
   (gradle run step + the failure-upload `path:` list). The `SettlementOutboxRelayIntegrationTest`
   should also be pinned `@SpringBootTest(classes = SettlementServiceApplication.class)`
   (the other 3 settlement ITs already are) to match the MONO-319 convention.

### Out of scope

- Other ecommerce services' lanes (MONO-319 batches 1–3 already cover them).
- Any change to the `settlement.period.closed.v1` event contract / wire.

## Acceptance Criteria

- **AC-0 (reproduce)** — Confirm `:settlement-service:integrationTest` is RED on CI
  with `BeanDefinitionOverrideException` on `processedEventJpaRepository` (JUnit-XML
  artifact is the authority; the gh job log swallows the cause).
- **AC-1** — settlement-service `ApplicationContext` loads; all 16 settlement ITs
  execute (pass/fail on their own merits, no context-load error).
- **AC-2** — settlement's `integrationTest` lane is wired into the
  `ecommerce-integration-tests` CI job and the job is GREEN.
- **AC-3** — No regression to settlement's consume-path dedupe: the `processed_event`
  dedupe still prevents double-accrual (the `replayed_payment_does_not_double_accrue_AC6`
  IT must pass on its merits).
- **AC-4** — settlement-service still boots in the federation-hardening-e2e / nightly-e2e
  compose stack (the fix is a real prod-boot fix, not test-only).

## Related Specs

- `tasks/ready/TASK-MONO-319-...` (this lane rollout; settlement was deferred here) — repo-root `tasks/`.
- `tasks/done/TASK-MONO-307-ecommerce-integration-ci-lane.md` (the lane pattern).
- TASK-BE-415 (settlement became an outbox producer — the change that introduced the collision).
- TASK-BE-432 (the inventory-service outbox boot-collision precedent + `OutboxAutoConfiguration` exclude approach).
- ADR-MONO-004 § 5 (shared outbox library).
- `platform/testing-strategy.md`.

## Related Contracts

- `specs/contracts/events/` — `settlement.period.closed.v1` (unchanged; producer wire must be preserved).

## Edge Cases

- Two JPA `@Entity` classes (settlement-local + lib) targeting the same physical
  `processed_event` table → Hibernate mapping conflict or schema drift. Confirm the
  table ownership and that only one Flyway migration creates `processed_event`.
- If option (b)/(c) removes the local repo, ensure every local injection point
  (consume-path dedupe checker) still resolves to a working bean.
- Renamed bean (option a) must not collide with any other `*ProcessedEvent*` bean.

## Failure Scenarios

- **Silent dedupe loss** — deleting/replacing the local repo without preserving its
  exact dedupe query semantics could let replayed payment events double-accrue
  (money-safety). AC-3 guards this; verify against the real-DB IT, not a mock.
- **Masked-not-fixed** — using `allow-bean-definition-overriding=true` makes the
  context load but a non-deterministic repo wins; rejected in scope.
- **CI-RED-at-merge** — settlement must be GREEN on CI before its lane merges; a
  context-load failure left in the lane would red the whole `ecommerce-integration-tests`
  job for every subsequent ecommerce PR.

---

## Definition of Done

- AC-0…AC-4 satisfied.
- settlement-service boots; its `integrationTest` lane is GREEN on CI.
- Ready for review.
