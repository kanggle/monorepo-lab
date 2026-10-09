import { NextResponse } from 'next/server';
import {
  getEntryPolicy,
  setEntryPolicy,
} from '@/features/tenant-entry-policy/api/entry-policy-api';
import { SetEntryPolicyBodySchema } from '@/features/tenant-entry-policy/api/types';
import { mapError, badRequest, newRequestId } from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin tenant ENTRY POLICY proxy (TASK-MONO-771 S5 —
 * console-integration-contract § 2.4.3.3; producer admin-api.md § Tenant Entry
 * Policy). GET reads the policy; PUT takes `{ requireMfa, reason }` — the api
 * layer sends `{ requireMfa }` as the producer body and the reason as
 * `X-Operator-Reason` (never an `Idempotency-Key`).
 *
 * Uses the tenants `_proxy` mapping (the api raises `TenantsUnavailableError`
 * on 503/timeout): 401 → 401 (forced re-login), 403 `PERMISSION_DENIED` /
 * `TENANT_SCOPE_DENIED`, 400 `REASON_REQUIRED` / `VALIDATION_ERROR`, 404
 * `TENANT_NOT_FOUND`, 409 `OPTIMISTIC_LOCK_CONFLICT` pass through verbatim;
 * 503 / timeout → 503 (only this control degrades).
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ tenantId: string }> },
) {
  const requestId = newRequestId();
  const { tenantId } = await params;
  try {
    return NextResponse.json(await getEntryPolicy(tenantId));
  } catch (err) {
    return mapError(err, requestId);
  }
}

export async function PUT(
  req: Request,
  { params }: { params: Promise<{ tenantId: string }> },
) {
  const requestId = newRequestId();
  const { tenantId } = await params;
  let body;
  try {
    body = SetEntryPolicyBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    return NextResponse.json(await setEntryPolicy(tenantId, body.requireMfa, body.reason));
  } catch (err) {
    return mapError(err, requestId);
  }
}
