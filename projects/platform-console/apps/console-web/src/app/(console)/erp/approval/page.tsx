import {
  ErpApprovalScreen,
  ErpSectionNotice,
  getErpApprovalState,
} from '@/features/erp-ops';
import { resolveErpEligibility } from '../_eligibility';
import { resolveErpCanWrite } from '../_write-access';

export const dynamic = 'force-dynamic';

const HEADING = 'ERP 결재함';

/**
 * erp **결재함** route (`/erp/approval` — TASK-PC-FE-076 drill-in split;
 * the approval-workflow slice of the former single `/erp` page,
 * TASK-PC-FE-051). A sibling under the sidebar ERP drill. Renders the
 * approval requests list + the caller's inbox; together the route's SOLE
 * degrade authority (§ 2.4.8 / § 2.5). approval has no `?asOf=` concept.
 * Shared eligibility + notice (PC-FE-076).
 *
 * `?request=<id>` (PC-FE-230) — the notification bell's approval deep-link
 * fallback opens this route with the target request preselected. An
 * unknown / stale id degrades gracefully to the list (the detail dialog
 * surfaces a not-found notice; never a crash).
 */
export default async function ErpApprovalPage({
  searchParams,
}: {
  searchParams?: Promise<{ request?: string }>;
}) {
  const eligibility = await resolveErpEligibility();
  const { eligible, registryDegraded } = eligibility;
  if (registryDegraded) {
    return <ErpSectionNotice kind="registryDegraded" heading={HEADING} />;
  }

  const state = await getErpApprovalState(eligible);

  if (state.notEligible) {
    return <ErpSectionNotice kind="notEligible" heading={HEADING} />;
  }
  if (state.forbidden) {
    return <ErpSectionNotice kind="forbidden" heading={HEADING} />;
  }
  if (state.degraded) {
    return <ErpSectionNotice kind="degraded" heading={HEADING} />;
  }

  const sp = (await searchParams) ?? {};
  const requestId = sp.request?.trim() || null;
  // TASK-PC-FE-347 — 상신 · 승인 · 반려 · 회수 follow ERP_APPROVAL_WRITE (contract § 2.4.3.4).
  const canWrite = await resolveErpCanWrite('/erp/approval', eligibility);

  return (
    <ErpApprovalScreen
      initialApprovalRequests={state.approvalRequests}
      initialApprovalInbox={state.approvalInbox}
      initialSelectedId={requestId}
      canWrite={canWrite}
    />
  );
}
