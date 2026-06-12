import { NextResponse } from 'next/server';
import { resolveDiscrepancy } from '@/features/ledger-ops/api/ledger-api';
import { ResolveDiscrepancyBodySchema } from '@/features/ledger-ops/api/types';
import { mapLedgerError, newRequestId } from '../../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin finance ledger reconciliation discrepancy RESOLVE proxy
 * (TASK-PC-FE-073 — the ledger surface's FIRST and ONLY operator mutation;
 * `reconciliation-api.md` § 2). POST only — no GET / PUT / PATCH / DELETE.
 *
 * Validates the `ResolveDiscrepancyBodySchema` up-front: an empty /
 * whitespace-only `note` (or an out-of-vocabulary `resolutionType`) →
 * `400 VALIDATION_ERROR` with **NO** upstream call (the reason is the audit
 * record — required). On a valid body it calls `resolveDiscrepancy(id, …)`,
 * which attaches the HttpOnly domain-facing IAM OIDC access token server-side
 * (NOT the operator token — the #569 invariant is GAP-domain-scoped) +
 * forwards the body `{ resolutionType, note }`.
 *
 * Header matrix (honest, producer-faithful): body-reason, **NO
 * `Idempotency-Key`** (the producer defines none for resolve — the
 * `409 RECONCILIATION_ALREADY_RESOLVED` state guard is the double-submit
 * defence), **NO `X-Operator-Reason`**, **NO `X-Tenant-Id`**.
 *
 * Errors map via `mapLedgerError`: `409 RECONCILIATION_ALREADY_RESOLVED` /
 * `422 RECONCILIATION_PERIOD_LOCKED` / `404 RECONCILIATION_DISCREPANCY_NOT_FOUND`
 * pass through inline-actionably; `401` → re-login; `403` → not-scoped;
 * `503` / timeout → the ledger section degrades. NO 429 branch.
 */
export async function POST(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;

  let body: ReturnType<typeof ResolveDiscrepancyBodySchema.parse>;
  try {
    body = ResolveDiscrepancyBodySchema.parse(await req.json());
  } catch {
    return NextResponse.json(
      {
        code: 'VALIDATION_ERROR',
        message:
          'invalid resolve-discrepancy body (resolutionType + non-empty note required)',
      },
      { status: 400 },
    );
  }

  try {
    const result = await resolveDiscrepancy(id, body);
    return NextResponse.json(result);
  } catch (err) {
    return mapLedgerError(err, requestId);
  }
}
