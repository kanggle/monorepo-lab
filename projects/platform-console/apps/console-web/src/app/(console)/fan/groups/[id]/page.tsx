import { notFound } from 'next/navigation';
import {
  FanSectionNote,
  GroupDetail,
  decodeSegment,
  getGroupDetailSectionState,
} from '@/features/fan-directory';
import { resolveFanEligibility } from '../../_eligibility';

export const dynamic = 'force-dynamic';

/** Fan artist-group DETAIL route (TASK-MONO-751). Missing / cross-tenant group → notFound(). */
export default async function FanGroupDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const id = decodeSegment((await params).id);
  const { eligible, registryDegraded } = await resolveFanEligibility();
  if (registryDegraded) {
    return <FanSectionNote title="아티스트 그룹 상세" flags={null} registryDegraded backHref="/fan/groups" />;
  }
  const state = await getGroupDetailSectionState(eligible, id);
  if (state.notFound) notFound();
  if (state.notEligible || state.forbidden || state.degraded || !state.data) {
    return (
      <FanSectionNote title="아티스트 그룹 상세" flags={state} registryDegraded={false} backHref="/fan/groups" />
    );
  }
  return <GroupDetail group={state.data} />;
}
