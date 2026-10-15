import { NextResponse } from 'next/server';
import { inviteFirstAdmin } from '@/features/tenants/api/tenants-api';
import {
  FirstAdminInvitationBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin first-admin re-invitation proxy (TASK-MONO-773 S6 — the `/tenants` «다시 초대» row action for a new
 * address or a `PENDING` tenant with no invitation left; console-integration-contract § 2.6.4). → IAM
 * `POST /api/admin/tenants/{tenantId}/first-admin-invitation` (admin-api.md — SUPER_ADMIN `'*'` only).
 *
 * The SAME address is not this route: that is the invitation's own `:resend`
 * (`/api/operator-invitations/{id}/resend`). The body is validated with the create's `firstAdmin` rules (trimmed)
 * so a bad draft fails fast as `422` and never reaches IAM; `reason` → `X-Operator-Reason`, `idempotencyKey` →
 * `Idempotency-Key` (both required by the producer). `409 TENANT_NOT_PENDING` / `409
 * OPERATOR_INVITATION_ALREADY_PENDING` / `404 TENANT_NOT_FOUND` pass through inline.
 */
export async function POST(
  req: Request,
  { params }: { params: Promise<{ tenantId: string }> },
): Promise<NextResponse> {
  const requestId = newRequestId();
  const { tenantId } = await params;
  let body;
  try {
    body = FirstAdminInvitationBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    const result = await inviteFirstAdmin(
      tenantId,
      { email: body.email, displayName: body.displayName },
      body.reason,
      body.idempotencyKey,
    );
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapError(err, requestId);
  }
}
