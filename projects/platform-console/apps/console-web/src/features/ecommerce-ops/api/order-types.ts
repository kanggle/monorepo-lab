import { z } from 'zod';
import type { StatusTone } from '@/shared/ui/StatusBadge';

/**
 * Feature-local types for the ecommerce `order-service` operator surface —
 * the orders facet of the ecommerce write binding federated by the console
 * (TASK-PC-FE-083, ADR-MONO-031 Phase 1b). Drives the in-console equivalent
 * of the standalone `admin-dashboard` order screens: list / detail / status
 * change.
 *
 * Authoritative producer contract (do NOT redefine — consume only):
 *   ecommerce `order-service`
 *   `AdminOrderController` (`/api/admin/orders/**`, BE-366 operator-plane)
 * Consumer obligation: `console-integration-contract.md` § 2.4.10
 * (#15–17 order endpoints; inherits the non-IAM domain credential/tenant/
 * envelope/resilience rules).
 *
 * TOLERANCE invariant: read shapes are permissive (`.passthrough()`); only the
 * fields the UI strictly needs are required, everything else passes through.
 * An unknown / future `status` enum parses to a generic string and NEVER throws.
 *
 * State machine — OPERATOR-INITIATED transitions only (Order.java + ADR-MONO-022 §D7):
 *   PENDING → {CONFIRMED, CANCELLED}
 *   CONFIRMED → {CANCELLED}
 *   SHIPPED, DELIVERED, CANCELLED, STUCK_RECOVERY_FAILED → (no operator action)
 *
 * SHIPPED and DELIVERED are reached SOLELY via the shipping-driven return-leg
 * (the `ShippingStatusChanged` event flips the Order CONFIRMED→SHIPPED→DELIVERED),
 * NOT by operator action — the order detail displays them read-only. The producer
 * rejects an operator `status: SHIPPED|DELIVERED` with 400 INVALID_ORDER_REQUEST.
 */

// ===========================================================================
// ORDER STATUS
// ===========================================================================

export const ORDER_STATUS_VALUES = [
  'PENDING',
  'CONFIRMED',
  'SHIPPED',
  'DELIVERED',
  'CANCELLED',
  'STUCK_RECOVERY_FAILED',
] as const;
export type OrderStatus = (typeof ORDER_STATUS_VALUES)[number];

/**
 * Order status → shared semantic {@link StatusTone} (rendered via the shared
 * `<StatusBadge>` — TASK-PC-FE-158/159). PENDING awaits operator action
 * (warning); CONFIRMED/SHIPPED are mid-lifecycle (progress); DELIVERED is the
 * happy terminal (success); CANCELLED and the stuck-recovery failure are
 * terminal-bad (danger). An unknown/future status → `neutral` (TOLERANCE — the
 * producer may add a status the console has not shipped yet).
 */
const ORDER_STATUS_TONE: Record<OrderStatus, StatusTone> = {
  PENDING: 'warning',
  CONFIRMED: 'progress',
  SHIPPED: 'progress',
  DELIVERED: 'success',
  CANCELLED: 'danger',
  STUCK_RECOVERY_FAILED: 'danger',
};

export function orderStatusTone(status: string): StatusTone {
  return ORDER_STATUS_TONE[status as OrderStatus] ?? 'neutral';
}

/**
 * Allowed operator-triggered transitions from a given status. SHIPPED/DELIVERED
 * are intentionally absent — they are driven by the shipping return-leg
 * (`ShippingStatusChanged`), not operator action, so the order detail shows them
 * read-only.
 */
const TRANSITIONS: Record<string, OrderStatus[]> = {
  PENDING: ['CONFIRMED', 'CANCELLED'],
  CONFIRMED: ['CANCELLED'],
  SHIPPED: [],
  DELIVERED: [],
  CANCELLED: [],
  STUCK_RECOVERY_FAILED: [],
};

/**
 * Returns the allowed target statuses from the given `status`. Unknown / future
 * statuses return an empty array (no operator action — fail-safe).
 */
export function allowedTransitions(status: string): OrderStatus[] {
  return TRANSITIONS[status] ?? [];
}

