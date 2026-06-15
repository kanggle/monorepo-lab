# Intentional / non-libs-eligible infrastructure "duplication"

A periodic refactor sweep (TASK-MONO-270, 2026-06-15) evaluated the structurally-similar
infrastructure classes that appear across the ecommerce services as candidates for promotion
into a shared `libs/` module. **Most are intentional or policy-blocked and must NOT be promoted.**
This note records the rationale so future sweeps do not re-flag them.

## Promoted (the one clean candidate)

- **JWT-Bearer OpenAPI scheme** — the `bearerAuth` Swagger security scheme + token-input hint,
  formerly copy-pasted into each service's `OpenApiConfig`, is now built by the shared
  `com.example.web.openapi.BearerJwtOpenApi` (`libs/java-web`). Domain-agnostic, policy-clean
  (swagger-core *models* only, `compileOnly` → no spring-web/servlet leak). Each service keeps
  its own `@Bean` and passes only title/description/version.

## NOT promoted — intentional per-service (do not consolidate)

- **`EventDeduplicationChecker` / `ProcessedEventCleanupScheduler`** (order-service, shipping-service).
  `libs/java-messaging`'s `EventDedupePort` states the design explicitly: *"the persistence
  implementation lives in each service's adapter layer because the dedupe table's retention policy
  and tenant scoping is service-specific."* Authority: **`rules/traits/transactional.md` §T8**.
  Promoting the concrete impl/cleanup into the lib would contradict the idempotent-consumer rule
  and would spread a 30-day-retention DELETE scheduler into services that must not run it.

## NOT promoted — project-specific (HARDSTOP-03 if moved to libs/)

- **`TenantContext` / `TenantContextFilter`** — carry the ecommerce default tenant (`"ecommerce"`);
  moving verbatim into a shared lib would put project-specific content in `libs/`
  (`platform/shared-library-policy.md`, HARDSTOP-03). The duplication across services is accepted.
- **`SellerScopeContext` / `SellerScopeContextFilter`** — the inner marketplace seller axis
  (ADR-MONO-030); a domain concept specific to ecommerce. Same HARDSTOP-03 reasoning.

## NOT promoted — per-service variance / no live duplication

- **`KafkaConsumerConfig`** (×9) — the base DLQ/back-off shape is similar, but the not-retryable
  exception sets differ per service; there is no single clean shape to extract without a
  customization seam that would outweigh the saving.
- **`Email` value object / `UserWithdrawnEvent`** — only **one live copy** each; the second copy
  lives in the build-excluded, decommissioned auth-service (TASK-BE-132), so there is no live
  duplication to remove.

## Rule of thumb for the next sweep

Shared promotion is justified only when the class is (a) domain-agnostic, (b) free of
project-specific defaults/identifiers, and (c) not governed by a rule that mandates a per-service
implementation (e.g. transactional.md §T8). When in doubt, leave it per-service — explicit
duplication of a small, stable class is cheaper than a wrong shared abstraction.
