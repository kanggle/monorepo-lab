import {
  FanSectionNote,
  GroupsScreen,
} from '@/features/fan-directory';
import { resolveFanEligibility } from '../_eligibility';

export const dynamic = 'force-dynamic';

/**
 * Fan artist-groups route (TASK-MONO-751). No server read: the producer has no group list
 * endpoint — the screen opens a group by id or creates one.
 */
export default async function FanGroupsPage() {
  const { eligible, registryDegraded } = await resolveFanEligibility();
  if (registryDegraded || !eligible) {
    return (
      <FanSectionNote
        title="아티스트 그룹"
        flags={{ notEligible: !eligible, forbidden: false, degraded: false }}
        registryDegraded={registryDegraded}
      />
    );
  }
  return <GroupsScreen />;
}
