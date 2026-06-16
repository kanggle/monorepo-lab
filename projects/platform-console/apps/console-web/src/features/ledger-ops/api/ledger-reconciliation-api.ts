import {
  DiscrepancySchema,
  type Discrepancy,
  DiscrepanciesResponseSchema,
  type DiscrepanciesResponse,
  type DiscrepanciesQueryParams,
  type ResolveDiscrepancyBody,
} from './types';
import { callLedger, pageParams } from './ledger-client';

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
// reconciliation discrepancy (detail) —
//   GET /api/finance/ledger/reconciliation/discrepancies/{id}
//   reconciliation-api.md § 5 envelope = { data: Discrepancy (+ resolution
//   when RESOLVED), meta }. 404 RECONCILIATION_DISCREPANCY_NOT_FOUND.
// ---------------------------------------------------------------------------

export async function getDiscrepancy(id: string): Promise<Discrepancy> {
  return callLedger(
    {
      path: `/api/finance/ledger/reconciliation/discrepancies/${encodeURIComponent(id)}`,
      // confidential / F7 — the log path carries NO discrepancyId.
      logPath: '/api/finance/ledger/reconciliation/discrepancies/{id}',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return DiscrepancySchema.parse(env.data);
    },
  );
}

// ---------------------------------------------------------------------------
// reconciliation discrepancy RESOLVE (the ledger's FIRST and ONLY mutation) —
//   POST /api/finance/ledger/reconciliation/discrepancies/{id}/resolve
//   reconciliation-api.md § 2 — request `{ resolutionType, note }`, 200 →
//   the discrepancy with `status: "RESOLVED"` + a `resolution` sub-object.
//
//   Header matrix (honest, producer-faithful — § 2.4.7.1):
//     - the SAME domain-facing IAM OIDC token (NEVER getOperatorToken());
//     - body `{ resolutionType, note }` (Content-Type added by callLedger);
//     - **NO `Idempotency-Key`** (the producer defines none for resolve; the
//       `409 RECONCILIATION_ALREADY_RESOLVED` state guard is the
//       double-submit defence — NOT a fabricated header);
//     - **NO `X-Operator-Reason`** (the reason rides in the body `note`);
//     - **NO `X-Tenant-Id`** (tenant from the JWT claim).
//
//   Errors map via the SAME taxonomy as the reads: 409
//   RECONCILIATION_ALREADY_RESOLVED / 422 RECONCILIATION_PERIOD_LOCKED / 404
//   RECONCILIATION_DISCREPANCY_NOT_FOUND / 400 → ApiError (inline
//   actionable); 503 / timeout → LedgerUnavailableError (the ledger section
//   degrades, the resolve affordance re-enables on retry).
// ---------------------------------------------------------------------------

export async function resolveDiscrepancy(
  id: string,
  input: ResolveDiscrepancyBody,
): Promise<Discrepancy> {
  return callLedger(
    {
      path: `/api/finance/ledger/reconciliation/discrepancies/${encodeURIComponent(id)}/resolve`,
      // confidential / F7 — the log path carries NO discrepancyId.
      logPath: '/api/finance/ledger/reconciliation/discrepancies/{id}/resolve',
      method: 'POST',
      body: { resolutionType: input.resolutionType, note: input.note },
    },
    (json) => {
      // The producer 200 body is the success envelope `{ data, meta }` — the
      // discrepancy with `status: "RESOLVED"` + `resolution`.
      const env = (json ?? {}) as { data?: unknown };
      return DiscrepancySchema.parse(env.data);
    },
  );
}
