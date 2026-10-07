import { NextResponse } from 'next/server';
import {
  acceptAccountLink,
  declineAccountLink,
  revokeAccountLink,
} from '@/features/erp-ops/api/erp-api';
import {
  ACCOUNT_LINK_PROPOSAL_ACTIONS,
  AccountLinkProposalActionBodySchema,
  proposalActionRequiresReason,
  type AccountLinkProposalAction,
} from '@/features/erp-ops/api/types';
import { mapErpError, newRequestId } from '../../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp account-link PROPOSAL action proxy (POST only —
 * TASK-PC-FE-318). One dynamic route for the three proposal actions, the
 * `[action]` segment validated against the allow-list
 * (`accept | decline | revoke`); anything else → 404 with no upstream call
 * (same shape as the approval `[transition]` route).
 *
 * 🔴 accept / decline are written by the ACCOUNT OWNER (caller `sub ==
 * proposal.accountId`), NOT by an `erp.write` holder; revoke is the
 * `erp.write` side. The producer enforces all of it — 403
 * `EMPLOYEE_LINK_NOT_ADDRESSEE` / `EMPLOYEE_LINK_SELF_ACCEPT` and 409
 * `EMPLOYEE_LINK_CONFLICT` (`details.cause`) pass through inline.
 * `Idempotency-Key` required on all three; revoke requires a reason (body).
 */
function isAllowedAction(a: string): a is AccountLinkProposalAction {
  return (ACCOUNT_LINK_PROPOSAL_ACTIONS as readonly string[]).includes(a);
}

export async function POST(
  req: Request,
  { params }: { params: Promise<{ proposalId: string; action: string }> },
) {
  const requestId = newRequestId();
  const { proposalId, action } = await params;

  if (!isAllowedAction(action)) {
    return NextResponse.json(
      { code: 'NOT_FOUND', message: `unknown account-link action '${action}'` },
      { status: 404 },
    );
  }

  let body: ReturnType<typeof AccountLinkProposalActionBodySchema.parse>;
  try {
    body = AccountLinkProposalActionBodySchema.parse(await req.json());
  } catch {
    return NextResponse.json(
      { code: 'VALIDATION_ERROR', message: 'invalid account-link action body' },
      { status: 400 },
    );
  }

  const reason = body.reason?.trim() || undefined;
  if (proposalActionRequiresReason(action) && !reason) {
    return NextResponse.json(
      { code: 'VALIDATION_ERROR', message: `'${action}' requires a reason` },
      { status: 400 },
    );
  }

  try {
    let result: unknown;
    switch (action) {
      case 'accept':
        result = await acceptAccountLink(proposalId, body.idempotencyKey);
        break;
      case 'decline':
        result = await declineAccountLink(proposalId, reason, body.idempotencyKey);
        break;
      case 'revoke':
        result = await revokeAccountLink(
          proposalId,
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
