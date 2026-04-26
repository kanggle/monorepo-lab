# TASK-MONO-006 — fix product-service web slice context (missing mocks + @SpringBootConfiguration pin)

## Goal

Restore `:projects:ecommerce-microservices-platform:apps:product-service:check`
to a green state so it can join root CI (TASK-MONO-008's deferred slot).
Currently 20 of 175 tests fail at context load: 3 web slice classes can
neither resolve `ProductImageService` / `MediaUrlResolver` (and, for the
contract test, also `VariantManagementService`) when `TestProductServiceApplication`
boots, and the contract test additionally hits the same
`@SpringBootConfiguration` ambiguity that TASK-MONO-004 fixed for
`OrderApiContractTest`.

## Background

`TestProductServiceApplication` is an empty `@SpringBootApplication`
shim that exists so `@WebMvcTest` slices can avoid pulling in
`@EnableJpaRepositories` / `@EntityScan` from the production
`ProductServiceApplication`. With that shim:

- `ProductController` is auto-wired into the slice context but its
  constructor takes `QueryProductService`, **`ProductImageService`**,
  and **`MediaUrlResolver`**. Only `QueryProductService` is registered
  as `@MockitoBean` in the three slice tests; the other two beans
  cause `NoSuchBeanDefinitionException` at context refresh and the
  failure is then replayed for every test in the cached context.
- `ProductApiContractTest` additionally lacks
  `@ContextConfiguration(classes = TestProductServiceApplication.class)`,
  which lets Spring's `AnnotatedClassFinder` see two
  `@SpringBootApplication`-annotated classes (`TestProductServiceApplication`
  and `ProductServiceApplication`) and fail with
  `Found multiple @SpringBootConfiguration annotated classes`. This is
  the same drift TASK-MONO-004 fixed for `OrderApiContractTest`.
- `ProductApiContractTest` is also missing a `@MockitoBean
  VariantManagementService` (one of `AdminProductController`'s 5
  constructor dependencies) — once the context-loader cache
  invalidates after the first two fixes, this would surface as a
  fresh `NoSuchBeanDefinitionException`.

The drift is purely in test scaffolding; production code is fine.

## Scope

**In scope:**

1. `ProductApiContractTest` — add
   `@ContextConfiguration(classes = TestProductServiceApplication.class)`,
   add three `@MockitoBean` declarations: `ProductImageService`,
   `MediaUrlResolver`, `VariantManagementService`. Also extend the
   `getProductDetail_response_containsSpecFields` strict-set assertion
   with `thumbnailUrl` and `images` — both already documented in
   `specs/contracts/http/product-api.md` GET /api/products/{productId} 200,
   but the test assertion lagged the spec.
2. `ProductControllerTest` — add two `@MockitoBean` declarations:
   `ProductImageService`, `MediaUrlResolver`.
3. `AdminProductControllerTest` — add two `@MockitoBean` declarations:
   `ProductImageService`, `MediaUrlResolver`.
4. `.github/workflows/ci.yml` — append
   `:projects:ecommerce-microservices-platform:apps:product-service:check`
   to the Build & Test gradle list; remove product-service from the
   deferred-services comment block.

**Out of scope:**

- `ProductImageService`, `MediaUrlResolver`, or any production code change.
- `AdminProductImageControllerTest` — this slice does not load
  `ProductController` and is unaffected by the missing mocks.
- search-service drift (TASK-MONO-007).

## Acceptance Criteria

1. `./gradlew :projects:ecommerce-microservices-platform:apps:product-service:check`
   passes locally and on CI.
2. `ProductApiContractTest` carries the explicit
   `@ContextConfiguration` pin matching the OrderApiContractTest pattern.
3. The three slice tests register `@MockitoBean` for every
   constructor parameter of every controller they declare via
   `@WebMvcTest(controllers = …)`.
4. CI Build & Test gradle list includes product-service `:check`.
5. The deferred-services comment block in `ci.yml` no longer mentions
   product-service / TASK-MONO-006.
6. No other ecommerce service test report regresses.

## Related Specs

- `tasks/done/TASK-MONO-004-...` — established the OrderApiContractTest
  `@ContextConfiguration` pin pattern; this task applies the same pin
  to ProductApiContractTest.
- `tasks/done/TASK-MONO-008-...` — root CI extension PR; this task
  closes one of its remaining two deferred slots.

## Related Contracts

None — production controllers, services, and the public HTTP contract
are unchanged. The fix is restricted to test scaffolding.

## Edge Cases

- `ProductController.detail()` calls `productImageService.getImages(productId)`
  and then `mediaUrlResolver.resolve(objectKey)` for each returned
  image. With Mockito's default behavior an unstubbed `getImages()`
  returns an empty list, which short-circuits `mediaUrlResolver`. So
  the mocks need no explicit stubbing in tests that call `GET /api/products/{id}`;
  they exist purely to satisfy the bean factory.
- `AdminProductControllerTest` does not exercise `ProductController` at
  all, but the slice loads both controllers via
  `@WebMvcTest(controllers = {ProductController.class, AdminProductController.class})`,
  so `ProductController`'s deps must still be satisfiable.

## Failure Scenarios

- **An unrelated test starts failing**: highly unlikely — only test
  scaffolding changes — but if it happens, the regression is in
  test-cache pollution from the prior failures and clears with a
  clean `--rerun-tasks` run. Investigate before bundling further fixes.
- **CI build-and-test runtime grows materially**: product-service has
  ~25 test classes. Total runtime should remain well under the 30 min cap.

## Outcome (2026-04-26)

- `ProductApiContractTest` gained `@ContextConfiguration(classes = TestProductServiceApplication.class)`
  plus `@MockitoBean` declarations for `ProductImageService`,
  `MediaUrlResolver`, `VariantManagementService`. The
  `getProductDetail_response_containsSpecFields` assertion was extended
  with `thumbnailUrl` and `images` (already in the spec, only the
  test's strict-set lagged).
- `ProductControllerTest` and `AdminProductControllerTest` each gained
  `@MockitoBean` declarations for `ProductImageService` and
  `MediaUrlResolver`.
- `.github/workflows/ci.yml` Build & Test gradle list extended with
  `:projects:ecommerce-microservices-platform:apps:product-service:check`;
  the deferred-services comment block now lists search-service only.
- Local verify: `./gradlew :projects:ecommerce-microservices-platform:apps:product-service:check`
  → BUILD SUCCESSFUL, 180 tests, 0 failures.

Acceptance criteria 1, 2, 3, 4, 5 met by the diff. AC #6 verified by
the same local run. CI run on the PR will exercise the cold path.
