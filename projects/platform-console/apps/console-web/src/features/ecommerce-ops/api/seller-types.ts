import { z } from 'zod';

/**
 * Feature-local types for the ecommerce `product-service` seller operator
 * surface — the sellers facet of the ecommerce console absorption
 * (TASK-PC-FE-090, ADR-MONO-031 § 2.4.10 7th area / ADR-MONO-030 Step 4 facet f).
 * Drives the in-console seller list, detail, and register screens.
 *
 * Authoritative producer contract (do NOT redefine — consume only):
 *   ecommerce `product-service` `AdminSellerController`
 *   `GET /api/admin/sellers` (list), `GET /api/admin/sellers/{sellerId}` (detail),
 *   `POST /api/admin/sellers` (register).
 *   Precondition: TASK-BE-375.
 * Consumer obligation: `console-integration-contract.md` § 2.4.10.5
 *
 * TOLERANCE invariant: read shapes are permissive (`.passthrough()`); only the
 * fields the UI strictly needs are required, everything else passes through.
 * An unknown / future field (or status) never throws.
 *
 * Lifecycle actions (provision/suspend/close) added by TASK-PC-FE-154 (ADR-MONO-042).
 * The seller domain has NO update/delete (CRUD); mutation = state transitions.
 *
 * Base URL: ECOMMERCE_ADMIN_BASE_URL + /sellers (the ADMIN subtree `/api/admin/sellers`
 * — unlike promotions/notifications/shippings which use ECOMMERCE_PUBLIC_BASE_URL).
 */

// ===========================================================================
// SELLER STATUS
// ===========================================================================

/**
 * Full seller lifecycle (ADR-MONO-042): a seller is born `PENDING_PROVISIONING`,
 * becomes `ACTIVE` on provision, and can be `SUSPENDED` (reversible lock) or
 * `CLOSED` (terminal). The producer may emit a future value — read shapes keep
 * `status: z.string()` so anything unknown passes through and renders neutral.
 */
export const SELLER_STATUS_VALUES = [
  'PENDING_PROVISIONING',
  'ACTIVE',
  'SUSPENDED',
  'CLOSED',
] as const;
export type SellerStatus = (typeof SELLER_STATUS_VALUES)[number];

/** Badge tone (label + Tailwind classes) for a seller status. */
export interface SellerStatusTone {
  label: string;
  className: string;
}

const SELLER_STATUS_TONES: Record<SellerStatus, SellerStatusTone> = {
  ACTIVE: { label: 'ACTIVE', className: 'bg-green-100 text-green-800' },
  PENDING_PROVISIONING: {
    label: 'PENDING_PROVISIONING',
    className: 'bg-amber-100 text-amber-800',
  },
  SUSPENDED: { label: 'SUSPENDED', className: 'bg-gray-200 text-gray-700' },
  CLOSED: { label: 'CLOSED', className: 'bg-red-100 text-red-800' },
};

/**
 * Maps a (possibly unknown) status string to a badge tone. A value outside the
 * known lifecycle renders with a neutral tone using the raw string as its label
 * — the console never crashes on a future producer status (TOLERANCE invariant).
 */
export function sellerStatusTone(status: string): SellerStatusTone {
  return (
    SELLER_STATUS_TONES[status as SellerStatus] ?? {
      label: status,
      className: 'bg-muted text-muted-foreground',
    }
  );
}

/** The lifecycle actions valid from a given status (drives the detail UI). */
export type SellerLifecycleAction = 'provision' | 'suspend' | 'close';

export function sellerActionsFor(status: string): SellerLifecycleAction[] {
  switch (status) {
    case 'PENDING_PROVISIONING':
      return ['provision'];
    case 'ACTIVE':
      return ['suspend', 'close'];
    case 'SUSPENDED':
      return ['close'];
    default:
      // CLOSED (terminal) or any unknown status → no actions.
      return [];
  }
}

// ===========================================================================
// READ shapes
// ===========================================================================

/** List — seller summary row. */
export const SellerSummarySchema = z
  .object({
    sellerId: z.string(),
    displayName: z.string(),
    status: z.string(),
    createdAt: z.string(),
  })
  .passthrough();
export type SellerSummary = z.infer<typeof SellerSummarySchema>;

/** List envelope. */
export const SellerListSchema = z
  .object({
    content: z.array(SellerSummarySchema),
    page: z.number().int().nonnegative(),
    size: z.number().int().positive(),
    totalElements: z.number().nonnegative(),
  })
  .passthrough();
export type SellerList = z.infer<typeof SellerListSchema>;

/** Detail — full seller detail (adds updatedAt). */
export const SellerDetailSchema = z
  .object({
    sellerId: z.string(),
    displayName: z.string(),
    status: z.string(),
    createdAt: z.string(),
    updatedAt: z.string().optional().nullable(),
  })
  .passthrough();
export type SellerDetail = z.infer<typeof SellerDetailSchema>;

/** Register response — { sellerId }. */
export const RegisterSellerResponseSchema = z
  .object({ sellerId: z.string() })
  .passthrough();
export type RegisterSellerResponse = z.infer<typeof RegisterSellerResponseSchema>;

// ===========================================================================
// WRITE request bodies
// ===========================================================================

/**
 * RegisterSellerRequest body: `sellerId` (≤64 chars, non-blank) + `displayName` (non-blank).
 * Used by both the route handler (Zod validation) and the client form.
 */
export const RegisterSellerBodySchema = z.object({
  sellerId: z
    .string()
    .min(1, '셀러 ID를 입력해 주세요.')
    .max(64, '셀러 ID는 64자 이하여야 합니다.'),
  displayName: z.string().min(1, '셀러 이름을 입력해 주세요.'),
});
export type RegisterSellerBody = z.infer<typeof RegisterSellerBodySchema>;

// ===========================================================================
// SUMMARY (TASK-PC-FE-160 — period-based counts)
// ===========================================================================

/** GET /admin/sellers/summary — period-based count.
 *  Response: { today, week, month, total } all non-negative integers. */
export const SellerAreaSummarySchema = z
  .object({
    today: z.number().int().nonnegative(),
    week: z.number().int().nonnegative(),
    month: z.number().int().nonnegative(),
    total: z.number().int().nonnegative(),
  })
  .passthrough();
export type SellerAreaSummary = z.infer<typeof SellerAreaSummarySchema>;

// ===========================================================================
// List query params + pagination defaults
// ===========================================================================

export const SELLER_DEFAULT_PAGE_SIZE = 20;
export const SELLER_MAX_PAGE_SIZE = 100;

export interface SellerListParams {
  page?: number;
  size?: number;
}
