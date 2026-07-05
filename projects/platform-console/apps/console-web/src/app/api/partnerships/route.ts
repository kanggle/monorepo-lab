import { NextResponse } from 'next/server';
import {
  listPartnerships,
  invitePartnership,
} from '@/features/partnerships/api/partnerships-api';
import type {
  PartnershipMyRole,
  PartnershipStatus,
} from '@/features/partnerships/api/types';
import {
  InviteBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from './_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin partnership LIST proxy (GET) + INVITE proxy (POST) for the client
 * screen (TASK-PC-FE-187). The operator token + active tenant (`X-Tenant-Id`)
 * are attached server-side in the api layer; the host tenant is resolved
 * server-side (never client-supplied) so a client can only ever invite AS its
 * OWN active tenant.
 *
 * - GET  → `listPartnerships` (read; NO mutation headers). role/status/page/size
 *   query passthrough. host-side + partner-side rows (D2-confined to the active
 *   tenant).
 * - POST → `invitePartnership` (mutation; the api layer attaches BOTH
 *   `X-Operator-Reason` AND `Idempotency-Key` per the producer matrix).
 *
 * 401 → 401 (re-login); 403 → inline (lacks partnership.manage / scope);
 * 409 PARTNERSHIP_ALREADY_EXISTS / 422 PARTNERSHIP_SCOPE_INVALID → inline;
 * 503/timeout → 503 (partnership surface degrades only).
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const url = new URL(req.url);

  const roleParam = url.searchParams.get('role');
  const role =
    roleParam === 'host' || roleParam === 'partner'
      ? (roleParam as PartnershipMyRole)
      : undefined;
  const statusParam = url.searchParams.get('status');
  const status =
    statusParam === 'PENDING' ||
    statusParam === 'ACTIVE' ||
    statusParam === 'SUSPENDED' ||
    statusParam === 'TERMINATED'
      ? (statusParam as PartnershipStatus)
      : undefined;
  const page = url.searchParams.has('page')
    ? Number(url.searchParams.get('page'))
    : undefined;
  const size = url.searchParams.has('size')
    ? Number(url.searchParams.get('size'))
    : undefined;

  try {
    const result = await listPartnerships({ role, status, page, size });
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
    const result = await invitePartnership(
      body.partnerTenantId,
      body.delegatedScope,
      body.reason,
    );
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapError(err, requestId);
  }
}
