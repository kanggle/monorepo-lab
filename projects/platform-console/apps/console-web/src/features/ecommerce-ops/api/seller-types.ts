import { z } from 'zod';
import type { StatusTone } from '@/shared/ui/StatusBadge';

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

const SELLER_STATUS_TONE: Record<SellerStatus, StatusTone> = {
  ACTIVE: 'success',
  PENDING_PROVISIONING: 'warning',
  SUSPENDED: 'neutral',
  CLOSED: 'danger',
};

/**
 * Maps a (possibly unknown) seller status to a shared semantic
 * {@link StatusTone} (rendered via the shared `<StatusBadge>` — TASK-PC-FE-158).
 * A value outside the known lifecycle → `neutral`, so the console never crashes
 * on a future producer status (TOLERANCE invariant). The raw status string
 * stays the badge label at the call site.
 */
export function sellerStatusTone(status: string): StatusTone {
  return SELLER_STATUS_TONE[status as SellerStatus] ?? 'neutral';
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
// List query params + pagination defaults
// ===========================================================================

export const SELLER_DEFAULT_PAGE_SIZE = 20;
export const SELLER_MAX_PAGE_SIZE = 100;

export interface SellerListParams {
  page?: number;
  size?: number;
}
