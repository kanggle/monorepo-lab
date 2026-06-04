import { NextResponse } from 'next/server';
import {
  approveApproval,
  rejectApproval,
  submitApproval,
  withdrawApproval,
} from '@/features/erp-ops/api/approval-api';
import {
  APPROVAL_TRANSITIONS,
  ApprovalTransitionBodySchema,
  transitionRequiresReason,
  type ApprovalTransition,
} from '@/features/erp-ops/api/approval-types';
import { mapErpError, newRequestId } from '../../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp approval TRANSITION proxy (write — POST only). Drives all
 * four producer transitions through ONE dynamic route, validating the
 * `[transition]` path segment against the allow-list
 * (`submit | approve | reject | withdraw`); any other segment → 404 (no
 * upstream call). Forwards the console-generated `Idempotency-Key` + the
 * body `reason` to the matching producer endpoint via the approval client,
 * which (for reject / withdraw / a reasoned approve) ALSO echoes the reason
 * via the `X-Operator-Reason` audit header server-side.
 *
 * reject / withdraw REQUIRE a non-blank reason (E4) — the route rejects a
 * missing reason with 400 `VALIDATION_ERROR` before any upstream call (the
 * client dialog also gates this, but the route is the authoritative guard).
 * submit / approve do not.
 *
 * NO GET / PUT / PATCH / DELETE handler — transitions are POST-only. The
 * producer state-machine errors (403 `APPROVAL_NOT_AUTHORIZED_APPROVER`,
 * 409 `APPROVAL_STATUS_TRANSITION_INVALID` / `APPROVAL_ALREADY_FINALIZED`,
 * 422 `APPROVAL_ROUTE_INVALID`, `IDEMPOTENCY_*`, 404
 * `APPROVAL_REQUEST_NOT_FOUND`) pass through `mapErpError` inline-actionably
 * (no crash — the console operator may not be the authorized approver, which
 * is a normal 403, not an error boundary).
 */
function isAllowedTransition(t: string): t is ApprovalTransition {
  return (APPROVAL_TRANSITIONS as readonly string[]).includes(t);
}

export async function POST(
  req: Request,
  { params }: { params: Promise<{ id: string; transition: string }> },
) {
  const requestId = newRequestId();
  const { id, transition } = await params;

  if (!isAllowedTransition(transition)) {
    return NextResponse.json(
      {
        code: 'NOT_FOUND',
        message: `unknown approval transition '${transition}'`,
      },
      { status: 404 },
    );
  }

  let body: ReturnType<typeof ApprovalTransitionBodySchema.parse>;
  try {
    body = ApprovalTransitionBodySchema.parse(await req.json());
  } catch {
    return NextResponse.json(
      { code: 'VALIDATION_ERROR', message: 'invalid transition body' },
      { status: 400 },
    );
  }

  const reason = body.reason?.trim();
  if (transitionRequiresReason(transition) && !reason) {
    // E4 — 반려 / 회수 사유 필수 (the producer would 400 anyway; the route
    // pre-rejects so no upstream call fires).
    return NextResponse.json(
      {
        code: 'VALIDATION_ERROR',
        message: `'${transition}' requires a reason`,
      },
      { status: 400 },
    );
  }

  try {
    let result;
    switch (transition) {
      case 'submit':
        result = await submitApproval(id, body.idempotencyKey);
        break;
      case 'approve':
        result = await approveApproval(id, body.idempotencyKey, reason);
        break;
      case 'reject':
        result = await rejectApproval(id, reason as string, body.idempotencyKey);
        break;
      case 'withdraw':
        result = await withdrawApproval(
          id,
          reason as string,
          body.idempotencyKey,
        );
        break;
    }
    return NextResponse.json({ data: result });
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
