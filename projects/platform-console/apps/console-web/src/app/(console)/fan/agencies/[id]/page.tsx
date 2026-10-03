import { notFound } from 'next/navigation';
import {
  AgencyDetail,
  FanSectionNote,
  decodeSegment,
  getAgencyDetailSectionState,
} from '@/features/fan-directory';
import { resolveFanEligibility } from '../../_eligibility';

export const dynamic = 'force-dynamic';

/** Fan agency DETAIL route (TASK-MONO-751). Missing / cross-tenant agency → notFound(). */
export default async function FanAgencyDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const id = decodeSegment((await params).id);
  const { eligible, registryDegraded } = await resolveFanEligibility();
  if (registryDegraded) {
    return <FanSectionNote title="소속사 상세" flags={null} registryDegraded backHref="/fan/agencies" />;
  }
  const state = await getAgencyDetailSectionState(eligible, id);
  if (state.notFound) notFound();
  if (state.notEligible || state.forbidden || state.degraded || !state.data) {
    return (
      <FanSectionNote title="소속사 상세" flags={state} registryDegraded={false} backHref="/fan/agencies" />
    );
  }
  return <AgencyDetail agency={state.data} />;
}
