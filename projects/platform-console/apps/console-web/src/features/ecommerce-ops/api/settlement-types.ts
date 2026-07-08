import { z } from 'zod';
import type { StatusTone } from '@/shared/ui/StatusBadge';

/**
 * Feature-local types for the ecommerce `settlement-service` operator surface
 * (TASK-PC-FE-221 Phase A — the 8th ecommerce-ops facet). Drives the in-console
 * settlement READ screens: accrual lines, seller balance, commission rate,
 * settlement periods, and per-period payouts.
 *
 * Authoritative producer contract (do NOT redefine — consume read-only):
 *   ecommerce `settlement-service` operator REST, gateway-routed at
 *   `Path=/api/admin/settlements/**` (role `ECOMMERCE_OPERATOR`):
 *     GET /settlements/accruals?sellerId=&orderId=&page=&size=
 *     GET /settlements/sellers/{sellerId}/balance
 *     GET /settlements/commission-rates/{sellerId}
 *     GET /settlements/periods?page=&size=
 *     GET /settlements/periods/{periodId}/payouts?page=&size=
 * Consumer obligation: `console-integration-contract.md` § 2.4.10 (reuses the
 * § 2.4.10 admin-subtree base + domain-facing IAM OIDC credential rule — same
 * as products/orders/users/sellers).
 *
 * Base URL: ECOMMERCE_ADMIN_BASE_URL + /settlements (the ADMIN subtree
 * `/api/admin/settlements` — like sellers, NOT the public path).
 *
 * MONEY invariant (task Edge): every money field is an **integer minor-unit
 * amount** (`number`, KRW long; the KRW minor unit IS the won — scale 0).
 * REVERSAL accrual lines are NEGATIVE. The console renders money via
 * {@link minorToWon} (integer string formatting only — NO float arithmetic ever
 * derives a won value; the producer amount is displayed, not recomputed).
 *
 * rateBps is BASIS POINTS (integer; 1000 = 10.00%) — rendered by
 * {@link rateBpsToPercent} (integer division for display, never float math).
 *
 * TOLERANCE invariant: read shapes are permissive (`.passthrough()`); only the
 * fields the UI strictly needs are required, and every enum-like value is a free
 * `z.string()` so an unknown / future producer value never throws.
 */

// ===========================================================================
// MONEY + RATE display helpers (display-only; NO float arithmetic)
// ===========================================================================

/**
 * Renders an integer minor-unit KRW amount as a won string. The KRW minor unit
 * is the won itself (scale 0), so this is thousands-grouping + a `₩` prefix with
 * the sign hoisted in front (REVERSAL lines are negative → `-₩5,000`). NO float
 * math is applied — the producer's integer amount is displayed verbatim.
 */
export function minorToWon(minor: number): string {
  const sign = minor < 0 ? '-' : '';
  return `${sign}₩${Math.abs(minor).toLocaleString('ko-KR')}`;
}

/**
 * Renders an integer basis-points rate as a 2-decimal percent for DISPLAY only
 * (1000 → `10.00%`, 1250 → `12.50%`, 25 → `0.25%`). Uses integer division +
 * modulo (never `bps/100` float math) so the presentation is exact.
 */
export function rateBpsToPercent(bps: number): string {
  const sign = bps < 0 ? '-' : '';
  const abs = Math.abs(bps);
  const whole = Math.trunc(abs / 100);
  const frac = abs % 100;
  return `${sign}${whole}.${String(frac).padStart(2, '0')}%`;
}

// ===========================================================================
// ENUM tone maps (rendered via the shared <StatusBadge> — TASK-PC-FE-158)
// ===========================================================================

/** Accrual line type — a positive commission ACCRUAL or a negative REVERSAL
 *  (clawback, e.g. from a partial refund — ADR proportional clawback). */
export const ACCRUAL_TYPE_VALUES = ['ACCRUAL', 'REVERSAL'] as const;
export type AccrualType = (typeof ACCRUAL_TYPE_VALUES)[number];

const ACCRUAL_TYPE_TONE: Record<AccrualType, StatusTone> = {
  ACCRUAL: 'neutral',
  REVERSAL: 'warning',
};

/** Accrual type → tone. Unknown/future → neutral (tolerant, never throws). */
export function accrualTypeTone(type: string): StatusTone {
  return ACCRUAL_TYPE_TONE[type as AccrualType] ?? 'neutral';
}

/** Settlement period lifecycle — OPEN accrues; CLOSED folds accruals into
 *  PENDING payouts (irreversible — the close mutation is Phase B). */
export const PERIOD_STATUS_VALUES = ['OPEN', 'CLOSED'] as const;
export type PeriodStatus = (typeof PERIOD_STATUS_VALUES)[number];

const PERIOD_STATUS_TONE: Record<PeriodStatus, StatusTone> = {
  OPEN: 'progress',
  CLOSED: 'success',
};

export function periodStatusTone(status: string): StatusTone {
  return PERIOD_STATUS_TONE[status as PeriodStatus] ?? 'neutral';
}

/** Payout status — PENDING (accrual folded, awaiting execution), PAID, FAILED. */
export const PAYOUT_STATUS_VALUES = ['PENDING', 'PAID', 'FAILED'] as const;
export type PayoutStatus = (typeof PAYOUT_STATUS_VALUES)[number];

