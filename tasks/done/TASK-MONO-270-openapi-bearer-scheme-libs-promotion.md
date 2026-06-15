# TASK-MONO-270 — Promote the JWT-Bearer OpenAPI boilerplate to libs/java-web + document intentional per-service duplication

**Status:** done

**Type:** TASK-MONO (monorepo-level — touches `libs/`)
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (mechanical shared-lib extraction + 10-service adaptation; one new dependency, net-behavior-zero)

---

## Goal

The ecommerce duplication-promotion sweep (Cluster 2 of the ecommerce code-refactor request) found that **most** apparent infrastructure duplication is **intentional or policy-blocked** from `libs/`, leaving exactly one policy-clean, domain-agnostic candidate: the **JWT-Bearer OpenAPI security-scheme boilerplate** copy-pasted across 10 ecommerce servlet REST services. Promote it to a shared `libs/java-web` builder so the `bearerAuth` scheme + token-input hint is defined once, and **record the intentional duplications in a knowledge note** so they are not re-investigated.

Why the others are NOT promoted (documented, not done):

- `EventDeduplicationChecker` / `ProcessedEventCleanupScheduler` (order, shipping) — **intentional per-service** by `libs/java-messaging` `EventDedupePort`'s own design note: *"the persistence implementation lives in each service's adapter layer because the dedupe table's retention policy and tenant scoping is service-specific"* (`rules/traits/transactional.md` §T8). Promoting would contradict the rule.
- `TenantContext` (default `"ecommerce"`) / `SellerScopeContext` (marketplace seller axis, ADR-MONO-030) — **project-specific**; moving verbatim to a shared lib violates the shared-library policy (HARDSTOP-03).
- `KafkaConsumerConfig` (×9) — per-service variance (not-retryable exception sets differ); not a clean single shape.
- `Email` VO / `UserWithdrawnEvent` — only one **live** copy each (the other lives in decommissioned auth-service); no live duplication to remove.

## Scope

**In scope:**

1. **New shared builder** `libs/java-web/src/main/java/com/example/web/openapi/BearerJwtOpenApi.java` — a final utility class with a private ctor and a static `create(String title, String description, String version)` returning an `io.swagger.v3.oas.models.OpenAPI` configured with a single global `bearerAuth` HTTP bearer (JWT) security scheme and the token-input hint. Pure builder over `io.swagger.v3.oas.models` POJOs — **no Spring, no servlet API** — consistent with `libs/java-web`'s framework-agnostic charter (swagger-core models are classpath-safe on servlet and reactive stacks).
2. **`libs/java-web/build.gradle`** — add `compileOnly 'io.swagger.core.v3:swagger-models-jakarta:2.2.28'` (compile-time only; no transitive leak to consumers; does NOT introduce spring-web/webmvc/servlet — honors the MONO-044a constraint). Add a matching `testImplementation` for the unit test.
3. **Lib unit test** `BearerJwtOpenApiTest` — assert the scheme key (`bearerAuth`), `type=HTTP`, `scheme=bearer`, `bearerFormat=JWT`, and that title/description/version pass through.
4. **Refactor 10 ecommerce `OpenApiConfig.java`** (order, user, product, payment, shipping, review, promotion, settlement, search, notification) — each keeps its `@Configuration` + `@Bean <svc>OpenAPI()` but the body becomes `return BearerJwtOpenApi.create("<Title>", "<subtitle>", "v1");`. Drop the now-unused `BEARER_SCHEME` constant and the swagger `Components`/`Info`/`SecurityScheme`/`SecurityRequirement` imports (keep the `OpenAPI` import). Bean method names + titles + subtitles unchanged (Swagger output byte-identical).
5. **Knowledge note** `projects/ecommerce-microservices-platform/knowledge/intentional-infrastructure-duplication.md` — record the per-service-by-design / project-specific rationale above so future sweeps don't re-flag it.

**Out of scope:** any non-ecommerce project's OpenApiConfig (additive helper; other projects may adopt later but are not required to here), the reactive gateway-service (WebFlux springdoc, different shape), and all the NOT-promoted items above (left as-is by design).

**Sequencing:** this branch is **stacked on TASK-BE-389** (`task/be-389-decommission-residue-cleanup`) because both touch the 10 `OpenApiConfig.java` files (BE-389 corrected the token-hint string; this task moves that string into the shared builder). Merge **BE-389 first**, then this; the helper's `TOKEN_HINT` carries the corrected issuer text so the end state is consistent.

## Acceptance Criteria

- **AC-1** — `BearerJwtOpenApi` exists in `libs/java-web` (`com.example.web.openapi`), compiles with only swagger-core models + no Spring/servlet imports; `libs/java-web` still declares **no** spring-web/webmvc/orm/servlet-api dependency.
- **AC-2** — All 10 ecommerce `OpenApiConfig` produce their `OpenAPI` bean via `BearerJwtOpenApi.create(...)`; no service still defines its own `bearerAuth` `SecurityScheme`. `grep -rl "SecurityScheme" apps/*/.../OpenApiConfig.java` returns 0.
- **AC-3 (net-behavior-zero)** — The generated OpenAPI doc per service is byte-equivalent to before (same title, subtitle, version, scheme key `bearerAuth`, bearer/JWT, token hint). No endpoint, no runtime auth, no contract changes.
- **AC-4** — `./gradlew :libs:java-web:test` GREEN (incl. `BearerJwtOpenApiTest`); the 10 services' `compileJava` GREEN.
- **AC-5** — Knowledge note committed documenting the intentional/non-eligible duplications with their authorities (transactional.md §T8, HARDSTOP-03, auth-service decommission).
- **AC-6** — Atomic PR: the `libs/` change + all 10 adaptations + knowledge note land together (no transiently-broken main).

## Related Specs

- `rules/traits/transactional.md` §T8 (idempotent-consumer dedupe is per-service — the authority for NOT promoting EventDedup).
- `platform/shared-library-policy.md` (no project-specific content in `libs/` — HARDSTOP-03; the authority for NOT promoting TenantContext/SellerScope).

## Related Contracts

- None. OpenAPI/Swagger UI is documentation-only; no HTTP/event contract changes.

## Edge Cases

- **swagger-models-jakarta version** — `compileOnly` resolves independently of the services' springdoc; the OpenAPI builder API is stable across swagger-core 2.2.x, and `compileOnly` never reaches consumer runtime classpaths, so the exact patch version is immaterial to correctness (2.2.28 matches springdoc 2.7.0's era).
- **Framework-agnostic constraint (MONO-044a)** — adding swagger-core *models* (pure POJOs) is not spring-web/servlet; the reactive gateway (which depends on libs:java-web) is unaffected because the dep is `compileOnly` and the gateway never references `BearerJwtOpenApi`.
- **Package divergence** — most services place `OpenApiConfig` under `infrastructure.config`, but payment, notification, search use `config`. The refactor is per-file (package unchanged); only the body + imports change.

## Failure Scenarios

- **F1 — spring-web leak into libs/java-web** — if the helper accidentally pulled springdoc-starter (which carries spring-webmvc), it would re-trigger the MONO-044a reactive-classpath `BeanDefinitionOverrideException`. Guarded by AC-1 (swagger *models* only, compileOnly) — verify the resolved `libs:java-web` classpath has no spring-web.
- **F2 — Swagger doc drift** — a wrong title/subtitle/version during refactor would change a service's published doc. Guarded by AC-3 (values copied verbatim per service).
- **F3 — main breakage from non-atomic landing** — splitting the lib and the adaptations across PRs would break compilation on main. Guarded by AC-6 (single atomic PR).