// ===========================================================================
// READ shapes
// ===========================================================================

/** #15 list — AdminOrderListResponse.OrderSummaryItem row. */
export const OrderSummarySchema = z
  .object({
    orderId: z.string(),
    userId: z.string(),
    status: z.string(),
    totalPrice: z.number(),
    itemCount: z.number().int(),
    firstItemName: z.string(),
    createdAt: z.string(),
  })
  .passthrough();
export type OrderSummary = z.infer<typeof OrderSummarySchema>;

/** #15 list — AdminOrderListResponse envelope. */
export const OrderListSchema = z
  .object({
    content: z.array(OrderSummarySchema),
    page: z.number().int().nonnegative(),
    size: z.number().int().positive(),
    totalElements: z.number().nonnegative(),
  })
  .passthrough();
export type OrderList = z.infer<typeof OrderListSchema>;

/**
 * #16 detail — order item line.
 *
 * TOLERANCE (header invariant): the producer serializes genuinely-nullable
 * domain fields as JSON `null` (Jackson default — no `NON_NULL` inclusion).
 * `OrderItem.variantId` / `optionName` are nullable on the aggregate (no
 * placement-time normalization), so they must accept `null` here — otherwise a
 * plain `z.string()` throws on `null`, and that parse failure is caught
 * downstream and mis-surfaced as a fake 503 "일시적으로 불러올 수 없습니다"
 * degrade (ecommerce-client rethrows any non-ApiError as EcommerceUnavailable).
 */
export const OrderItemSchema = z
  .object({
    productId: z.string(),
    variantId: z.string().nullable().optional(),
    productName: z.string(),
    optionName: z.string().nullable().optional(),
    quantity: z.number().int(),
    unitPrice: z.number(),
    sellerId: z.string(),
  })
  .passthrough();
export type OrderItem = z.infer<typeof OrderItemSchema>;

/**
 * #16 detail — shipping address.
 *
 * `address2` is nullable on the aggregate (the optional detailed-address line,
 * also cleared to `null` on GDPR anonymization — ADR-MONO-037 P3-B). The
 * producer serializes it as JSON `null` when empty, so the schema must accept
 * `null` (not just `undefined`). This was THE root cause of the order-detail
 * page degrading for essentially every order without a second address line —
 * `z.string().optional()` rejects `null`, and the parse throw was mis-surfaced
 * as a fake "일시적으로 불러올 수 없습니다" degrade.
 */
export const ShippingAddressSchema = z
  .object({
    recipient: z.string(),
    phone: z.string(),
    zipCode: z.string(),
    address1: z.string(),
    address2: z.string().nullable().optional(),
  })
  .passthrough();
export type ShippingAddress = z.infer<typeof ShippingAddressSchema>;

/** #16 detail — AdminOrderDetailResponse. */
export const OrderDetailSchema = z
  .object({
    orderId: z.string(),
    userId: z.string(),
    status: z.string(),
    totalPrice: z.number(),
    items: z.array(OrderItemSchema).default([]),
    shippingAddress: ShippingAddressSchema,
    createdAt: z.string(),
    updatedAt: z.string().nullable().optional(),
  })
  .passthrough();
export type OrderDetail = z.infer<typeof OrderDetailSchema>;

/** #17 status change response — { orderId, status }. */
export const OrderStatusChangeResponseSchema = z
  .object({
    orderId: z.string(),
    status: z.string(),
  })
  .passthrough();
export type OrderStatusChangeResponse = z.infer<
  typeof OrderStatusChangeResponseSchema
>;

// ===========================================================================
// WRITE request bodies
// ===========================================================================

/** #17 — AdminOrderStatusChangeRequest body: { status: string }. */
export const OrderStatusChangeBodySchema = z.object({
  status: z.string().min(1),
});
export type OrderStatusChangeBody = z.infer<typeof OrderStatusChangeBodySchema>;

// ===========================================================================
// List query params + pagination defaults
// ===========================================================================

export const ORDER_DEFAULT_PAGE_SIZE = 20;
export const ORDER_MAX_PAGE_SIZE = 100;

export interface OrderListParams {
  status?: string;
  page?: number;
  size?: number;
}
