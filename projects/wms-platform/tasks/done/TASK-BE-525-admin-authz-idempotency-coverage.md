# TASK-BE-525 — admin-service: authz-gate + IdempotencyFilter coverage hardening

- **Type**: TASK-BE (test-coverage hardening — no production behavior change)
- **Status**: done
- **Service**: admin-service (wms-platform)
- **Domain/traits**: wms / [transactional, event-driven, multi-tenant]
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (AOP method-security proxy wiring + filter replay/lock semantics)

## Goal

Close the two CRITICAL zero/under-coverage surfaces the 2026-07-19 wms test audit verified in admin-service:

1. **Service-level `@PreAuthorize` gates that no test ever executes** — 21 real `@PreAuthorize` annotations exist, but only 3 are proven both-ways. `RoleService` (4), `AssignmentService` (2), `SettingsService.upsert` (1), and `UserService.update/deactivate/reactivate` (3) carry `hasAnyRole('WMS_ADMIN','WMS_SUPERADMIN')` that is **never run through the method-security AOP proxy** — `RoleServiceTest`/`AssignmentServiceTest`/`SettingsServiceTest` instantiate the RAW (unproxied) bean, so the annotation is pure decoration from a coverage standpoint. A regression that deleted the annotation would stay GREEN.
2. **`IdempotencyFilter` has ZERO coverage of any kind** — header validation, replay-on-match, 409-on-mismatch, lock contention/timeout, and store-outage 503 are all unasserted.

Plus close the dashboard-controller negative-assertion gaps (annotation present, denial unproven).

**Verified (audit + orchestrator recon, 2026-07-19):**
- `@PreAuthorize` real count = 21 (3 Javadoc mentions excluded). Fully proven both-ways = 3 only (`AlertDashboardController.acknowledge`, `OperationsController` class-level, `UserService.create` via `UserServiceAuthzTest`).
- `RoleServiceTest`/`AssignmentServiceTest`/`SettingsServiceTest` call the raw service (no `ProxyFactory`/`SecurityContextHolder`) — 7 gates never executed.
- `IdempotencyFilter` (`infra/idempotency/IdempotencyFilter.java`, `OncePerRequestFilter`): `Idempotency-Key` header, ≤128 chars, mutating verbs under `/api/v1/admin/` only; store = `com.example.web.idempotency.IdempotencyStore` (`InMemoryIdempotencyStore` default / `RedisIdempotencyStore` non-`standalone`); key = `sha256(key:method:path)`; replay-on-match, 409 `DUPLICATE_REQUEST` on hash mismatch, distributed lock (409 `CONFLICT` on lock-timeout), 503 `SERVICE_UNAVAILABLE` on store outage, caches status < 500. Zero `@Test` touches it (only a comment in `FlywayMigrationIntegrationTest`).
- Missing slice files entirely: `MasterRefControllerWebMvcTest`, `ThroughputDashboardControllerWebMvcTest`.
- 4 dashboard slices (`AdjustmentAudit`, `Asn`, `Order`, `Shipment`) assert VIEWER-200 only — no 403 (wrong-role) / 401 (unauth).

This is a `project_enforcement_straggler_sibling_parity` coverage straggler: `UserServiceAuthzTest` proved the pattern for 1 service; the sibling admin services never got it.

## Scope

