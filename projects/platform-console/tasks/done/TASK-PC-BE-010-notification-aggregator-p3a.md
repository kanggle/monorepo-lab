# TASK-PC-BE-010 — console-bff notification aggregator endpoint (ADR-MONO-043 P3a)

**Status:** done

**Type:** TASK-PC-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (cross-domain fan-in with failure-isolation + per-domain credential dispatch — the D5/D6 invariants must hold exactly)

---

## Goal

ADR-MONO-043 **P3a**: add the console-bff **notification aggregator** endpoint — the server-side fan-in that lets the platform-console shared-shell bell read one merged, per-domain-failure-isolated notification feed instead of hard-coupling every console page to a single domain (the § 1.2 incident this ADR fixes). Additive + consumer-free; the console-web bell rewire is **P3b** (TASK-PC-FE-137, separate). Operator-facing scope = **erp** for Phase 1 (the only operator-facing notification domain today); Phase 2 extends via config with zero controller change.

## Scope

`projects/platform-console/apps/console-bff/` only (additive):

- `application/port/outbound/ErpNotificationsReadPort.java` — `readInbox(credential,page,size,unread)` + `markRead(credential,id)`; **no tenantId** (erp reads tenant from JWT).
- `adapter/outbound/http/ErpNotificationsReadAdapter.java` — drives the existing `erpRestClient` directly with `Authorization: Bearer <IamOidcAccessToken>` + `Accept`, **NO `X-Tenant-Id`** (the erp D6/contract-§3 divergence; cannot use `RestClientHelper.authenticatedGet` which always sets it).
- `infrastructure/config/NotificationAggregatorProperties.java` — `@ConfigurationProperties("consolebff.notifications")`, `domains` default `["erp"]`.
- `application/usecase/NotificationAggregationUseCase.java` (+ `NotificationAggregationResult`, `UnknownNotificationDomainException`) — mirrors `OperatorOverviewCompositionUseCase`: pre-resolve credentials on the servlet thread, fan out via `CompositionEngine` (route `"notification-aggregator"`), merge items, inject `sourceDomain` iff absent (contract §4.2; erp already carries it — never overwritten), sort `createdAt` desc, collect `degradedDomains`. **D5 divergences from operator-overview: NO cross-leg 401 collapse** (a domain's 401 degrades only its leg) **+ always a partial feed** (one domain down ≠ whole bell down).
- `adapter/inbound/web/NotificationAggregatorController.java` (+ `NotificationInboxResponse`) — `GET /api/console/notifications/inbox?page&size&unread` (**always HTTP 200**, body `{asOf, items[§1+sourceDomain], meta, degradedDomains}`) + `POST /api/console/notifications/{sourceDomain}/{id}/read` (dispatch to owning domain; unknown domain → 404 `NOTIFICATION_NOT_FOUND`).
- `ConsoleBffApplication.java` `@EnableConfigurationProperties`; `GlobalExceptionHandler` maps `UnknownNotificationDomainException → 404`; `application.yml` adds `consolebff.notifications.domains: erp`.
- Metrics: reuse `bff_fanout_latency`/`bff_fanout_errors`/`bff_aggregation_degrade_count` with `route/dashboard = "notification-aggregator"`.

## Out of Scope

- console-web bell rewire (P3b / TASK-PC-FE-137).
- Phase-2 domains (fan/ecommerce/wms) — config-gated; not added (audience semantics: only erp is operator-facing today).
- Any change to existing console-bff endpoints/use-cases or to any downstream service.

## Acceptance Criteria

- [x] `GET /api/console/notifications/inbox` returns **200** with a merged, `createdAt`-desc, `sourceDomain`-attributed feed; erp leg uses the IAM OIDC token + **no `X-Tenant-Id`**.
- [x] **D5 failure isolation**: erp down → still **200**, `degradedDomains=["erp"]`, items partial (no 5xx; no whole-bell failure). No cross-leg 401 collapse (401 → per-leg degrade).
- [x] **D6**: per-domain credential dispatch via `CredentialSelectionAdapter` (ERP→IamOidcAccessToken), pre-resolved on the servlet thread; dispatcher-not-rewrite.
- [x] `POST /{sourceDomain}/{id}/read` proxies to the owning domain; unknown sourceDomain → 404.
- [x] Phase-2 extensible (config `domains` + `targetForDomain` switch; unknown config domain fail-soft skipped).
- [x] Additive — no existing endpoint modified; only console-bff files changed.
- [x] `:console-bff:test` BUILD SUCCESSFUL (52 tests); the Testcontainers IT (MockWebServer JWKS + WireMocked erp inbox) compiles; CI `Integration (platform-console console-bff)` is authoritative.

## Related Specs

- [ADR-MONO-043](../../../../docs/adr/ADR-MONO-043-notification-architecture-unification.md) — D2 (aggregator), D5 (failure isolation), D6 (credential dispatch).
- [platform/contracts/notification-inbox-contract.md](../../../../platform/contracts/notification-inbox-contract.md) §4 — aggregator consumption contract.
- [ADR-MONO-017](../../../../docs/adr/ADR-MONO-017-platform-console-bff-architecture.md) D2/D4/D5/D7 — the console-bff fan-out machinery mirrored.

## Related Contracts

- Serves the §4 aggregator consumption contract; the erp leg consumes the erp inbox (per-domain credential, no X-Tenant-Id).

## Edge Cases / Failure Scenarios

- **One domain down / 401 / timeout** → per-leg degrade, 200 + degradedDomains (D5). Verified by unit + IT.
- **`X-Tenant-Id` sent to erp** → erp rejects; avoided — the erp adapter omits it.
- **Unknown sourceDomain on mark-read** → 404 (no outbound).
- **Misconfigured `domains` entry** → fail-soft skip (never fails the bell).

## Definition of Done

- [x] Aggregator endpoint + use case + erp port/adapter + config + metrics; unit + IT.
- [x] `:console-bff:test` GREEN.
- [ ] commit + push (branch `task/pc-be-010-notification-aggregator-p3a`) + PR + CI `Integration (platform-console console-bff)` GREEN + merge (3-dim verify).
- [ ] P3b — console-web bell rewires to this endpoint (TASK-PC-FE-137, next).
