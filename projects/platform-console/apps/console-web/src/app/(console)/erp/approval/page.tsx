import {
  ErpApprovalScreen,
  ErpSectionNotice,
  getErpApprovalState,
  resolveErpEligibility,
} from '@/features/erp-ops';

export const dynamic = 'force-dynamic';

const HEADING = 'ERP 결재함';

/**
 * erp **결재함** route (`/erp/approval` — TASK-PC-FE-076 drill-in split;
 * the approval-workflow slice of the former single `/erp` page,
 * TASK-PC-FE-051). A sibling under the sidebar ERP drill. Renders the
 * approval requests list + the caller's inbox; together the route's SOLE
 * degrade authority (§ 2.4.8 / § 2.5). approval has no `?asOf=` concept.
 * Shared eligibility + notice (PC-FE-076).
 */
export default async function ErpApprovalPage() {
  const { eligible, registryDegraded } = await resolveErpEligibility();
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

  return (
    <ErpApprovalScreen
      initialApprovalRequests={state.approvalRequests}
      initialApprovalInbox={state.approvalInbox}
    />
  );
}
