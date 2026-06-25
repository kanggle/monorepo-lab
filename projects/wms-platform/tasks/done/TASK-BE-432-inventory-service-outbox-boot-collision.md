# TASK-BE-432 — inventory-service can't boot: shared OutboxAutoConfiguration bean collision

**Status:** done
**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (one-line exclude, established pattern)

## Goal

`inventory-service` fails to start as a real app:

```
APPLICATION FAILED TO START
The bean 'outboxPublisher', defined in class path resource
[com/example/messaging/outbox/OutboxAutoConfiguration.class], could not be registered.
A bean with that name has already been defined in
[com/wms/inventory/adapter/out/messaging/OutboxPublisher.class] and overriding is disabled.
```

Root cause: inventory-service ships its OWN `@Component OutboxPublisher`
(`com.wms.inventory.adapter.out.messaging.OutboxPublisher`, extends `AbstractOutboxPublisher`)
AND pulls `libs/java-messaging`'s `OutboxAutoConfiguration`, whose
`@Bean outboxPublisher` (type `com.example.messaging.outbox.OutboxPublisher`,
`@ConditionalOnMissingBean` **by type**) does not see inventory's differently-typed
local bean. Both then register under the same bean name `outboxPublisher` →
`BeanDefinitionOverrideException` (overriding disabled by default).

`outbound-service` hit the identical problem and fixed it in **TASK-BE-333** via
`@SpringBootApplication(exclude = OutboxAutoConfiguration.class)`. inventory-service was
never given the same exclude.

**Why it was never caught**: the slice/unit tests don't load the auto-config, and there is
no inventory full-context (Testcontainers) job in CI — so no test ever boots the real
context. It surfaced only when the service was brought up as a real container in the
TASK-BE-431 ecommerce↔wms fulfillment-loop demo.

## Scope

- `InventoryServiceApplication`: add `@SpringBootApplication(exclude = OutboxAutoConfiguration.class)`
  (mirrors outbound-service / TASK-BE-333). inventory's own outbox stack
  (`OutboxPublisher` + `OutboxWriterAdapter` over `InventoryOutboxJpaEntity`, local
  `OutboxWriter` port) is self-contained — no libs auto-config bean is used.

## Acceptance Criteria

- AC-1: inventory-service boots a non-`standalone` full context without
  `BeanDefinitionOverrideException`.
- AC-2: its own outbox still works — `OutboxPublisher` relays `InventoryOutboxJpaEntity`
  rows (e.g. `inventory.reserved` after a reservation). **Live-verified in the BE-431
  demo**: with the auto-config excluded, inventory booted, reserved stock, and emitted
  `inventory.reserved` (driving the outbound saga to `RESERVED`).
- AC-3: `:inventory-service:test` stays GREEN (168).

## Related

- TASK-BE-333 (outbound-service applied the same exclude — the reference pattern)
- TASK-BE-431 (the fulfillment-loop demo that surfaced this)
- `libs/java-messaging/.../OutboxAutoConfiguration.java`

## Follow-up (out of scope, noted)

inventory-service has no full-context (Testcontainers) CI job, so this class of boot
regression is unguarded in CI. Consider adding inventory-service to a wms Testcontainers
integration job (its `PickingFlowIntegrationTest` already loads the full context and would
catch it — and would also run the BE-431 real-wire reservation IT on CI).
