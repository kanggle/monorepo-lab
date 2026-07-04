# TASK-BE-475 — add a `status` filter to the accounts search/list endpoint

- **Status**: done
- **Type**: TASK-BE (iam-platform — admin-service + account-service)
- **Depends on**: TASK-BE-357 (tenant-scoped account search/list — this threads a new param through the same chain), TASK-BE-231 (the repository `findByTenantIdWithStatusFilter` hook this un-parks)
- **Consumer follow-up**: TASK-PC-FE-181 (platform-console IAM overview adopts `status=LOCKED` to show a 잠금 현황 count — the demand behind this producer change)
- **Analysis model**: Opus 4.8 · **Impl model recommendation**: Opus (multi-service param threading + contract + resilience-correct validation placement)

## Goal

`GET /api/admin/accounts` (list branch) has **no status filter** — a caller cannot count/scope accounts by lifecycle state. Add an optional `status` filter (`ACTIVE` | `LOCKED` | `DORMANT` | `DELETED`) threaded admin-service → account-service → repository. The repository hook (`findByTenantIdWithStatusFilter`, TASK-BE-231) **already exists but is hard-wired to `null`**; the bulk of this task is parameter-threading + validation placement + the `"*"` platform-scope status-aware query, not new query logic.

The immediate consumer is the platform-console IAM overview snapshot (TASK-PC-FE-181), which will read `GET /api/admin/accounts?status=LOCKED&page=0&size=1`.`totalElements` to surface a lock-status count.

## Scope

**admin-service** (`projects/iam-platform/apps/admin-service`):
- `presentation/AccountAdminController.search()` — add `@RequestParam(required=false) String status`. Validate at this public boundary against the allowed set `{ACTIVE, LOCKED, DORMANT, DELETED}` (case-insensitive → normalize to upper); an unknown value throws `IllegalArgumentException` → the existing `AdminExceptionHandler` maps it to **400 `VALIDATION_ERROR`**. Validating here (not only downstream) prevents the `AccountServiceClient` from masking a downstream 400 as `503 DOWNSTREAM_ERROR`. `status` applies to the **list branch only** (the `email` single-lookup branch ignores it). Forward the normalized value to `accountServiceClient.listAll(...)`.
- `infrastructure/client/AccountServiceClient.listAll(tenantId, page, size, status)` — add the `status` param; attach `?status=` **only when non-null** (absent ⇒ unfiltered, back-compat).

**account-service** (`projects/iam-platform/apps/account-service`):
- `presentation/internal/AccountSearchController.search()` — add `@RequestParam(required=false) String status`; pass to the query service.
- `application/service/AccountSearchQueryService.search(tenantId, email, status, page, size)` — parse `status` (non-null) to `AccountStatus` fail-closed (bad value → `IllegalArgumentException` → 400, defense-in-depth; admin-service already gated). `status` applies to the list branch only (email lookup ignores it). Pass to the port.
- `application/port/AccountQueryPort.findAll(tenantId, status, page, size)` — thread `AccountStatus status` (nullable = no filter). **Single caller** (the query service) + single impl → signature change is safe.
- `infrastructure/persistence/AccountQueryPortImpl.findAll()` — pass `status` into the existing `findByTenantIdWithStatusFilter(tenantId, status, pageable)` (was `null`); for the `"*"` platform branch use a new status-aware all-tenant query.
- `infrastructure/persistence/AccountJpaRepository` — add `findAllAccountsWithStatusFilter(status, pageable)` (`WHERE (:status IS NULL OR a.status = :status)` across all tenants); `findAllAccounts(pageable)` stays for the null-status `"*"` path (or is superseded — impl passes null through the new one).

**Contracts** (spec-first):
- `specs/contracts/http/admin-api.md` § `GET /api/admin/accounts` — add the `status` query param row (enum, optional, list-branch-only) + a `400 VALIDATION_ERROR` note for a bad status value.
- `specs/contracts/http/internal/admin-to-account.md` § `GET /internal/accounts` — add the `status` param row.

## Non-Goals

- No change to the `email` single-lookup branch semantics (status ignored there — a single account is looked up by identity, not filtered).
- No new account status values, no lifecycle/transition change (consumes the existing `AccountStatus` enum).
- No console consumer change (that is TASK-PC-FE-181).
- No breakdown/aggregation endpoint — a caller derives counts from `totalElements` per status (ADR-MONO-017 D3.B discipline, mirrored by the console consumer).

## Acceptance Criteria

- **AC-1** `GET /api/admin/accounts?status=LOCKED` (list branch, `account.read`) returns only LOCKED accounts; `totalElements` reflects the LOCKED count in the resolved tenant scope.
- **AC-2** Omitting `status` returns the unfiltered list (back-compat — identical to pre-BE-475 behavior).
- **AC-3** An invalid `status` (e.g. `status=BOGUS`) → **400 `VALIDATION_ERROR`** at admin-service (NOT a 503 masked downstream error). Case-insensitive accepted values: `ACTIVE`/`LOCKED`/`DORMANT`/`DELETED`.
- **AC-4** `status` is honored for the `"*"` SUPER_ADMIN all-tenant list branch too (status-aware all-tenant query).
- **AC-5** The `email` branch ignores `status` (single-lookup semantics unchanged).
- **AC-6** admin-service forwards `?status=` to account-service **only when present** (no empty/`status=null` param on the unfiltered path).
- **AC-7** admin-api.md + admin-to-account.md updated with the `status` param.
- **AC-8** Tests green: account-service (`AccountSearchQueryServiceTest`, `AccountSearchControllerTest`/`InternalControllerTest`, `AccountJpaRepositoryTest` status-filter cases incl. `"*"`), admin-service (`AccountAdminControllerTest` status forward + bad-value 400, `AccountServiceClientUnitTest` conditional `?status=`).

## Related Specs

- `specs/contracts/http/admin-api.md` § `GET /api/admin/accounts` (public contract).
- `specs/contracts/http/internal/admin-to-account.md` § `GET /internal/accounts` (internal contract).
- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.4.1 / §2.4.3.1 (the consumer that will adopt this — TASK-PC-FE-181).

## Related Contracts

- Public: `GET /api/admin/accounts` (admin-service) — additive optional `status` param, backward-compatible.
- Internal: `GET /internal/accounts` (account-service) — additive optional `status` param.

## Edge Cases

- `status` present but blank (`?status=`) → treat as absent (no filter), not a 400 (a blank query param is "unset").
- Both `email` and `status` given → `email` wins (single-lookup), `status` ignored (documented).
- `"*"` platform scope + `status` → status-aware all-tenant query (AC-4); previously `findAllAccounts` had no status hook.
- `DORMANT`/`DELETED` are real enum values and are filterable (the endpoint already can surface them in the unfiltered list); exposing them in the filter is consistent, not a new capability.

## Failure Scenarios

- A bad status masked as 503: prevented by validating at the admin boundary before the downstream call (AC-3) — the `AccountServiceClient` `onStatus(isError)` would otherwise wrap a downstream 400 into `DownstreamFailureException` (503).
- Signature drift: `AccountQueryPort.findAll` has exactly one caller + one impl (verified) — the signature change is contained; a missed call site would fail compilation.
- Repository `"*"` path silently ignoring status: covered by AC-4 + an `AccountJpaRepositoryTest` all-tenant status case.
