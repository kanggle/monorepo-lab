# TASK-BE-526 — outbound-service: write-controller HTTP-layer slice coverage

- **Type**: TASK-BE (test-coverage hardening — no production behavior change)
- **Status**: done
- **Service**: outbound-service (wms-platform)
- **Domain/traits**: wms / [transactional, event-driven, multi-tenant]
- **Analysis model**: Opus 4.8 · **Impl model**: Sonnet (mechanical @WebMvcTest slice authoring — no domain reasoning)

## Goal

Close the HTTP-layer coverage gap the 2026-07-19 wms audit verified: **5 write controllers / 8 write endpoints have ZERO controller-level test coverage** (no `@WebMvcTest` slice, no HTTP-driving IT). The application services behind them are unit-tested, but request binding (`@Valid` DTO constraints, `@PathVariable`/`@RequestBody`), HTTP status mapping, and the `SecurityConfig` coarse role gate are **untested for every write endpoint**.

**Verified (audit + orchestrator recon, 2026-07-19):**
- Write controllers (`adapter/in/web/controller/`): `OrderController` (2), `PackingController` (3), `PickingController` (1), `ShippingController` (1), `ShipmentController` (1) = **8 write endpoints**, all uncovered at HTTP layer. Only the read-only `OrderQueryController` has slices.
- **No `@PreAuthorize` anywhere** in outbound `src/main`. Authorization = coarse `SecurityConfig` HTTP-method gate (`config/SecurityConfig.java` L91–96): `GET /api/** → OUTBOUND_READ|WRITE|ADMIN`; `POST|PATCH|PUT|DELETE /api/** → OUTBOUND_WRITE|ADMIN`. Fine-grained ADMIN-only checks (`retryTmsNotify`, cancel post-pick branch) live INSIDE the services via `AuthorizationGuards` — NOT at the HTTP layer, so they are NOT slice-testable (already service-unit-tested).
- `OutboundServiceIntegrationBase` boots `webEnvironment = NONE` → no existing IT can drive HTTP. No `TestRestTemplate`/`WebTestClient` anywhere → **`@WebMvcTest` slice is the correct layer**.

The 8 endpoints and exact `SecurityConfig` role each requires (bare authority, Spring prepends `ROLE_`):

| Verb + Path | Controller.method | HTTP gate |
|---|---|---|
| `POST /api/v1/outbound/orders` | `OrderController.createOrder` | WRITE\|ADMIN |
| `POST /api/v1/outbound/orders/{id}:cancel` | `OrderController.cancelOrder` | WRITE\|ADMIN |
| `POST /api/v1/outbound/orders/{orderId}/packing-units` | `PackingController.createUnit` | WRITE\|ADMIN |
| `PATCH /api/v1/outbound/packing-units/{id}` | `PackingController.sealUnit` | WRITE\|ADMIN |
| `POST /api/v1/outbound/orders/{orderId}/packing/confirm` | `PackingController.confirmPacking` | WRITE\|ADMIN |
| `POST /api/v1/outbound/picking-requests/{id}/confirmations` | `PickingController.confirmPicking` | WRITE\|ADMIN |
| `POST /api/v1/outbound/orders/{orderId}/shipments` | `ShippingController.confirmShipping` | WRITE\|ADMIN |
| `POST /api/v1/outbound/shipments/{id}:retry-tms-notify` | `ShipmentController.retryTmsNotify` | WRITE\|ADMIN (service further narrows to ADMIN — not slice-testable) |

## Scope

- **In scope** (outbound-service `src/test` only — NEW `@WebMvcTest` slice code, no `src/main` change):
  - One slice per write controller (`OrderControllerTest`, `PackingControllerTest`, `PickingControllerTest`, `ShippingControllerTest`, `ShipmentControllerTest`), `@WebMvcTest(controllers = X.class)` importing the real `SecurityConfig` so the coarse role gate is exercised, service `@MockBean`-ed. For each endpoint:
    - **happy path**: valid request + `OUTBOUND_WRITE` (or `OUTBOUND_ADMIN`) → expected 2xx + response body/status mapping asserted.
    - **validation**: an `@Valid`-violating body (missing required field / bad shape) → 400 with the platform error envelope.
    - **authz gate**: `OUTBOUND_READ`-only caller → 403; unauthenticated → 401.
