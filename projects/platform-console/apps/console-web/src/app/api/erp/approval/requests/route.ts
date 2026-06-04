import { NextResponse } from 'next/server';
import {
  createApprovalRequest,
  listApprovalRequests,
} from '@/features/erp-ops/api/approval-api';
import {
  CreateApprovalBodySchema,
  type ApprovalListQueryParams,
} from '@/features/erp-ops/api/approval-types';
import { mapErpError, newRequestId } from '../../_proxy';

export const runtime = 'nodejs';

/** Builds the producer list query from the incoming request's
 *  search-params (status / role / page / size forwarded verbatim). */
function buildApprovalListParams(req: Request): ApprovalListQueryParams {
  const sp = new URL(req.url).searchParams;
  const out: ApprovalListQueryParams = {};
  const status = sp.get('status');
  if (status) out.status = status;
  const role = sp.get('role');
  if (role) out.role = role;
  if (sp.has('page')) out.page = Number(sp.get('page'));
  if (sp.has('size')) out.size = Number(sp.get('size'));
  return out;
}

/**
 * Same-origin erp approval LIST proxy (read — GET). The domain-facing GAP
 * OIDC token is attached server-side in `listApprovalRequests()`
 * (TASK-PC-FE-051 / § 2.4.8 reuse — NOT the operator token). Scope-aware
 * producer list (the caller sees requests in their data scope + where they
 * are submitter / approver).
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  try {
    const result = await listApprovalRequests(buildApprovalListParams(req));
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}

/**
 * Same-origin erp approval CREATE proxy (write — POST). The client posts the
 * create body + a console-generated `idempotencyKey`; this route validates
 * it and forwards to the UNCHANGED producer `POST /api/erp/approval/requests`
 * via `createApprovalRequest()`, which attaches the domain-facing GAP token +
 * `Idempotency-Key` server-side (NEVER the operator token). Create is a DRAFT
 * — no master / route validation happens here (deferred to submit). The
 * mutation-only producer errors (`400 IDEMPOTENCY_KEY_REQUIRED`,
 * `409 IDEMPOTENCY_KEY_CONFLICT`, `403 PERMISSION_DENIED` /
 * `DATA_SCOPE_FORBIDDEN`) pass through `mapErpError` inline-actionably.
 */
export async function POST(req: Request) {
  const requestId = newRequestId();
  let body: ReturnType<typeof CreateApprovalBodySchema.parse>;
  try {
    body = CreateApprovalBodySchema.parse(await req.json());
  } catch {
    return NextResponse.json(
      { code: 'VALIDATION_ERROR', message: 'invalid create-approval body' },
      { status: 400 },
    );
  }
  try {
    const { idempotencyKey, ...input } = body;
    const result = await createApprovalRequest(input, idempotencyKey);
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
