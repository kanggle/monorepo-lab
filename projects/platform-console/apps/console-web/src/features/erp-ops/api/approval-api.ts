/**
 * Server-side erp `approval-service` workflow client (TASK-PC-FE-051 —
 * ADR-MONO-016 § D3.1 parity slice). Public barrel — re-exports the read
 * + mutation operations from the operation-group sub-modules split out in
 * TASK-PC-FE-153 (behaviour-preserving; this import path is the stable
 * public surface consumed by `erp-state.ts` + the
 * `/api/erp/approval/**` route handlers). Consumes the UNCHANGED producer
 * `approval-api.md` (base path `/api/erp/approval`):
 *
 *   read   listApprovalRequests / getApprovalRequest / listApprovalInbox
 *   write  createApprovalRequest (DRAFT) + the 4 transitions
 *          submitApproval / approveApproval / rejectApproval /
 *          withdrawApproval
 *
 * Server-only by construction (same posture as `erp-api.ts`); the shared
 * hardened call site, credential posture (domain-facing IAM OIDC token,
 * NEVER `getOperatorToken()`; no `X-Tenant-Id`), § 2.5 resilience
 * taxonomy, and the per-mutation `Idempotency-Key` / `X-Operator-Reason`
 * discipline all live in the sub-modules below.
 */

export {
  listApprovalRequests,
  getApprovalRequest,
  listApprovalInbox,
} from './approval-reads';
export {
  createApprovalRequest,
  submitApproval,
  approveApproval,
  rejectApproval,
  withdrawApproval,
} from './approval-mutations';
