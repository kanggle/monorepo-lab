import { NextResponse } from 'next/server';
import {
  listEmployeeAccountLinkProposals,
  proposeAccountLink,
} from '@/features/erp-ops/api/erp-api';
import { ProposeAccountLinkBodySchema } from '@/features/erp-ops/api/types';
import { mapErpError, newRequestId } from '../../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp employee ↔ IAM account link — one employee's proposal
 * HISTORY (GET) + PROPOSE (POST) (TASK-PC-FE-318; `masterdata-api.md`
 * § Employee ↔ IAM account link). Propose is an `erp.write` write under the
 * employee's department data scope — the producer is the authority (a 403 is
 * passed through inline, the console never pre-judges). `Idempotency-Key`
 * required; `accountId` form-checked only (non-blank, ≤64) — existence in IAM
 * is deliberately not checked at proposal time. The account id is a person
 * identifier and is never logged.
 */
export async function GET(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  const sp = new URL(req.url).searchParams;
  try {
    const result = await listEmployeeAccountLinkProposals(id, {
      page: sp.has('page') ? Number(sp.get('page')) : undefined,
      size: sp.has('size') ? Number(sp.get('size')) : undefined,
    });
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}

export async function POST(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  let body: ReturnType<typeof ProposeAccountLinkBodySchema.parse>;
  try {
    body = ProposeAccountLinkBodySchema.parse(await req.json());
  } catch {
    return NextResponse.json(
      { code: 'VALIDATION_ERROR', message: 'invalid account-link proposal body' },
      { status: 400 },
    );
  }
  try {
    const reason = body.reason?.trim() || undefined;
    const result = await proposeAccountLink(
      id,
      { accountId: body.accountId, reason },
      body.idempotencyKey,
    );
    return NextResponse.json({ data: result }, { status: 201 });
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
