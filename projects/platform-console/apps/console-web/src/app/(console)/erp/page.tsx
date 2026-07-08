import {
  ErpOverviewScreen,
  ErpSectionNotice,
  getErpOverviewState,
  resolveErpEligibility,
} from '@/features/erp-ops';

export const dynamic = 'force-dynamic';

const HEADING = 'ERP 개요';

/**
 * erp domain **overview** route (`/erp` — TASK-PC-FE-232). Repoints the
 * domain root at a **live overview snapshot** (orthodox parity with every
 * other domain's 개요-at-root convention — `/wms`, `/scm`, `/finance`,
 * `/ecommerce`, `/iam` are all 개요 landings). The former masters surface
 * that used to live at `/erp` is **relocated** to `/erp/masters` (a
 * separate, unchanged route + feature — see
 * `app/(console)/erp/masters/page.tsx`).
 *
 * Supersedes(부분) the embedded TASK-PC-FE-161 overview snapshot — see
 * `features/erp-ops/api/overview-state.ts` (`getErpOverviewState`) for the
 * promotion: the 5 masterdata counts move here verbatim + 결재 대기 /
 * 활성 위임 counts are added.
 *
 * Server component. STRICTLY READ-ONLY. Mirrors the other erp routes'
 * eligibility waterfall (registry `productKey='erp'` pre-flight →
 * `resolveErpEligibility()` → registryDegraded → notEligible → happy). The
 * overview fan-out does its OWN per-cell degrade (§ overview-state.ts) —
 * a single count leg's 403/503 never blanks the whole page (AC-2), so
 * this route intentionally has NO page-level forbidden/degraded branch
 * beyond the shared registry pre-flight (identical shape to the promoted
 * TASK-PC-FE-161 masters-overview fan-out it replaces).
 *
 * E3 first-class: reads an optional `?asOf=` and threads it to the
 * masterdata count legs only (approval/delegation counts are current-time,
 * § overview-state.ts).
 */
export default async function ErpOverviewPage({
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

  const state = await getErpOverviewState(eligible, asOf);

  if (state.notEligible) {
    return <ErpSectionNotice kind="notEligible" heading={HEADING} />;
  }

  // Per-cell degrade/forbidden are rendered inline by ErpOverviewScreen
  // (so a sibling tile stays mounted) — never a whole-page block (AC-2).
  return <ErpOverviewScreen state={state} />;
}
