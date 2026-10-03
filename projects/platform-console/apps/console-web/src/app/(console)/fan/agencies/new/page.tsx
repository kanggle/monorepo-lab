import { DetailHeader } from '@/shared/ui/DetailHeader';
import {
  AgencyCreateForm,
  FanSectionNote,
} from '@/features/fan-directory';
import { resolveFanEligibility } from '../../_eligibility';

export const dynamic = 'force-dynamic';

/** Fan agency CREATE route (TASK-MONO-751 AC-1). Eligibility pre-flight only. */
export default async function NewFanAgencyPage() {
  const { eligible, registryDegraded } = await resolveFanEligibility();
  if (registryDegraded || !eligible) {
    return (
      <FanSectionNote
        title="소속사 등록"
        flags={{ notEligible: !eligible, forbidden: false, degraded: false }}
        registryDegraded={registryDegraded}
      />
    );
  }
  return (
    <section>
      <DetailHeader
        headingId="fan-agency-new-heading"
        title="소속사 등록"
        backHref="/fan/agencies"
        backTestId="fan-agency-new-back"
      />
      <AgencyCreateForm />
    </section>
  );
}
