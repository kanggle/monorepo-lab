import {
  ErpOrgViewScreen,
  ErpSectionNotice,
  getErpOrgViewState,
  resolveErpEligibility,
} from '@/features/erp-ops';

export const dynamic = 'force-dynamic';

const HEADING = 'ERP 통합 조회';

/**
 * erp **통합 조회** route (`/erp/orgview` — TASK-PC-FE-076 drill-in split;
 * the read-model org-view slice of the former single `/erp` page). A
 * sibling under the sidebar ERP drill. Renders ONLY the read-model
 * employee org-view card (TASK-PC-FE-049/069). The read-model leg is this
 * route's SOLE degrade authority (§ 2.4.8 / § 2.5). E3 `?asOf=` threads to
 * the org-view query verbatim. Shared eligibility + notice (PC-FE-076).
 */
export default async function ErpOrgViewPage({
  searchParams,
}: {
  searchParams?: Promise<{ asOf?: string }>;
}) {
  const { eligible, registryDegraded } = await resolveErpEligibility();
  if (registryDegraded) {
    return <ErpSectionNotice kind="registryDegraded" heading={HEADING} />;
  }

  const sp = (await searchParams) ?? {};
  const asOf = sp.asOf?.trim() || null;

  const state = await getErpOrgViewState(eligible, asOf);

  if (state.notEligible) {
    return <ErpSectionNotice kind="notEligible" heading={HEADING} />;
  }
  if (state.forbidden) {
    return <ErpSectionNotice kind="forbidden" heading={HEADING} />;
  }
  if (state.degraded) {
    return <ErpSectionNotice kind="degraded" heading={HEADING} />;
  }

  return <ErpOrgViewScreen initialEmployeeOrgViews={state.employeeOrgViews} />;
}