const PAYOUT_STATUS_TONE: Record<PayoutStatus, StatusTone> = {
  PENDING: 'warning',
  PAID: 'success',
  FAILED: 'danger',
};

export function payoutStatusTone(status: string): StatusTone {
  return PAYOUT_STATUS_TONE[status as PayoutStatus] ?? 'neutral';
}

/** Commission-rate resolution source. */
export const COMMISSION_SOURCE_VALUES = [
  'SELLER_OVERRIDE',
  'PLATFORM_DEFAULT',
] as const;
export type CommissionSource = (typeof COMMISSION_SOURCE_VALUES)[number];

/** Commission source → a short human label (ko). Unknown → the raw value. */
export function commissionSourceLabel(source: string): string {
  switch (source) {
    case 'SELLER_OVERRIDE':
      return '셀러 개별 설정';
    case 'PLATFORM_DEFAULT':
      return '플랫폼 기본값';
    default:
      return source;
  }
}

// ===========================================================================
// READ shapes
// ===========================================================================

/** A single append-only settlement ledger line (ACCRUAL or REVERSAL). */
export const AccrualLineSchema = z
  .object({
    accrualId: z.string(),
    orderId: z.string(),
    paymentId: z.string(),
    sellerId: z.string(),
    // Free string (tolerant) — the UI maps known ACCRUAL/REVERSAL to a tone.
    type: z.string(),
    grossMinor: z.number(),
    rateBps: z.number(),
    commissionMinor: z.number(),
    sellerNetMinor: z.number(),
    occurredAt: z.string(),
  })
  .passthrough();
export type AccrualLine = z.infer<typeof AccrualLineSchema>;

/** Paginated accrual-lines envelope (`items`, not `content`). */
export const AccrualsResponseSchema = z
  .object({
    items: z.array(AccrualLineSchema),
    page: z.number().int().nonnegative(),
    size: z.number().int().positive(),
    totalElements: z.number().nonnegative(),
  })
  .passthrough();
export type AccrualsResponse = z.infer<typeof AccrualsResponseSchema>;

/** Seller settlement balance rollup (as-of a timestamp). */
export const SellerBalanceSchema = z
  .object({
    sellerId: z.string(),
    accruedNetMinor: z.number(),
    platformCommissionMinor: z.number(),
    grossMinor: z.number(),
    accrualCount: z.number().int().nonnegative(),
    asOf: z.string(),
  })
  .passthrough();
export type SellerBalance = z.infer<typeof SellerBalanceSchema>;

/** Effective commission rate for a seller + its resolution source. */
export const CommissionRateSchema = z
  .object({
    sellerId: z.string(),
    rateBps: z.number(),
    // Free string (tolerant) — the UI maps known sources to a label.
    source: z.string(),
  })
  .passthrough();
export type CommissionRate = z.infer<typeof CommissionRateSchema>;

/** A settlement period (OPEN accrues; CLOSED is settled). */
export const SettlementPeriodSchema = z
  .object({
    periodId: z.string(),
    from: z.string(),
    to: z.string(),
    // Free string (tolerant) — the UI maps known OPEN/CLOSED to a tone.
    status: z.string(),
    closedAt: z.string().nullable().optional(),
    sellerCount: z.number().nullable().optional(),
  })
  .passthrough();
export type SettlementPeriod = z.infer<typeof SettlementPeriodSchema>;

/** Paginated settlement-periods envelope. */
export const PeriodsResponseSchema = z
  .object({
    items: z.array(SettlementPeriodSchema),
    page: z.number().int().nonnegative(),
    size: z.number().int().positive(),
    totalElements: z.number().nonnegative(),
  })
  .passthrough();
export type PeriodsResponse = z.infer<typeof PeriodsResponseSchema>;

/** A per-seller payout within a settlement period. */
export const PayoutSchema = z
  .object({
    payoutId: z.string(),
    sellerId: z.string(),
    payableNetMinor: z.number(),
    commissionMinor: z.number(),
    accrualCount: z.number().int().nonnegative(),
    // Free string (tolerant) — the UI maps known PENDING/PAID/FAILED to a tone.
    status: z.string(),
    payoutReference: z.string().nullable().optional(),
    paidAt: z.string().nullable().optional(),
  })
  .passthrough();
export type Payout = z.infer<typeof PayoutSchema>;

/** Paginated payouts envelope. */
export const PayoutsResponseSchema = z
  .object({
    items: z.array(PayoutSchema),
    page: z.number().int().nonnegative(),
    size: z.number().int().positive(),
    totalElements: z.number().nonnegative(),
  })
  .passthrough();
export type PayoutsResponse = z.infer<typeof PayoutsResponseSchema>;

// ===========================================================================
// List query params + pagination defaults
// ===========================================================================

export const SETTLEMENT_DEFAULT_PAGE_SIZE = 20;
export const SETTLEMENT_MAX_PAGE_SIZE = 100;

export interface AccrualsListParams {
  /** Optional seller filter (exact sellerId). */
  sellerId?: string;
  /** Optional order filter (exact orderId). */
  orderId?: string;
  page?: number;
  size?: number;
}

export interface PeriodsListParams {
  page?: number;
  size?: number;
}

export interface PayoutsListParams {
  page?: number;
  size?: number;
}
