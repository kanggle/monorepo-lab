import { notFound } from 'next/navigation';
import {
  ArtistDetail,
  FanSectionNote,
  decodeSegment,
  getArtistDetailSectionState,
} from '@/features/fan-directory';
import { resolveFanEligibility } from '../../_eligibility';

export const dynamic = 'force-dynamic';

/** Fan artist DETAIL route (TASK-MONO-751). Reaches DRAFT / ARCHIVED artists by id. */
export default async function FanArtistDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const id = decodeSegment((await params).id);
  const { eligible, registryDegraded } = await resolveFanEligibility();
  if (registryDegraded) {
    return <FanSectionNote title="아티스트 상세" flags={null} registryDegraded backHref="/fan/artists" />;
  }
  const state = await getArtistDetailSectionState(eligible, id);
  if (state.notFound) notFound();
  if (state.notEligible || state.forbidden || state.degraded || !state.data) {
    return (
      <FanSectionNote title="아티스트 상세" flags={state} registryDegraded={false} backHref="/fan/artists" />
    );
  }
  return <ArtistDetail artist={state.data} />;
}
