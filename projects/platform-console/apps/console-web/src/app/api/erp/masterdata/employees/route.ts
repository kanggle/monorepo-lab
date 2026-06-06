import { NextResponse } from 'next/server';
import { listEmployees, createEmployee } from '@/features/erp-ops/api/erp-api';
import { CreateEmployeeBodySchema } from '@/features/erp-ops/api/types';
import {
  buildListParams,
  mapErpError,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp employees LIST read proxy (GET) + CREATE proxy (POST —
 * TASK-PC-FE-048). IAM OIDC domain-facing token attached server-side; E3
 * `?asOf=` threaded through on read. Confidential PII never logged. Create
 * carries an `Idempotency-Key` (no `X-Operator-Reason` — create has no
 * producer reason slot).
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  try {
    const params = buildListParams(req);
    const result = await listEmployees(params);
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}

export async function POST(req: Request) {
  const requestId = newRequestId();
  let body: ReturnType<typeof CreateEmployeeBodySchema.parse>;
  try {
    body = CreateEmployeeBodySchema.parse(await req.json());
  } catch {
    return NextResponse.json(
      { code: 'VALIDATION_ERROR', message: 'invalid create-employee body' },
      { status: 400 },
    );
  }
  try {
    const { idempotencyKey, ...input } = body;
    const result = await createEmployee(input, idempotencyKey);
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
