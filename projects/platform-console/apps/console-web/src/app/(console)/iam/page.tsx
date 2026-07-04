import { getIamOverviewState, IamOverviewScreen } from '@/features/iam-overview';

export const dynamic = 'force-dynamic';

/**
 * IAM 개요 라우트 (TASK-PC-FE-180). The IAM drill's first entry point — now a
 * **live operator overview snapshot** (운영자·계정·감사 현황) instead of the
 * former static guide (relocated to `/iam/guide`). A server-side DIRECT fan-out
 * over the existing IAM admin-service list endpoints (operators/accounts/audit),
 * per-cell degrade/forbidden with a whole-session re-login on any leg's 401
 * (handled inside `getIamOverviewState`). `force-dynamic` — the snapshot is a
 * live per-request read (contrast the static `/iam/guide`).
 */
export default async function IamOverviewPage() {
  const state = await getIamOverviewState();
  return (
    <section aria-labelledby="iam-overview-heading">
      <h1 id="iam-overview-heading" className="mb-6 text-2xl font-semibold">
        IAM 개요
      </h1>
      <IamOverviewScreen state={state} />
    </section>
  );
}
