import { NextResponse } from 'next/server';
import {
  createDepartment,
  listDepartments,
} from '@/features/erp-ops/api/erp-api';
import { CreateDepartmentBodySchema } from '@/features/erp-ops/api/types';
import {
  buildListParams,
  mapErpError,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp departments LIST read proxy (read-only — GET).
 * The HttpOnly GAP OIDC access token is attached server-side in
 * `listDepartments()` (§ 2.4.8 reusing § 2.4.5 — NOT the operator
 * token). E3 `?asOf=` threaded through to the producer verbatim.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  try {
    const params = buildListParams(req);
    const result = await listDepartments(params);
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}

/**
 * Same-origin erp department CREATE proxy (write PILOT — TASK-PC-FE-046
 * / § 2.4.8 *Department write binding (PILOT)*). The client posts the
 * create body + a console-generated `idempotencyKey`; this route
 * validates it and forwards to the UNCHANGED producer
 * `POST /api/erp/masterdata/departments` via `createDepartment()`,
 * which attaches the GAP OIDC token + `Idempotency-Key` header
 * server-side (NEVER the operator token, NEVER `X-Operator-Reason` —
 * create has no producer reason slot). The mutation-only producer
 * errors (`409 MASTERDATA_DUPLICATE_KEY` / `IDEMPOTENCY_KEY_CONFLICT`,
 * `400 IDEMPOTENCY_KEY_REQUIRED`, `403 PERMISSION_DENIED`) pass through
 * `mapErpError` inline-actionably (no crash).
 */
export async function POST(req: Request) {
  const requestId = newRequestId();
  let body: ReturnType<typeof CreateDepartmentBodySchema.parse>;
  try {
    body = CreateDepartmentBodySchema.parse(await req.json());
  } catch {
    return NextResponse.json(
      { code: 'VALIDATION_ERROR', message: 'invalid create-department body' },
      { status: 400 },
    );
  }
  try {
    const { idempotencyKey, ...input } = body;
    const result = await createDepartment(input, idempotencyKey);
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
