import {
  EntryPolicySchema,
  EnrolmentSummarySchema,
  type EntryPolicy,
  type EnrolmentSummary,
} from './types';
import { callEntryPolicy, ENTRY_POLICY_PATH } from './entry-policy-client';

/**
 * Server-side tenant entry-policy API functions (TASK-MONO-771 S5,
 * admin-api.md § Tenant Entry Policy). Imported only from server components
 * and `runtime = 'nodejs'` route handlers.
 */

/** GET — the tenant's policy (`requireMfa=false` with null stamps when never set). */
export async function getEntryPolicy(tenantId: string): Promise<EntryPolicy> {
  return callEntryPolicy(
    { method: 'GET', path: ENTRY_POLICY_PATH(tenantId) },
    (json) => EntryPolicySchema.parse(json),
  );
}

/**
 * PUT — full replace. `{ requireMfa }` is the producer body; the reason leaves
 * as `X-Operator-Reason`. No `Idempotency-Key` (§ 2.4.3.3 — MUST NOT be sent).
 */
export async function setEntryPolicy(
  tenantId: string,
  requireMfa: boolean,
  reason: string,
): Promise<EntryPolicy> {
  return callEntryPolicy(
    {
      method: 'PUT',
      path: ENTRY_POLICY_PATH(tenantId),
      reason,
      body: { requireMfa },
    },
    (json) => EntryPolicySchema.parse(json),
  );
}

/** GET — the «켜기 전 사전 점검» counts (advisory; the caller degrades to «no number»). */
export async function getEnrolmentSummary(tenantId: string): Promise<EnrolmentSummary> {
  return callEntryPolicy(
    { method: 'GET', path: `${ENTRY_POLICY_PATH(tenantId)}/enrolment-summary` },
    (json) => EnrolmentSummarySchema.parse(json),
  );
}
