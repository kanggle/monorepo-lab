import { NextResponse } from 'next/server';
import { editOperatorRoles } from '@/features/operators/api/operators-api';
import {
  EditRolesBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin edit-roles proxy. Full-replace PATCH (an empty `roles: []`
 * removes all roles — high-impact, the client gates it behind a strong
 * confirm). The client supplies an operator-entered `reason` (the
 * reason-capture gate); the operator token + tenant are attached
 * server-side.
 *
 * PER-ENDPOINT HEADER MATRIX (§ 2.4.3): the api layer attaches
 * `X-Operator-Reason` ONLY — there is **NO `Idempotency-Key`** (the
 * producer does not list it; sending it is a contract deviation). This
 * proxy carries no idempotency key and never fabricates a reason.
 */
export async function POST(
  req: Request,
  { params }: { params: Promise<{ operatorId: string }> },
) {
  const requestId = newRequestId();
  const { operatorId } = await params;
  let body;
  try {
    body = EditRolesBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    const result = await editOperatorRoles(
      operatorId,
      body.roles,
      body.reason,
    );
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}
