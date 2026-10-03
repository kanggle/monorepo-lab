import {
  AgenciesScreen,
  FanSectionNote,
  getAgenciesSectionState,
} from '@/features/fan-directory';
import { resolveFanEligibility } from '../_eligibility';

export const dynamic = 'force-dynamic';

/**
 * Fan agencies list route (TASK-MONO-751 — ADR-MONO-079 D4-A). Server component; waterfall
 * registryDegraded → notEligible → forbidden → degraded → happy (same as the ecommerce
 * seller list).
 */
export default async function FanAgenciesPage() {
  const { eligible, registryDegraded } = await resolveFanEligibility();
  if (registryDegraded) {
    return <FanSectionNote title="소속사" flags={null} registryDegraded />;
  }
  const state = await getAgenciesSectionState(eligible);
  const note = <FanSectionNote title="소속사" flags={state} registryDegraded={false} />;
  if (state.notEligible || state.forbidden || state.degraded || !state.data) return note;
  return <AgenciesScreen agencies={state.data} />;
}
