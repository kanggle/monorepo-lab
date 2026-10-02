import {
  ArtistsScreen,
  FanSectionNote,
  getArtistsSectionState,
} from '@/features/fan-directory';
import { resolveFanEligibility } from '../_eligibility';

export const dynamic = 'force-dynamic';

/** Fan artists list route (TASK-MONO-751). PUBLISHED-only directory search (producer rule). */
export default async function FanArtistsPage() {
  const { eligible, registryDegraded } = await resolveFanEligibility();
  if (registryDegraded) {
    return <FanSectionNote title="아티스트" flags={null} registryDegraded />;
  }
  const state = await getArtistsSectionState(eligible);
  if (state.notEligible || state.forbidden || state.degraded || !state.data) {
    return <FanSectionNote title="아티스트" flags={state} registryDegraded={false} />;
  }
  return <ArtistsScreen artists={state.data} />;
}
