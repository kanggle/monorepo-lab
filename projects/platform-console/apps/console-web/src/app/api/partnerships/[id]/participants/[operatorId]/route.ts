import { NextResponse } from 'next/server';
import {
  addParticipant,
  removeParticipant,
} from '@/features/partnerships/api/partnerships-api';
import {
  ParticipantAddBodySchema,
  ReasonBodySchema,
  IdParamSchema,
  mapError,
  badRequest,
  newRequestId,
} from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin partnership participant proxy (TASK-PC-FE-187) — the partner
 * assigns / offboards its OWN operator on an ACTIVE partnership (D4):
 *
 *   - POST   → assign the (partnership, operator) participant. 201 + row.
 *              Optional `participantScope` narrows within delegatedScope
 *              (omitted/null ⟺ full delegatedScope, net-zero default).
 *   - DELETE → remove the participant. 204 no content. The host-reach
 *              derivation dies on the next request (D6 individual offboarding).
 *
 * PER-ENDPOINT HEADER MATRIX: `X-Operator-Reason` ONLY (required) — NO
 * `Idempotency-Key`. The client supplies the `reason` in the body; the operator
 * token + active tenant + the `X-Operator-Reason` header are attached
 * server-side by the api layer. The tenant is the server active tenant (D2 —
 * `X-Tenant-Id` == partner_tenant_id); the client supplies NO tenant.
 *
 * Producer errors — 403 PARTNERSHIP_SCOPE_DENIED / 404 OPERATOR_NOT_FOUND /
 * 404 PARTICIPANT_NOT_FOUND / 422 PARTICIPANT_NOT_OWN_OPERATOR /
 * 422 PARTICIPANT_SCOPE_EXCEEDS_DELEGATION — map inline; 401 → re-login;
 * 503/timeout → section degrade.
 *
 * POST/DELETE only: no other method handler is exported — Next.js returns 405.
 */
export async function POST(
  req: Request,
  { params }: { params: Promise<{ id: string; operatorId: string }> },
) {
  const requestId = newRequestId();
  const { id, operatorId } = await params;
  const idOk = IdParamSchema.safeParse(id);
  const opOk = IdParamSchema.safeParse(operatorId);
  if (!idOk.success || !opOk.success) return badRequest();

  let body;
  try {
    body = ParticipantAddBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    const result = await addParticipant(
      idOk.data,
      opOk.data,
      body.participantScope ?? null,
      body.reason,
    );
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapError(err, requestId);
  }
}

export async function DELETE(
  req: Request,
  { params }: { params: Promise<{ id: string; operatorId: string }> },
) {
  const requestId = newRequestId();
  const { id, operatorId } = await params;
  const idOk = IdParamSchema.safeParse(id);
  const opOk = IdParamSchema.safeParse(operatorId);
  if (!idOk.success || !opOk.success) return badRequest();

  let body;
  try {
    body = ReasonBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    await removeParticipant(idOk.data, opOk.data, body.reason);
    return new NextResponse(null, { status: 204 });
  } catch (err) {
    return mapError(err, requestId);
  }
}
