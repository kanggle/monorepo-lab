import {
  TrialBalanceSchema,
  type TrialBalance,
} from './ledger-types/trial-balance';
import {
  PeriodsResponseSchema,
  type PeriodsResponse,
  type PeriodsQueryParams,
} from './ledger-types/period';
import {
  DiscrepanciesResponseSchema,
  type DiscrepanciesResponse,
  type DiscrepanciesQueryParams,
} from './ledger-types/reconciliation';
import { FxRatesResponseSchema, type FxRatesResponse } from './ledger-types/fx';
import { callLedger, pageParams } from './ledger-client';

/**
 * Shared finance `ledger-service` **browsable index reads** — trial balance ·
 * accounting-period list · reconciliation discrepancy queue · FX rate cache
 * (TASK-PC-FE-072/073/075 producer bindings / TASK-PC-FE-259 promotion).
 *
 * ── WHY THIS LIVES IN `shared/` ──
 * These four reads are consumed by **two** features: `features/ledger-ops`
 * (the `/ledger` operator screens) and `features/finance-overview` (the
 * `/finance` landing snapshot, which previously reached into
 * `@/features/ledger-ops/api/ledger-api` directly). Per `architecture.md`
 * § Forbidden Dependencies —
 *
 *   > 같은 계층 `features/A → features/B` 상호 참조 금지
 *   > (공유 가치는 `shared/` 로 승격).
 *
 * — the shared reads are promoted here rather than cross-imported. Same
 * promotion pattern as `shared/api/rbac-catalog.ts` (which cites the same
 * rule). Only the reads with two consumers moved: `getPeriod`,
 * `getJournalEntry`, `getAccountBalance`, `getAccountEntries`, `getStatement`,
 * `getPositionLots`, `getFxRateHistory`, `refreshFxRates`, `getDiscrepancy`
 * and the `resolveDiscrepancy` mutation all stay feature-local in
 * `features/ledger-ops` (single consumer each), using the SAME
 * `shared/api/ledger-client.ts` hardened call site.
 *
 * `features/ledger-ops/api/ledger-reads-api.ts` and
 * `ledger-reconciliation-api.ts` re-export the functions below, so the
 * `ledger-api` barrel — the public surface consumed by the 19
 * `app/api/ledger/**` proxy routes, the feature screens and the existing
 * tests — is unchanged.
 *
 * ── BEHAVIOUR (moved verbatim from `features/ledger-ops/api/*`) ──
 * Server-only. The DOMAIN-FACING IAM OIDC token (`getDomainFacingToken()`),
 * NEVER `getOperatorToken()`; the ledger resolves the tenant from the JWT
 * `tenant_id ∈ {finance,*}` claim, so the console sends NO `X-Tenant-Id`.
 * Every function here is a pure GET — NO body, NO `Idempotency-Key`, NO
 * `X-Operator-Reason`. FLAT finance error envelope; no `429` handling (the
 * ledger documents none). `401` → whole-session re-login `ApiError`; `403` /
 * `404` / `400` / `422` → inline `ApiError`; `503` / timeout / network →
 * `LedgerUnavailableError` (only the ledger section degrades). Confidential /
 * F7: the sanitised `logPath` carries no ids. F5: every money amount stays a
 * minor-units string.
 */

// ---------------------------------------------------------------------------
// trial balance — GET /api/finance/ledger/trial-balance
//   ledger-api.md § 4 envelope = { data: TrialBalance, meta }. READ-ONLY.
//   Index-style browsable read (no input — tenant-scoped from the JWT).
// ---------------------------------------------------------------------------

export async function getTrialBalance(): Promise<TrialBalance> {
  return callLedger(
    {
      path: '/api/finance/ledger/trial-balance',
      logPath: '/api/finance/ledger/trial-balance',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return TrialBalanceSchema.parse(env.data);
    },
  );
}

// ---------------------------------------------------------------------------
// accounting periods (list) — GET /api/finance/ledger/periods?page=&size=
//   ledger-api.md § 7 envelope = { data: [ Period (no snapshot) ], meta }.
// ---------------------------------------------------------------------------

export async function listPeriods(
  params: PeriodsQueryParams = {},
): Promise<PeriodsResponse> {
  const qs = new URLSearchParams();
  pageParams(qs, params.page, params.size);
  return callLedger(
    {
      path: `/api/finance/ledger/periods?${qs.toString()}`,
      logPath: '/api/finance/ledger/periods',
    },
    (json) => PeriodsResponseSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// reconciliation discrepancies (queue) —
//   GET /api/finance/ledger/reconciliation/discrepancies?status=&page=&size=
//   reconciliation-api.md § 4 envelope = { data: [ Discrepancy ], meta }.
// ---------------------------------------------------------------------------

export async function listDiscrepancies(
  params: DiscrepanciesQueryParams = {},
): Promise<DiscrepanciesResponse> {
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  pageParams(qs, params.page, params.size);
  return callLedger(
    {
      path: `/api/finance/ledger/reconciliation/discrepancies?${qs.toString()}`,
      logPath: '/api/finance/ledger/reconciliation/discrepancies',
    },
    (json) => DiscrepanciesResponseSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// FX rate cache — GET /api/finance/ledger/fx-rates
//   § 14 envelope = { data: { feedEnabled, rates }, meta }. READ-ONLY.
//   `rate` is a decimal **string** (F5 — NEVER Number/parseFloat/parseInt).
//   An empty cache → 200 with `rates: []` (NOT a 404 — empty-state).
//   `logPath` is a fixed constant (no id/code/currency to sanitise).
// ---------------------------------------------------------------------------

/**
 * `getFxRates()` — reads the FX feed cache from the ledger service.
 * Returns `{ feedEnabled, rates }` where each rate carries a pair of
 * currency codes, the exact decimal `rate` **string** (F5 — NOT a float),
 * freshness timestamps, `ageSeconds` (duration, not money), and `stale`.
 * READ-ONLY. The domain-facing IAM OIDC access token is attached by
 * `callLedger`; NEVER `getOperatorToken()`. No path parameters — global
 * list. An empty cache is a normal `200` (`rates: []`) — NOT a 404.
 */
export async function getFxRates(): Promise<FxRatesResponse> {
  return callLedger(
    {
      path: '/api/finance/ledger/fx-rates',
      // No id / code / currency to sanitise — the path is already generic.
      logPath: '/api/finance/ledger/fx-rates',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return FxRatesResponseSchema.parse(env.data);
    },
  );
}
