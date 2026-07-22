# TASK-BE-555 — gateway-service: remove dead `/api/auth/**` route references (BE-552 AC-2 follow-up)

Status: ready

`(분석=Opus 4.8 / 구현 권장=Sonnet — dead-code + stale-test drift 정리, gateway IT 재검증)`

---

## Goal

Remove the dead `/api/auth/**` references left in `gateway-service` after the
auth-service route was decommissioned (`TASK-BE-132`, auth → IAM/GAP OIDC per
`TASK-MONO-027`). `TASK-BE-552` corrected the **spec** (`public-routes.md`) and
explicitly **deferred** the code/test cleanup as harmless-but-confusing (AC-2
option b). This ticket does that deferred cleanup — and it is **not merely
cosmetic**: the gateway **integration test** still exercises `/api/auth/**` as a
public passthrough route (via a test-only route re-added in
`application-integration-test.yml`), so the suite asserts a behaviour that no
longer exists in production. That is real test drift.

## Re-measured evidence (line numbers = hypotheses, re-verify at start)

Confirmed on `origin/main` (tip `68f44e3b0`):

- **Production route already gone** — `application.yml` has no `/api/auth/**`
  route (removal comment `# auth-service route removed by TASK-BE-132`). A live
  `/api/auth/**` request → no route → 404. (This is why it is safe.)
- **Dead production refs:**
  - `apps/gateway-service/.../config/SecurityConfig.java` (≈ line 34):
    `"/api/auth/**"` in `PUBLIC_PATHS` — permits a path with no route.
  - `apps/gateway-service/.../security/RouteService.java` (≈ line 9):
    `if (path.startsWith("/api/auth")) return "auth-service";` — dead
    metrics/log-label branch; no live route feeds it.
  - `apps/gateway-service/src/test/resources/application-integration-test.yml`
    (≈ lines 17–20): `id: auth-service-test`, `Path=/api/auth/**` → dead port
    `19999`. **This test-only route re-adds a route production does not serve.**
- **Load-bearing tests (must be updated, not just the code):**
  - `RouteServiceTest.java` (≈ 17–18): CsvSource rows `/api/auth/login →
    auth-service`, `/api/auth/refresh → auth-service`.
  - `GatewayIntegrationTest.java` (≈ 178–206): three tests
    (`publicRoute_login/signup/refresh_passesWithoutToken`) asserting
    `/api/auth/**` bypasses the JWT filter. **Redundant + stale** — the
    "public path bypasses JWT filter" behaviour is already covered by
    `publicRoute_products_passesWithoutToken` (≈ 208) using a **real**
    production public route (`GET /api/products/**`).
  - `GatewayMetricsTest.java` uses `"auth-service"` only as an arbitrary
    metric-label string (independent of routing) — a stale name, not a
    functional dependency.

## Scope

**In:** remove the dead `/api/auth/**` references and update the tests that
encode the removed behaviour:
- `SecurityConfig` PUBLIC_PATHS `/api/auth/**` row.
- `RouteService` `/api/auth` branch.
- `application-integration-test.yml` `auth-service-test` route.
- `RouteServiceTest` two `/api/auth/*` CsvSource rows.
- `GatewayIntegrationTest` three `/api/auth/*` public-route tests (coverage
  preserved by the existing `/api/products/**` public test).
- `GatewayMetricsTest` stale `"auth-service"` label → a live service name
  (mechanism-only test; keeps the sweep grep-clean).

**Out:** the production route config (already correct); the retained
`apps/auth-service/` source (kept for history per `PROJECT.md`); IAM OIDC
integration; any new public-route behaviour.

## Acceptance Criteria

- **AC-0 (re-measure):** confirm on `main` that (a) the three dead refs are
  present, (b) `RouteServiceTest`/`GatewayIntegrationTest` assert the
  `/api/auth/*` behaviour, and (c) `publicRoute_products_passesWithoutToken`
  already covers "public path bypasses JWT" with a real route (so the three
  auth IT tests can be dropped without a coverage hole). Re-verify file:line —
  code wins.
- **AC-1:** remove all dead `/api/auth/**` references (SecurityConfig,
  RouteService, integration-test yml) and the two `RouteServiceTest` rows.
- **AC-2:** drop the three `/api/auth/*` `GatewayIntegrationTest` public-route
  tests; the `/api/products/**` public test remains the "public path bypasses
  JWT filter" coverage. Re-point `GatewayMetricsTest`'s `"auth-service"` label
  to a live service so no dangling `auth-service`/`/api/auth` string survives.
- **AC-3:** no **functional** `/api/auth`/`auth-service` reference survives in
  `apps/gateway-service/` — no route, no `PUBLIC_PATHS` permit, no
  `RouteService` branch, no test route/assertion. Intentional artifacts are
  **retained and are not violations**: the `application.yml` removal-tombstone
  comments (from `TASK-BE-132`), the new `GatewayIntegrationTest` explanatory
  comment, and `application.yml:13`'s reference to the **GAP/IAM IdP's**
  auth-service (a different, live service — the OIDC issuer, not the retired
  ecommerce auth-service). The retained `apps/auth-service/` module is out of
  scope and unaffected.
- **AC-4:** `./gradlew :projects:ecommerce-microservices-platform:apps:gateway-service:check`
  green — CI Linux authority (`GatewayIntegrationTest` is `@Testcontainers`;
  local Windows Docker is host-dependent). Docker-free unit tests
  (`RouteServiceTest`, `GatewayMetricsTest`) verified locally.

## Related Specs

- `projects/ecommerce-microservices-platform/specs/services/gateway-service/public-routes.md` (already corrected by `TASK-BE-552`).
- `projects/ecommerce-microservices-platform/PROJECT.md` § auth-service RETIRED.

## Related Contracts

- None (no API/event contract change — internal routing/test cleanup only).

## Edge Cases

- `GatewayMetricsTest` `"auth-service"` is a pure label arg — changing it must
  not change what the test asserts (counter increment mechanics), only the
  string.
- Removing the `auth-service-test` route from the integration yml must not
  affect the other test routes (they share the dead `19999` port by design).
- The retained `apps/auth-service/` module still has tests hitting `/api/auth/*`
  — those run against the standalone auth-service, not the gateway; do NOT
  touch them (AC-3 grep is scoped to `apps/gateway-service/`).

## Failure Scenarios

- **Remove the code but leave the tests:** `RouteServiceTest`/
  `GatewayIntegrationTest` go RED (they assert the removed behaviour) — the
  test updates are mandatory, not optional.
- **Delete the three auth IT tests without checking `/api/products/**`
  coverage:** would drop the "public path bypasses JWT filter" property. AC-0
  pins that the products test already covers it.
- **Widen scope to a real routing change:** re-adding or re-pointing
  `/api/auth/**` would reintroduce a dangling route — the production config is
  already correct; this is a removal-only cleanup.