- **In scope** (admin-service `src/test` only — NEW test code, no `src/main` change):
  - **AC-1 Service authz proxy tests** — mirror `application/user/UserServiceAuthzTest.java` (the canonical `ProxyFactory` + `AuthorizationManagerBeforeMethodInterceptor.preAuthorize(new PreAuthorizeAuthorizationManager())` + `setProxyTargetClass(true)` + `SecurityContextHolder` pattern) for: `RoleService` (create/update/deactivate/reactivate), `AssignmentService` (grant/revoke), `SettingsService.upsert`, and the 3 uncovered `UserService` methods (update/deactivate/reactivate). Each: ADMIN allowed, SUPERADMIN allowed, OPERATOR → `AccessDeniedException`, VIEWER → `AccessDeniedException`.
  - **AC-2 IdempotencyFilter unit test** — construct the filter directly with an in-memory/stub `IdempotencyStore` + `MockHttpServletRequest`/`MockHttpServletResponse` + a recording `FilterChain` (same isolation style as outbound's `IdempotencyFilterRedisIT`, but no Testcontainer needed for the in-memory store paths). Assert: missing/blank header → 400 `VALIDATION_ERROR`; > 128 chars → 400; GET (non-mutating) and non-`/api/v1/admin/` path → passthrough (chain called, filter no-op); first call executes+caches; identical replay → cached status/body/content-type returned without re-invoking chain; same key + different body-hash → 409 `DUPLICATE_REQUEST`; store outage (stub throws) → 503 `SERVICE_UNAVAILABLE`; ≥500 response NOT cached. Lock-timeout → 409 `CONFLICT` if reachably testable with a stub store; if the lock path isn't unit-reachable, document why and cover it at the store level instead.
  - **AC-3 Dashboard slice negatives** — add to `AdjustmentAuditControllerWebMvcTest`, `AsnDashboardControllerWebMvcTest`, `OrderDashboardControllerWebMvcTest`, `ShipmentDashboardControllerWebMvcTest`: a wrong-role (e.g. `WMS_OPERATOR`) → 403 and an unauthenticated → 401 case each. Create `MasterRefControllerWebMvcTest` and `ThroughputDashboardControllerWebMvcTest` (positive VIEWER-200 + 403 wrong-role + 401 unauth).
- **Out of scope** (do NOT touch — surface, don't fix):
  - The **read-path authz gap** (GET/list/find on Users/Roles/Assignments/Settings carry no `@PreAuthorize`, self-documented as a BE-046 follow-up and now owned by decision ticket **TASK-BE-523**). This is a real authorization *behavior* question, not a coverage gap — do not add gates or change security here.
  - Any `src/main` production change. If a test surfaces a genuine defect (e.g. the filter mis-caches a 5xx), STOP and report it as a finding — file a follow-up, do not silently fix under this coverage task.
  - inbound-service (owned by concurrent sessions), outbound-service (TASK-BE-526).

## Acceptance Criteria

- **AC-1**: New/extended AOP-proxy authz tests execute `@PreAuthorize` on all 10 previously-unexercised gated methods (RoleService×4, AssignmentService×2, SettingsService×1, UserService update/deactivate/reactivate×3); each proves ADMIN+SUPERADMIN allowed and OPERATOR+VIEWER → `AccessDeniedException`. Verified RED-if-annotation-removed for at least one representative method per service (mutation discipline: temporarily strip the annotation, confirm the deny-case test fails, restore).
- **AC-2**: `IdempotencyFilter` unit test covers header-missing-400, header-too-long-400, non-mutating/non-admin passthrough, first-call-caches, replay-on-match, 409-on-mismatch, 503-on-store-outage, and ≥500-not-cached. Each behavioral branch has a distinct `@Test` with a `@DisplayName`.
- **AC-3**: The 4 existing dashboard slices each gain a 403 (wrong-role) + 401 (unauth) assertion; `MasterRefControllerWebMvcTest` + `ThroughputDashboardControllerWebMvcTest` exist with positive + 403 + 401.
- **AC-4**: `:admin-service:test` (+ `:integrationTest` on the wms Testcontainers lane — authoritative) GREEN. No `src/main` diff (verify `git diff --stat -- '*/admin-service/src/main'` is empty).
- **AC-5**: Every new `@Test` genuinely ASSERTS the property (no positive-only that a name implies is a denial test — the audit found `getById_viewer_forbidden` actually asserting 200; do not repeat that anti-pattern).

## Edge Cases / Failure Scenarios

- `@WebMvcTest` slices mock the service, so service-level `@PreAuthorize` will NOT fire in a slice — that is exactly why AC-1 uses the AOP-proxy unit pattern, not a slice. Controller/class-level `@PreAuthorize` (dashboards) DOES fire in `@WebMvcTest` with method security enabled — that is why AC-3 is a slice.
- The `IdempotencyFilter` is a plain `FilterRegistrationBean`, not auto-registered in `@WebMvcTest` — do not attempt to prove it via a controller slice; unit-test it directly (AC-2).
- Body canonicalization: the mismatch-409 test must send the SAME `Idempotency-Key` with a DIFFERENT canonical body so `requestHash` differs — verify `BodyCanonicalizer` semantics first so the two bodies actually hash differently.
- Match the existing test conventions (JUnit5 `@Nested`/`@DisplayName`, AssertJ, `@WithMockUser`/`SecurityMockMvcRequestPostProcessors` in slices) already used in the admin slices.

## Related

- Canonical proxy pattern to mirror: `admin-service/.../application/user/UserServiceAuthzTest.java`.
- Filter under test: `admin-service/.../infra/idempotency/IdempotencyFilter.java` (+ `InMemoryIdempotencyStore`, `BodyCanonicalizer`).
- Slice convention refs: `AlertDashboardControllerWebMvcTest` (has genuine 403 negative), `OperationsControllerWebMvcTest` (403+401 both-ways).
- Related decision ticket (do NOT implement here): `TASK-BE-523` (admin read-path authz decision).
- Memory: `project_enforcement_straggler_sibling_parity`, `feedback_guard_predicate_wrong_verify_the_artifact` (assert the property, not a proxy indicator), `project_testcontainers_docker_desktop_blocker` (CI wms lane authoritative), `platform/testing-strategy.md`.
