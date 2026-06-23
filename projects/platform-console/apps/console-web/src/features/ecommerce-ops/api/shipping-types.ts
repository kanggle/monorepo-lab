import { z } from 'zod';

/**
 * Feature-local types for the ecommerce `shipping-service` operator surface —
 * the shippings facet of the ecommerce write binding federated by the console
 * (TASK-PC-FE-088, ADR-MONO-031 Phase 4b). Drives the in-console equivalent
 * of the standalone `admin-dashboard` shipping screens: list / status-change /
 * refresh-tracking.
 *
 * Authoritative producer contract (do NOT redefine — consume only):
 *   ecommerce `shipping-service`
 *   `ShippingController` (`/api/shippings/**`, non-admin path — BE-369 tenant_id)
 * Consumer obligation: `console-integration-contract.md` § 2.4.10.3
 * (inherits §2.4.10 cross-cutting rules verbatim).
 *
 * TOLERANCE invariant: read shapes are permissive (`.passthrough()`); only the
 * fields the UI strictly needs are required, everything else passes through.
 * An unknown / future `status` enum parses to a generic string and NEVER throws.
 *
 * State machine (ShippingStatus.java confirmed — STRICTLY LINEAR, single successor):
 *   PREPARING → SHIPPED → IN_TRANSIT → DELIVERED
 *   DELIVERED is terminal. PREPARING→SHIPPED requires carrier+trackingNumber.
 */

// ===========================================================================
// SHIPPING STATUS
// ===========================================================================

export const SHIPPING_STATUS_VALUES = [
  'PREPARING',
  'SHIPPED',
  'IN_TRANSIT',
  'DELIVERED',
] as const;
export type ShippingStatus = (typeof SHIPPING_STATUS_VALUES)[number];

/**
 * Strictly linear single-successor state machine.
 * Returns the one allowed forward transition, or null for terminal states.
 */
const LINEAR_TRANSITIONS: Record<string, ShippingStatus | null> = {
  PREPARING: 'SHIPPED',
  SHIPPED: 'IN_TRANSIT',
  IN_TRANSIT: 'DELIVERED',
  DELIVERED: null,
};

/**
 * Returns the single allowed next status from the given `status`, or null if
 * terminal or unknown. The UI must expose only this one forward transition.
 * Unknown / future statuses return null (fail-safe — no UI action offered).
 */
export function allowedNextStatus(status: string): ShippingStatus | null {
  return LINEAR_TRANSITIONS[status] ?? null;
}

// ===========================================================================
// READ shapes
// ===========================================================================

/** Status history entry within a shipping. */
export const ShippingStatusHistorySchema = z
  .object({
    status: z.string(),
    changedAt: z.string(),
  })
  .passthrough();
export type ShippingStatusHistory = z.infer<typeof ShippingStatusHistorySchema>;

/** List row — ShippingSummary. */
export const ShippingSummarySchema = z
  .object({
    shippingId: z.string(),
    orderId: z.string(),
    // The producer's list/detail DTOs (ShippingSummary / ShippingResponse) do
    // NOT expose userId, and return null (not absent) for an unset tracking
    // number / carrier. Match that wire shape: userId optional, tracking/carrier
    // nullable — otherwise a non-empty list fails Zod parse and the section
    // degrades the moment any shipping row exists. The UI never reads userId and
    // renders carrier/trackingNumber as `?? '—'`.
    userId: z.string().optional(),
    status: z.string(),
    trackingNumber: z.string().nullable().optional(),
    carrier: z.string().nullable().optional(),
    createdAt: z.string(),
  })
  .passthrough();
export type ShippingSummary = z.infer<typeof ShippingSummarySchema>;

/** Full Shipping resource (list row + history). */
export const ShippingSchema = z
  .object({
    shippingId: z.string(),
    orderId: z.string(),
    // Producer ShippingResponse omits userId and returns null for unset
    // tracking/carrier (same wire shape as the list summary above).
    userId: z.string().optional(),
    status: z.string(),
    trackingNumber: z.string().nullable().optional(),
    carrier: z.string().nullable().optional(),
    statusHistory: z.array(ShippingStatusHistorySchema).default([]),
    createdAt: z.string(),
    updatedAt: z.string().optional(),
  })
  .passthrough();
export type Shipping = z.infer<typeof ShippingSchema>;

/** List endpoint envelope — paginated. */
export const ShippingListSchema = z
  .object({
    content: z.array(ShippingSummarySchema),
    page: z.number().int().nonnegative(),
    size: z.number().int().positive(),
    totalElements: z.number().nonnegative(),
  })
  .passthrough();
export type ShippingList = z.infer<typeof ShippingListSchema>;

// ===========================================================================
// WRITE request bodies
// ===========================================================================

/**
 * PUT /api/shippings/{id}/status body.
 * `trackingNumber` + `carrier` are REQUIRED when `status=SHIPPED`
 * (producer rejects SHIPPED without them — InvalidShipping 400).
 * The UI enforces this via ShipFormDialog; the producer is the final authority.
 */
export const UpdateShippingStatusBodySchema = z.object({
  status: z.string().min(1),
  trackingNumber: z.string().optional(),
  carrier: z.string().optional(),
});
export type UpdateShippingStatusBody = z.infer<
  typeof UpdateShippingStatusBodySchema
>;

/** POST /api/shippings/{id}/refresh-tracking body (empty body, best-effort). */
export const RefreshTrackingBodySchema = z.object({}).passthrough();
export type RefreshTrackingBody = z.infer<typeof RefreshTrackingBodySchema>;

// ===========================================================================
// List query params + pagination defaults
// ===========================================================================

export const SHIPPING_DEFAULT_PAGE_SIZE = 20;
export const SHIPPING_MAX_PAGE_SIZE = 100;

export interface ShippingListParams {
  status?: string;
  page?: number;
  size?: number;
}
