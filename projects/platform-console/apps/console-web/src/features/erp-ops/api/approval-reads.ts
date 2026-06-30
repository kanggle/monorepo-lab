import {
  ApprovalListResponseSchema,
  type ApprovalListResponse,
  type ApprovalRequest,
  type ApprovalListQueryParams,
  type ApprovalInboxQueryParams,
} from './approval-types';
import {
  callApproval,
  listQs,
  inboxQs,
  parseApprovalRequest,
} from './approval-call';

/**
 * erp `approval-service` READ client (TASK-PC-FE-051; extracted in
 * TASK-PC-FE-153 from the former single `approval-api.ts`). Consumes the
 * UNCHANGED producer `approval-api.md` (base path `/api/erp/approval`):
 *
 *   listApprovalRequests / getApprovalRequest / listApprovalInbox
 *
 * Server-only by construction — see `approval-call.ts` for the credential
 * + resilience posture. The public surface stays the `approval-api.ts`
 * barrel (these functions are re-exported verbatim).
 */

// ---------------------------------------------------------------------------
// reads.
// ---------------------------------------------------------------------------

/** `GET /api/erp/approval/requests` — scope-aware list. */
export async function listApprovalRequests(
  params: ApprovalListQueryParams = {},
): Promise<ApprovalListResponse> {
  return callApproval(
    {
      path: `/api/erp/approval/requests?${listQs(params)}`,
      logPath: '/api/erp/approval/requests',
    },
    (json) => ApprovalListResponseSchema.parse(json),
  );
}

/** `GET /api/erp/approval/requests/{id}` — detail incl. history. */
export async function getApprovalRequest(
  id: string,
): Promise<ApprovalRequest> {
  return callApproval(
    {
      path: `/api/erp/approval/requests/${encodeURIComponent(id)}`,
      logPath: '/api/erp/approval/requests/{id}',
    },
    parseApprovalRequest,
  );
}

/** `GET /api/erp/approval/inbox` — the caller's pending SUBMITTED queue. */
export async function listApprovalInbox(
  params: ApprovalInboxQueryParams = {},
): Promise<ApprovalListResponse> {
  return callApproval(
    {
      path: `/api/erp/approval/inbox?${inboxQs(params)}`,
      logPath: '/api/erp/approval/inbox',
    },
    (json) => ApprovalListResponseSchema.parse(json),
  );
}
