import {
  type ApprovalRequest,
  type CreateApprovalInput,
} from './approval-types';
import { callApproval, parseApprovalRequest } from './approval-call';

/**
 * erp `approval-service` WRITE client (TASK-PC-FE-051; extracted in
 * TASK-PC-FE-153 from the former single `approval-api.ts`) — create +
 * the 4 transitions, each carrying a console-generated `Idempotency-Key`.
 *
 *   createApprovalRequest (DRAFT) + submitApproval / approveApproval /
 *   rejectApproval / withdrawApproval
 *
 * MUTATION discipline (producer § Mutating endpoints — erp E4 / T1):
 *   - create + the 4 transitions each carry a console-generated
 *     `Idempotency-Key` (the api caller supplies it per attempt).
 *   - `reject` / `withdraw` REQUIRE a reason; `approve` optional; the
 *     reason rides in the request BODY **and** is echoed via the
 *     `X-Operator-Reason` header for the audit trail (producer § Operator
 *     reason). This is the SOLE erp surface that sends `X-Operator-Reason`
 *     (the masterdata surface deliberately never does — erp has no reason
 *     slot there).
 *
 * Server-only by construction — see `approval-call.ts` for the credential
 * + resilience posture. The public surface stays the `approval-api.ts`
 * barrel (these functions are re-exported verbatim).
 */

// ---------------------------------------------------------------------------
// writes — create + the 4 transitions (each carries an Idempotency-Key).
// ---------------------------------------------------------------------------

/** `POST /api/erp/approval/requests` — create a DRAFT request.
 * v2.0: when `input.approverIds` is a non-empty array, sends the
 * multi-stage body (`approverIds`); otherwise sends the legacy
 * single-approver body (`approverId`). Exactly one is forwarded. */
export async function createApprovalRequest(
  input: CreateApprovalInput,
  idempotencyKey: string,
): Promise<ApprovalRequest> {
  // Build the approver payload — multi-stage (v2.0) vs legacy (v1).
  const approverPayload =
    input.approverIds && input.approverIds.length > 0
      ? { approverIds: input.approverIds }
      : { approverId: input.approverId };

  return callApproval(
    {
      path: '/api/erp/approval/requests',
      logPath: '/api/erp/approval/requests',
      method: 'POST',
      idempotencyKey,
      body: {
        subjectType: input.subjectType,
        subjectId: input.subjectId,
        title: input.title,
        ...approverPayload,
        ...(input.reason ? { reason: input.reason } : {}),
      },
    },
    parseApprovalRequest,
  );
}

/** `POST /api/erp/approval/requests/{id}/submit` — DRAFT → SUBMITTED.
 *  No body fields / no reason (the route was fixed at create time). */
export async function submitApproval(
  id: string,
  idempotencyKey: string,
): Promise<ApprovalRequest> {
  return callApproval(
    {
      path: `/api/erp/approval/requests/${encodeURIComponent(id)}/submit`,
      logPath: '/api/erp/approval/requests/{id}/submit',
      method: 'POST',
      idempotencyKey,
      body: {},
    },
    parseApprovalRequest,
  );
}

/** `POST /api/erp/approval/requests/{id}/approve` — SUBMITTED → APPROVED.
 *  Reason OPTIONAL (echoed via `X-Operator-Reason` when present). */
export async function approveApproval(
  id: string,
  idempotencyKey: string,
  reason?: string,
): Promise<ApprovalRequest> {
  return callApproval(
    {
      path: `/api/erp/approval/requests/${encodeURIComponent(id)}/approve`,
      logPath: '/api/erp/approval/requests/{id}/approve',
      method: 'POST',
      idempotencyKey,
      ...(reason ? { operatorReason: reason } : {}),
      body: reason ? { reason } : {},
    },
    parseApprovalRequest,
  );
}

/** `POST /api/erp/approval/requests/{id}/reject` — SUBMITTED → REJECTED.
 *  Reason REQUIRED (echoed via `X-Operator-Reason`). */
export async function rejectApproval(
  id: string,
  reason: string,
  idempotencyKey: string,
): Promise<ApprovalRequest> {
  return callApproval(
    {
      path: `/api/erp/approval/requests/${encodeURIComponent(id)}/reject`,
      logPath: '/api/erp/approval/requests/{id}/reject',
      method: 'POST',
      idempotencyKey,
      operatorReason: reason,
      body: { reason },
    },
    parseApprovalRequest,
  );
}

/** `POST /api/erp/approval/requests/{id}/withdraw` — SUBMITTED → WITHDRAWN.
 *  Reason REQUIRED (echoed via `X-Operator-Reason`). Submitter-only (E3). */
export async function withdrawApproval(
  id: string,
  reason: string,
  idempotencyKey: string,
): Promise<ApprovalRequest> {
  return callApproval(
    {
      path: `/api/erp/approval/requests/${encodeURIComponent(id)}/withdraw`,
      logPath: '/api/erp/approval/requests/{id}/withdraw',
      method: 'POST',
      idempotencyKey,
      operatorReason: reason,
      body: { reason },
    },
    parseApprovalRequest,
  );
}
