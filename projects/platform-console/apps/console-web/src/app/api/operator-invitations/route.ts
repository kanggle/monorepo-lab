import { NextResponse } from 'next/server';
import {
  listOperatorInvitations,
  createOperatorInvitation,
} from '@/features/operators/api/operators-api';
import type { InvitationStatus } from '@/features/operators/api/invitation-types';
import {
  InviteBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../operators/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin operator-invitations LIST (GET) + INVITE (POST) proxy
 * (TASK-MONO-772 S5 — console-integration-contract § 2.4.3 rows 11–12). The
 * HttpOnly operator token + active tenant are attached server-side in the api
 * layer; the browser never calls IAM directly.
 *
 * - GET  → `listOperatorInvitations` (read; NO mutation headers). `tenantId`
 *   (optional — default the active tenant), `status` (PENDING default),
 *   `page`/`size`.
 * - POST → `createOperatorInvitation` (the api layer attaches BOTH
 *   `X-Operator-Reason` AND `Idempotency-Key`). `201` even when the mail
 *   failed — the body's `delivery.status` says so (OD-4).
 *
 * Error mapping is the shared operators `mapError` (401 → re-login; 503 /
 * timeout → only this section degrades; 4xx → inline actionable).
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const url = new URL(req.url);
  const statusParam = url.searchParams.get('status');
  const status: InvitationStatus | undefined =
    statusParam === 'PENDING' ||
    statusParam === 'ACCEPTED' ||
    statusParam === 'CANCELLED'
      ? statusParam
      : undefined;
  const page = url.searchParams.has('page')
    ? Number(url.searchParams.get('page'))
    : undefined;
  const size = url.searchParams.has('size')
    ? Number(url.searchParams.get('size'))
    : undefined;
  const tenantId = url.searchParams.get('tenantId') ?? undefined;

  try {
    const result = await listOperatorInvitations({ tenantId, status, page, size });
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}

export async function POST(req: Request) {
  const requestId = newRequestId();
  let body;
  try {
    body = InviteBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    const result = await createOperatorInvitation(
      {
        email: body.email,
        displayName: body.displayName,
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
