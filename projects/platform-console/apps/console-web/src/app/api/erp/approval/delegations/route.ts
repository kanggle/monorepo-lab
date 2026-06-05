import { NextResponse } from 'next/server';
import {
  listDelegations,
  createDelegation,
} from '@/features/erp-ops/api/delegation-api';
import { CreateDelegationBodySchema } from '@/features/erp-ops/api/delegation-types';
import { mapErpError, newRequestId } from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp delegation LIST proxy (read — GET). Forwards the optional
 * `?role=DELEGATOR|DELEGATE` query param verbatim to the producer. The
 * domain-facing GAP OIDC token is attached server-side in
 * `listDelegations()` — NEVER the operator token; NO `X-Tenant-Id`.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  try {
    const sp = new URL(req.url).searchParams;
    const roleRaw = sp.get('role');
    const role =
      roleRaw === 'DELEGATOR' || roleRaw === 'DELEGATE' ? roleRaw : undefined;
    const result = await listDelegations(role);
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}

/**
 * Same-origin erp delegation CREATE proxy (write — POST). The client posts
 * the create body + a console-generated `idempotencyKey`; this route
 * validates it and forwards to the UNCHANGED producer
 * `POST /api/erp/approval/delegations` via `createDelegation()`, which
 * attaches the domain-facing GAP token + `Idempotency-Key` server-side.
 * The delegator identity is the JWT sub — NOT in the body. The reason rides
 * in the body (NOT `X-Operator-Reason`).
 *
 * Producer errors 422 `DELEGATION_INVALID` (self-delegation / invalid
 * period), 400 `IDEMPOTENCY_KEY_REQUIRED`, 409 `IDEMPOTENCY_KEY_CONFLICT`,
 * 403 `PERMISSION_DENIED` / `TENANT_FORBIDDEN` pass through `mapErpError`
 * inline-actionably.
 */
export async function POST(req: Request) {
  const requestId = newRequestId();
  let body: ReturnType<typeof CreateDelegationBodySchema.parse>;
  try {
    body = CreateDelegationBodySchema.parse(await req.json());
  } catch {
    return NextResponse.json(
      {
        code: 'VALIDATION_ERROR',
        message: 'invalid create-delegation body',
      },
      { status: 400 },
    );
  }
  try {
    const { idempotencyKey, ...input } = body;
    const result = await createDelegation(input, idempotencyKey);
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
