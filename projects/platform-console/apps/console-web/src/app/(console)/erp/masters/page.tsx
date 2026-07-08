import {
  ErpMastersScreen,
  ErpSectionNotice,
  getErpMastersState,
  resolveErpEligibility,
} from '@/features/erp-ops';

export const dynamic = 'force-dynamic';

const HEADING = 'ERP 마스터';

/**
 * erp **마스터** route (`/erp/masters` — TASK-PC-FE-010 surface;
 * TASK-PC-FE-076 drill-in split; relocated from the domain root `/erp` by
 * TASK-PC-FE-232, which repoints `/erp` at a standalone 개요 landing —
 * orthodox parity with every other domain's 개요-at-root convention
 * (Finance PC-FE-229 is the direct pattern twin). A pure route
 * relocation + embedded-overview-slot removal: the masters lists /
 * filters / write / retire / move-parent affordances and the
 * `/api/erp/masterdata/**` proxy are UNCHANGED. The former embedded
 * operator overview snapshot (TASK-PC-FE-161 `ErpMastersOverview` +
 * `getErpMastersOverviewState`) has been PROMOTED (not deleted) to the
 * standalone `/erp` overview — see `getErpOverviewState` +
 * `ErpOverviewScreen` in `features/erp-ops`.
 *
 * Server component. STRICTLY READ-ONLY for the seed (the masterdata write
 * affordances are client-driven through the same-origin proxy). erp is
 * reached server-side with the HttpOnly **IAM OIDC access token** (§ 2.4.8
 * reuses the § 2.4.5 per-domain credential rule). Eligibility + the four
 * notice states are resolved by the shared `resolveErpEligibility()` +
 * `<ErpSectionNotice>` (TASK-PC-FE-076 — identical across all 4 erp routes).
 *
 * E3 first-class: reads an optional `?asOf=` and threads it to the masters
 * loader verbatim (state-at-that-instant). Resilience (§ 2.5): 401 →
 * whole-session re-login (inside the loaders); 403 → inline forbidden;
 * 503 / timeout → only this route degrades.
 */
export default async function ErpMastersPage({
  searchParams,
}: {
  searchParams?: Promise<{ asOf?: string }>;
}) {
  const { eligible, registryDegraded } = await resolveErpEligibility();
  if (registryDegraded) {
    return <ErpSectionNotice kind="registryDegraded" heading={HEADING} />;
  }

  const sp = (await searchParams) ?? {};
  const asOf = sp.asOf?.trim() || null;

  const state = await getErpMastersState(eligible, asOf);

  if (state.notEligible) {
    return <ErpSectionNotice kind="notEligible" heading={HEADING} />;
  }
  if (state.forbidden) {
    return <ErpSectionNotice kind="forbidden" heading={HEADING} />;
  }
  if (state.degraded) {
    return <ErpSectionNotice kind="degraded" heading={HEADING} />;
  }

  return (
    <ErpMastersScreen
      initialDepartments={state.departments}
      initialEmployees={state.employees}
      initialJobGrades={state.jobGrades}
      initialCostCenters={state.costCenters}
      initialBusinessPartners={state.businessPartners}
      // TASK-PC-FE-046/048 — erp masterdata write across all 5 masters.
      // Eligible operators see the write affordances; the producer's E6
      // fail-CLOSED authz is the authority (a 403 is surfaced inline — the
      // console never pre-judges write authority). § 2.4.8 *Masterdata write*.
      mastersWritable
    />
  );
}
