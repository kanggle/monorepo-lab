import {
  ErpDelegationScreen,
  ErpSectionNotice,
  getErpDelegationState,
} from '@/features/erp-ops';
import { resolveErpEligibility } from '../_eligibility';
import { resolveErpCanWrite } from '../_write-access';

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
  const eligibility = await resolveErpEligibility();
  const { eligible, registryDegraded } = eligibility;
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

  // TASK-PC-FE-347 — 위임 생성 · 회수 follow ERP_APPROVAL_WRITE (contract § 2.4.3.4).
  const canWrite = await resolveErpCanWrite('/erp/delegation', eligibility);
  return (
    <ErpDelegationScreen
      initialDelegationFacts={state.delegationFacts}
      canWrite={canWrite}
    />
  );
}
