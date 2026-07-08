import { mapSectionResilience, mapDetailResilience } from './section-state';
import { listAccruals, listPeriods, listPayouts } from './settlements-api';
import type {
  AccrualsResponse,
  PeriodsResponse,
  PayoutsResponse,
  AccrualsListParams,
} from './settlement-types';

/**
 * Server-side ecommerce settlement section state for the
 * `(console)/ecommerce/settlements` routes (TASK-PC-FE-221 Phase A). Mirrors
 * `sellers-state.ts` — the eligibility gate + § 2.5 resilience boundary are
 * byte-identical.
 *
 * Eligibility gate: the page resolves the operator's ecommerce eligibility from
 * the data-driven registry and passes it here. If not eligible the section
 * blocks with an actionable state and NO ecommerce call is made.
 *
 * Resilience boundary (§ 2.4.10 / § 2.5):
 *   - `401` (IAM OIDC session expired) → `redirect('/login')` — WHOLE-SESSION.
 *   - `403` (TENANT_FORBIDDEN / role-insufficient) → non-crashing inline
 *     "not available to role" (NOT a fake degrade — task Edge).
 *   - `503` / timeout / network → DEGRADED — ONLY this section degrades.
 *   - any other producer error → degrade rather than crash.
 */
export interface SettlementsSectionState {
  /** Server-seeded page-0 accrual lines (the accruals table's initialData). */
  accruals: AccrualsResponse | null;
  /** Server-seeded page-0 settlement periods (the periods table's initialData). */
  periods: PeriodsResponse | null;
  /** True when the operator is not ecommerce-eligible — actionable block. */
  notEligible: boolean;
  /** True on a role-insufficient (403) producer response — inline. */
  forbidden: boolean;
  /** True on 503 / timeout / network — section degrades only. */
  degraded: boolean;
}

const EMPTY: SettlementsSectionState = {
  accruals: null,
  periods: null,
  notEligible: false,
  forbidden: false,
  degraded: false,
};

/**
 * Landing-page seed: accruals (page 0, optional filters) + settlement periods
 * (page 0), fetched in parallel. Both endpoints share the same gateway + role,
 * so a 403/503 on either maps identically (a single try/catch is faithful).
 *
 * @param eligible whether the operator is ecommerce-eligible, resolved by the
 *   page from the data-driven registry. `false` ⇒ block (no call).
 * @param accrualParams optional accrual filters (sellerId / orderId / page / size).
 */
export async function getSettlementsSectionState(
  eligible: boolean,
  accrualParams: AccrualsListParams = {},
): Promise<SettlementsSectionState> {
  if (!eligible) {
    return { ...EMPTY, notEligible: true };
  }

  try {
    const [accruals, periods] = await Promise.all([
      listAccruals({ page: 0, size: 20, ...accrualParams }),
      listPeriods({ page: 0, size: 20 }),
    ]);
    return { ...EMPTY, accruals, periods };
  } catch (err) {
    return { ...EMPTY, ...mapSectionResilience(err) };
  }
}

export interface PeriodPayoutsSectionState {
  /** The period whose payouts are being viewed (echoed for the header). */
  periodId: string;
  /** Server-seeded page-0 payouts for the period. */
  payouts: PayoutsResponse | null;
  notEligible: boolean;
  forbidden: boolean;
  /** True on 404 SETTLEMENT_NOT_FOUND (unknown / cross-tenant period). */
  notFound: boolean;
  degraded: boolean;
}

/** Period detail-page section state (the `periods/[id]` route — payouts list). */
export async function getPeriodPayoutsSectionState(
  eligible: boolean,
  periodId: string,
): Promise<PeriodPayoutsSectionState> {
  const base: PeriodPayoutsSectionState = {
    periodId,
    payouts: null,
    notEligible: false,
    forbidden: false,
    notFound: false,
    degraded: false,
  };
  if (!eligible) {
    return { ...base, notEligible: true };
  }
  try {
    const payouts = await listPayouts(periodId, { page: 0, size: 20 });
    return { ...base, payouts };
  } catch (err) {
    return { ...base, ...mapDetailResilience(err) };
  }
}
