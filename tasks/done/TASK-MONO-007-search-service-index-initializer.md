# TASK-MONO-007 — fix IndexInitializerUnitTest stubs to match current production code

## Goal

Restore `:projects:ecommerce-microservices-platform:apps:search-service:check`
to a green state so it can join root CI (the last of TASK-MONO-008's
three deferred slots). Currently 2 of 118 tests fail in
`IndexInitializerUnitTest`: production-code drift left the unit
test's Mockito stubs out of sync with the current
`IndexInitializer.run()` implementation.

## Background

`IndexInitializer.run()` evolved beyond the simple
"if-not-exists then create" loop the unit test was written against.
The current production behavior is:

1. `indices().exists(ExistsRequest)` — same as before.
2. If `exists && !hasCurrentSpec(indexName)` → `delete()` then create.
3. Otherwise create only when `!exists`.

Two new shapes of drift surfaced when the suite finally ran:

- `createIndex(indexName)` builds a typed `CreateIndexRequest` via
  `CreateIndexRequest.of(b -> b.index(...).withJson(...))` and passes
  *that* to `indices().create(CreateIndexRequest)`. The unit test still
  stubs `indices().create(any(Function.class))`, which Mockito's
  strict-stubbing mode (default for `MockitoExtension`) treats as a
  mismatch — `PotentialStubbingProblem` is raised on the real call.
- `hasCurrentSpec(indexName)` calls
  `indices().getMapping(g -> g.index(indexName))` and dereferences
  the chain `resp.result().get(indexName).mappings().properties()`
  to verify both that `name` carries `analyzer = nori_korean` and
  that `thumbnailUrl` is present. The "exists ⇒ skipsCreation" test
  never stubs `getMapping()`, so the call returns null and `resp.result()`
  raises `NullPointerException` before the test can assert anything.

The drift is purely in test scaffolding; production code is fine.

## Scope

**In scope:**

1. `IndexInitializerUnitTest.run_indexNotExists_createsIndex` — switch
   the `create()` stub and verify from `Function.class` to
   `CreateIndexRequest.class` to match
   `IndexInitializer.createIndex()`'s actual call site.
2. `IndexInitializerUnitTest.run_indexExists_skipsCreation` — rename
   to `run_indexExists_currentSpec_skipsCreation` (the predicate is
   no longer just "exists" — it's "exists AND current spec"), and
   add the Mockito chain that makes `hasCurrentSpec(indexName)`
   return `true`: stub `indices().getMapping(...)` to return a
   `GetMappingResponse` whose mapping has `name` (text + nori_korean
   analyzer) and `thumbnailUrl`.
3. `.github/workflows/ci.yml` — append
   `:projects:ecommerce-microservices-platform:apps:search-service:check`
   to the Build & Test gradle invocation list; remove search-service
   from the deferred-services comment block (the block goes away
   entirely — all 12 ecommerce backend services now run in CI).

**Out of scope:**

- `ElasticsearchIndexAdapterTest` (already integration-tagged) and
  the other ES tests — they pass today.
- Splitting `hasCurrentSpec(...)` out into its own collaborator for
  easier mocking. The unit test's deep stub chain is the cheapest
  fix today; a cleaner integration-test migration is a future task.
- The `delete + recreate` migration branch (`exists && !hasCurrentSpec`).
  The existing unit suite does not cover it; coverage there belongs
  in `ElasticsearchIndexAdapterTest` or a new integration test, not
  this fix.

## Acceptance Criteria

1. `./gradlew :projects:ecommerce-microservices-platform:apps:search-service:check`
   passes locally and on CI.
2. The two failing tests pass with their stubs aligned to the
   current `IndexInitializer.run()` implementation.
3. CI Build & Test gradle list includes search-service `:check`.
4. The deferred-services comment block in `ci.yml` is removed
   (or simplified to "all 12 ecommerce services run").
5. No other ecommerce service test report regresses.

## Related Specs

- `tasks/done/TASK-MONO-008-...` — root CI extension PR; this task
  closes its last deferred slot.

## Related Contracts

None — production code is unchanged.

## Edge Cases

- Mockito strict stubbing: any unused stub will cause
  `UnnecessaryStubbingException` if the test path doesn't reach it.
  The chain stubs in test 2 are read sequentially by `hasCurrentSpec`,
  so they are all consumed.
- `Property` is a tagged union in the ES Java client; the production
  code calls `nameProp.isText()` and `nameProp.text().analyzer()`,
  which need separate stubs in the unit test.
- `verify(indicesClient, never()).create(any(CreateIndexRequest.class))`
  in test 2 protects against accidental delete+recreate (the third
  branch of `run()`); the test would fail loudly if the
  `hasCurrentSpec()` stub leaks `false`.

## Failure Scenarios

- **ES Java client builder API surprises**: if a method signature
  doesn't accept the assumed argument type, fall back to plain
  `mock(...)` for the affected node and stub its accessor directly.
  Document the deviation in the Outcome section.
- **Test 2 stays brittle in the long run**: a deeper structural
  refactor (split `hasCurrentSpec` into a collaborator,
  or migrate to an integration test) is the right answer. Open a
  follow-up task once root CI is green for all 12 services and
  the stub chain proves to need adjustment a second time.

## Outcome (2026-04-26)

- `IndexInitializerUnitTest.run_indexNotExists_createsIndex`: switched
  the `create()` stub and verify call from `any(Function.class)` to
  `any(CreateIndexRequest.class)` to match the current production
  call site at `IndexInitializer.createIndex()`.
- `IndexInitializerUnitTest.run_indexExists_skipsCreation` renamed to
  `run_indexExists_currentSpec_skipsCreation`; added the Mockito
  chain (GetMappingResponse → IndexMappingRecord → TypeMapping →
  properties{name(text/nori_korean), thumbnailUrl}) that lets
  `hasCurrentSpec(indexName)` return true; assertion now also
  guards against accidental `delete()` calls.
- `.github/workflows/ci.yml` Build & Test gradle list extended with
  `:projects:ecommerce-microservices-platform:apps:search-service:check`.
  The deferred-services comment block is removed: all 12 ecommerce
  backend services run in root CI.
- Local verify: `./gradlew :projects:ecommerce-microservices-platform:apps:search-service:check`
  → BUILD SUCCESSFUL, 118 tests, 0 failures.

Acceptance criteria 1, 2, 3, 4 met by the diff. AC #5 verified by
the same local run. CI run on the PR will exercise the cold path.
