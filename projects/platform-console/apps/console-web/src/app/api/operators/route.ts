import { NextResponse } from 'next/server';
import {
  listOperators,
  createOperator,
} from '@/features/operators/api/operators-api';
import type { OperatorStatus } from '@/features/operators/api/types';
import {
  CreateBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from './_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin operators LIST proxy (GET) + CREATE proxy (POST) for client
 * components — the typed API client's single backend entry point (no
 * browser-direct GAP call, architecture.md § Forbidden Dependencies /
 * contract § 2.3). The HttpOnly operator token + active tenant are
 * attached server-side in the api layer.
 *
 * - GET  → `listOperators` (read; NO mutation headers).
 * - POST → `createOperator` (mutation; the api layer attaches BOTH
 *   `X-Operator-Reason` AND `Idempotency-Key` per the producer matrix —
 *   the proxy never re-derives the header set). The password is in the
 *   body and is forwarded server-side only; it is NEVER logged.
 *
 * 401 → 401 (re-login); 503/timeout → 503 (operators section degrades
 * only); 400 NO_ACTIVE_TENANT → 400 (tenant gate); 403/404/409/400
 * producer → inline actionable.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const url = new URL(req.url);
  const statusParam = url.searchParams.get('status');
  const status =
    statusParam === 'ACTIVE' || statusParam === 'SUSPENDED'
      ? (statusParam as OperatorStatus)
      : undefined;
  const page = url.searchParams.has('page')
    ? Number(url.searchParams.get('page'))
    : undefined;
  const size = url.searchParams.has('size')
    ? Number(url.searchParams.get('size'))
    : undefined;

  try {
    const result = await listOperators({ status, page, size });
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}

export async function POST(req: Request) {
  const requestId = newRequestId();
  let body;
  try {
    body = CreateBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    const result = await createOperator(
      {
        email: body.email,
        displayName: body.displayName,
        password: body.password,
        roles: body.roles,
        tenantId: body.tenantId,
      },
      body.reason,
      body.idempotencyKey,
    );
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapError(err, requestId);
  }
}
