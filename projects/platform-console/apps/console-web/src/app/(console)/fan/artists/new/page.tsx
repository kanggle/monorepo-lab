import { DetailHeader } from '@/shared/ui/DetailHeader';
import {
  ArtistCreateForm,
  FanSectionNote,
} from '@/features/fan-directory';
import { resolveFanEligibility } from '../../_eligibility';

export const dynamic = 'force-dynamic';

/** Fan artist REGISTER route (TASK-MONO-751 AC-1). Eligibility pre-flight only. */
export default async function NewFanArtistPage() {
  const { eligible, registryDegraded } = await resolveFanEligibility();
  if (registryDegraded || !eligible) {
    return (
      <FanSectionNote
        title="아티스트 등록"
        flags={{ notEligible: !eligible, forbidden: false, degraded: false }}
        registryDegraded={registryDegraded}
      />
    );
  }
  return (
    <section>
      <DetailHeader
        headingId="fan-artist-new-heading"
        title="아티스트 등록"
        backHref="/fan/artists"
        backTestId="fan-artist-new-back"
      />
      <ArtistCreateForm />
    </section>
  );
}