- **Out of scope**:
  - The service-level fine-grained ADMIN-only narrowing (`retryTmsNotify`, cancel post-pick) — enforced in the service, mocked in a slice; already covered by `RetryTmsNotificationServiceTest`/`CancelOrderServiceTest`. Do not attempt to prove it in a slice.
  - `IdempotencyKeyFilter` header enforcement — a `FilterRegistrationBean` not registered in `@WebMvcTest`; already unit-covered by `IdempotencyFilterRedisIT`.
  - `ErpOrderWebhookController` (different package, already has `ErpOrderWebhookControllerTest`).
  - Any `src/main` change; admin-service (TASK-BE-525); inbound-service (concurrent sessions).

## Acceptance Criteria

- **AC-1**: A `@WebMvcTest` slice exists for each of the 5 write controllers; all 8 write endpoints have a happy-path 2xx assertion driven via `MockMvc` with a `OUTBOUND_WRITE`/`OUTBOUND_ADMIN` caller, asserting the response status + key body fields (and correct service-method invocation via the mock).
- **AC-2**: Each endpoint has a request-validation negative (`@Valid` failure → 400 + platform error envelope) — pick a genuinely-constrained field per DTO (verify the DTO's constraints first; don't assume).
- **AC-3**: Each endpoint has an authz negative: `OUTBOUND_READ`-only → 403 AND unauthenticated → 401, proving the `SecurityConfig` method-verb gate. RED→GREEN discipline: confirm at least one 403 test actually fails if the gate is loosened (mutation check on the imported SecurityConfig, then restore).
- **AC-4**: `:outbound-service:test` GREEN (slices run in the default `test` task, not `integrationTest`). No `src/main` diff (`git diff --stat -- '*/outbound-service/src/main'` empty).
- **AC-5**: Slices import the REAL `SecurityConfig` (not a permissive test config) so AC-3 exercises the actual gate; caller roles set via `SecurityMockMvcRequestPostProcessors` / `@WithMockUser(authorities=...)` with bare authorities `OUTBOUND_READ`/`OUTBOUND_WRITE`/`OUTBOUND_ADMIN`.

## Edge Cases / Failure Scenarios

- Path uses matrix-style `:cancel` / `:retry-tms-notify` suffixes — verify the exact `@PostMapping` path string in the controller so MockMvc URLs match.
- The `SecurityConfig` gate keys on HTTP verb — a `PATCH` endpoint (`sealUnit`) must be tested with `patch(...)`, not `post(...)`, or the gate assertion is meaningless.
- If a slice needs the JWT converter / `SecurityConfig` collaborators to boot, import exactly what the existing `OrderQuerySagaControllerTest` imports (mirror its `@WebMvcTest` setup — it already boots the read controller against the same security infra).
- Assert the platform error envelope shape used elsewhere in outbound (`GlobalExceptionHandler`), not a bare 400 status, so the validation tests prove the contract.

## Related

- Slice convention to mirror: `outbound-service/.../adapter/in/web/controller/OrderQuerySagaControllerTest.java` + `OrderQueryPickingRequestsControllerTest.java` (existing `@WebMvcTest` read slices — same security infra).
- Gate under test: `outbound-service/.../config/SecurityConfig.java` (L91–96 verb→role matchers).
- Service unit tests (already cover business logic — do NOT duplicate): `ReceiveOrderServiceTest`, `CancelOrderServiceTest`, `PackingServiceTest`, `ConfirmPickingServiceTest`, `ConfirmShippingServiceTest`, `RetryTmsNotificationServiceTest`.
- Memory: `project_enforcement_straggler_sibling_parity`, `project_testcontainers_docker_desktop_blocker` (CI wms lane authoritative), `platform/testing-strategy.md` (controller-slice layer).
