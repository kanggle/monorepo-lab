import {
  ErpDelegationScreen,
  ErpSectionNotice,
  getErpDelegationState,
  resolveErpEligibility,
} from '@/features/erp-ops';

export const dynamic = 'force-dynamic';

const HEADING = 'ERP 위임';

/**
 * erp **위임** route (`/erp/delegation` — TASK-PC-FE-076 drill-in split).
 * A sibling under the sidebar ERP drill. Combines the two former 위임
 * sub-sections: 관리 (`<DelegationScreen>` write surface, client-driven —
 * no server seed) + 현황 (read-model delegation facts, TASK-PC-FE-055).
 * The delegation-fact read-model leg is this route's SOLE degrade authority
 * (§ 2.4.8 / § 2.5); delegation has no `?asOf=` concept. Shared eligibility
 * + notice (PC-FE-076).
 */
export default async function ErpDelegationPage() {
  const { eligible, registryDegraded } = await resolveErpEligibility();
  if (registryDegraded) {
    return <ErpSectionNotice kind="registryDegraded" heading={HEADING} />;
  }

  const state = await getErpDelegationState(eligible);

  if (state.notEligible) {
    return <ErpSectionNotice kind="notEligible" heading={HEADING} />;
  }
  if (state.forbidden) {
    return <ErpSectionNotice kind="forbidden" heading={HEADING} />;
  }
  if (state.degraded) {
    return <ErpSectionNotice kind="degraded" heading={HEADING} />;
  }

  return <ErpDelegationScreen initialDelegationFacts={state.delegationFacts} />;
}
