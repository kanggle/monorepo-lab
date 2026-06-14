import { z } from 'zod';

/**
 * Feature-local types for the wms `outbound-service` order-lifecycle surface
 * (the second wms surface federated by the console — TASK-PC-FE-057).
 *
 * Authoritative producer contract (do NOT redefine — consume only):
 *   `wms-platform/specs/contracts/http/outbound-service-api.md`
 *   §1.2 (GET order) / §1.3 (list orders) / §2.3 (pick confirm) /
 *   §2.4 (GET picking-requests, TASK-BE-343) / §3.1 (create packing-unit) /
 *   §3.2 (PATCH seal) / §4.1 (ship) / §5.1 (saga).
 * Consumer obligation: `console-integration-contract.md` § 2.4.5.1
 * (inherits the § 2.4.5 wms credential/tenant/envelope/resilience rules).
 *
 * These zod schemas are the runtime parsers the api-client / tests assert
 * against. They are feature-local (not cross-feature) per
 * architecture.md § Allowed Dependencies.
 *
 * TOLERANCE invariant (console-integration-contract § 2.4.5 / task Edge Case
 * "Unknown/future status/sagaState enum"): every read shape is permissive —
 * unknown / future enum values (a new order `status`, `sagaState`, …) parse
 * to a generic value and NEVER throw. Only the fields the UI strictly needs
 * are required; everything else is passthrough.
 */

// --- shared paginated page envelope (outbound-service-api.md § Pagination) -
// { content, page:{number,size,totalElements,totalPages}, sort }. Tolerant.

export const OutboundPageMetaSchema = z
  .object({
    number: z.number().int().nonnegative(),
    size: z.number().int().positive(),
    totalElements: z.number().int().nonnegative(),
    totalPages: z.number().int().nonnegative(),
  })
  .passthrough();
export type OutboundPageMeta = z.infer<typeof OutboundPageMetaSchema>;

/**
 * Normalise the producer's list envelope to `{ content, page:{…} }`.
 *
 * The contract (§ Pagination) documents `{ content, page:{number,size,
 * totalElements,totalPages}, sort }`, but the live `outbound-service`
 * `PagedResponse` DTO serialises FLAT as `{ items, page:<int>, size, total }`.
 * Accept BOTH (tolerance invariant) so the console parses the real service AND
 * the documented shape (the unit suite mocks the documented one).
 */
function normaliseOutboundPage(raw: unknown): unknown {
  if (!raw || typeof raw !== 'object') return raw;
  const r = raw as Record<string, unknown>;
  if (Array.isArray(r.content) && r.page && typeof r.page === 'object') {
    return r; // already the documented nested shape
  }
  const content = Array.isArray(r.items)
    ? r.items
    : Array.isArray(r.content)
      ? r.content
      : [];
  const size = typeof r.size === 'number' ? r.size : 20;
  const number = typeof r.page === 'number' ? r.page : 0;
  const totalElements =
    typeof r.total === 'number'
      ? r.total
      : typeof r.totalElements === 'number'
        ? r.totalElements
        : content.length;
  const totalPages = size > 0 ? Math.max(1, Math.ceil(totalElements / size)) : 1;
  return { content, page: { number, size, totalElements, totalPages } };
}

function outboundPage<T extends z.ZodTypeAny>(row: T) {
  return z.preprocess(
    normaliseOutboundPage,
    z.object({
      content: z.array(row),
      page: OutboundPageMetaSchema,
      sort: z.string().optional(),
    }),
  );
}

// --- 1.3 list — order summary row ----------------------------------------
// Status / sagaState kept as plain strings (tolerant): an unknown/future
// enum renders as a generic label, never a parser throw.

export const OutboundOrderSummarySchema = z
  .object({
    orderId: z.string(),
    orderNo: z.string().optional(),
    source: z.string().nullable().optional(),
    customerPartnerId: z.string().nullable().optional(),
    warehouseId: z.string().nullable().optional(),
    status: z.string().optional(),
    sagaState: z.string().nullable().optional(),
    lineCount: z.number().optional(),
    totalQtyOrdered: z.number().optional(),
    requiredShipDate: z.string().nullable().optional(),
    createdAt: z.string().optional(),
    updatedAt: z.string().optional(),
  })
  .passthrough();
export type OutboundOrderSummary = z.infer<typeof OutboundOrderSummarySchema>;

export const OutboundOrderPageSchema = outboundPage(OutboundOrderSummarySchema);
export type OutboundOrderPage = z.infer<typeof OutboundOrderPageSchema>;

// --- 1.2 detail — order with lines + status + version --------------------

export const OutboundOrderLineSchema = z
  .object({
    orderLineId: z.string(),
    lineNo: z.number().optional(),
    skuId: z.string(),
    lotId: z.string().nullable().optional(),
    qtyOrdered: z.number(),
  })
  .passthrough();
export type OutboundOrderLine = z.infer<typeof OutboundOrderLineSchema>;

export const OutboundOrderDetailSchema = z
  .object({
    orderId: z.string(),
    orderNo: z.string().optional(),
    source: z.string().nullable().optional(),
    customerPartnerId: z.string().nullable().optional(),
    warehouseId: z.string().nullable().optional(),
    status: z.string(),
    sagaState: z.string().nullable().optional(),
    lines: z.array(OutboundOrderLineSchema),
    // Optimistic-lock version — required on the ship body (op 8).
    version: z.number(),
    createdAt: z.string().optional(),
    updatedAt: z.string().optional(),
  })
  .passthrough();
export type OutboundOrderDetail = z.infer<typeof OutboundOrderDetailSchema>;

// --- 5.1 saga state -------------------------------------------------------

export const OutboundSagaSchema = z
  .object({
    sagaId: z.string().optional(),
    orderId: z.string().optional(),
    // `state` ∈ REQUESTED|RESERVE_FAILED|RESERVED|PICKING_CONFIRMED|
    //   PACKING_CONFIRMED|CANCELLATION_REQUESTED|CANCELLED|SHIPPED|
    //   SHIPPED_NOT_NOTIFIED|COMPLETED — kept as a string (tolerant).
    state: z.string(),
    failureReason: z.string().nullable().optional(),
    startedAt: z.string().nullable().optional(),
    lastTransitionAt: z.string().nullable().optional(),
    version: z.number().optional(),
  })
  .passthrough();
export type OutboundSaga = z.infer<typeof OutboundSagaSchema>;

// --- 2.4 picking-requests for order (TASK-BE-343) ------------------------
// Planned lines: locationId + qtyToPick feed the §2.3 confirm body
// (actualLocationId = locationId, qtyConfirmed = qtyToPick).

export const PickingRequestLineSchema = z
  .object({
    pickingRequestLineId: z.string().optional(),
    orderLineId: z.string(),
    skuId: z.string(),
    lotId: z.string().nullable().optional(),
    locationId: z.string(),
    qtyToPick: z.number(),
  })
  .passthrough();
export type PickingRequestLine = z.infer<typeof PickingRequestLineSchema>;

export const PickingRequestSchema = z
  .object({
    pickingRequestId: z.string(),
    orderId: z.string().optional(),
    sagaId: z.string().optional(),
    warehouseId: z.string().nullable().optional(),
    status: z.string().optional(),
    lines: z.array(PickingRequestLineSchema),
    version: z.number().optional(),
    createdAt: z.string().optional(),
    updatedAt: z.string().optional(),
  })
  .passthrough();
export type PickingRequest = z.infer<typeof PickingRequestSchema>;

/** §2.4 is NOT paginated — `{ content: [...] }` only (may be `[]`). */
export const PickingRequestListSchema = z.object({
  content: z.array(PickingRequestSchema),
});
export type PickingRequestList = z.infer<typeof PickingRequestListSchema>;

// --- 2.3 pick confirmation response --------------------------------------

export const PickConfirmationSchema = z
  .object({
    pickingConfirmationId: z.string().optional(),
    pickingRequestId: z.string().optional(),
    orderId: z.string().optional(),
    orderStatus: z.string().optional(),
    sagaState: z.string().optional(),
  })
  .passthrough();
export type PickConfirmation = z.infer<typeof PickConfirmationSchema>;

// --- 3.1 create packing-unit response (carries packingUnitId + version) --

export const PackingUnitSchema = z
  .object({
    packingUnitId: z.string(),
    orderId: z.string().optional(),
    cartonNo: z.string().optional(),
    packingType: z.string().optional(),
    status: z.string().optional(),
    orderStatus: z.string().optional(),
    // Optimistic-lock version — feeds the §3.2 seal body verbatim.
    version: z.number(),
  })
  .passthrough();
export type PackingUnit = z.infer<typeof PackingUnitSchema>;

// --- 4.1 ship confirmation response --------------------------------------

export const ShipmentSchema = z
  .object({
    shipmentId: z.string().optional(),
    shipmentNo: z.string().optional(),
    orderId: z.string().optional(),
    orderStatus: z.string().optional(),
    sagaState: z.string().optional(),
    tmsStatus: z.string().optional(),
  })
  .passthrough();
export type Shipment = z.infer<typeof ShipmentSchema>;

// --- 1.4 cancel response (TASK-PC-FE-085) --------------------------------
// Tolerant: `status`/`previousStatus`/`sagaState` kept as plain strings. The
// async-cancel hint reads `sagaState === 'CANCELLATION_REQUESTED'` (eventual
// `CANCELLED` once `inventory.released` is consumed).

export const CancelResultSchema = z
  .object({
    orderId: z.string().optional(),
    orderNo: z.string().optional(),
    status: z.string().optional(),
    previousStatus: z.string().optional(),
    cancelledReason: z.string().nullable().optional(),
    cancelledAt: z.string().nullable().optional(),
    cancelledBy: z.string().nullable().optional(),
    sagaState: z.string().optional(),
    version: z.number().optional(),
  })
  .passthrough();
export type CancelResult = z.infer<typeof CancelResultSchema>;

// --- 4.3 manual TMS retry response (TASK-PC-FE-087) -----------------------
// Tolerant: every field optional + passthrough. On success the producer
// returns `tmsStatus: NOTIFIED` + `sagaState: COMPLETED` (recovery); a still-
// failed retry leaves `tmsStatus: NOTIFY_FAILED` (saga stays
// `SHIPPED_NOT_NOTIFIED`) — the UI reads these to reflect the outcome.
export const TmsRetryResultSchema = z
  .object({
    shipmentId: z.string().optional(),
    tmsStatus: z.string().optional(),
    tmsNotifiedAt: z.string().nullable().optional(),
    trackingNo: z.string().nullable().optional(),
    sagaState: z.string().optional(),
    retriedAt: z.string().nullable().optional(),
    retriedBy: z.string().nullable().optional(),
  })
  .passthrough();
export type TmsRetryResult = z.infer<typeof TmsRetryResultSchema>;

// --- admin shipment-id resolver shape (TASK-PC-FE-087) -------------------
// TMS retry operates on a `shipmentId`, but the outbound order-centric reads
// carry none (§ 1.2 detail = create-response shape; no `GET /orders/{id}/
// shipments`). The id is resolved from the admin read-model
// `GET /api/v1/admin/dashboard/shipments?orderId={id}` (admin-service-api.md
// § 1.3 — same wms gateway + IAM-OIDC credential, distinct path prefix).
// Minimal + tolerant: only `shipmentId` matters; unknown fields pass through.
export const AdminShipmentRefSchema = z
  .object({ shipmentId: z.string() })
  .passthrough();
export const AdminShipmentRefPageSchema = z
  .object({ content: z.array(AdminShipmentRefSchema) })
  .passthrough();
export type AdminShipmentRefPage = z.infer<typeof AdminShipmentRefPageSchema>;

// --- query params + pagination defaults ----------------------------------

export const OUTBOUND_DEFAULT_PAGE_SIZE = 20;
export const OUTBOUND_MAX_PAGE_SIZE = 100;

export interface OutboundListParams {
  status?: string;
  warehouseId?: string;
  orderNo?: string;
  page?: number;
  size?: number;
}

// --- action gating helpers (UI mirror of the producer state machine) -----
// The console mirrors the producer state machine for UX only — the producer
// is the final authority (a server 422 STATE_TRANSITION_INVALID is still
// handled inline). Tolerant: unknown enums simply gate the action off.

/** Pick is reachable only when order `PICKING` AND saga `RESERVED`. */
export function canPick(status: string | undefined, saga: string | null | undefined): boolean {
  return status === 'PICKING' && saga === 'RESERVED';
}

/** Pack is reachable only when order `PICKED` or `PACKING`. */
export function canPack(status: string | undefined): boolean {
  return status === 'PICKED' || status === 'PACKING';
}

/** Ship is reachable only when order `PACKED`. */
export function canShip(status: string | undefined): boolean {
  return status === 'PACKED';
}

/** Cancel is reachable for any non-terminal lifecycle status
 *  (`PICKING|PICKED|PACKING|PACKED`). `SHIPPED`/`CANCELLED`/`BACKORDERED` are
 *  NOT cancellable here (producer returns 422 on SHIPPED — still handled
 *  inline). Tolerant: an unknown status gates the action off. */
const CANCELLABLE_STATES = new Set(['PICKING', 'PICKED', 'PACKING', 'PACKED']);
export function canCancel(status: string | undefined): boolean {
  return status !== undefined && CANCELLABLE_STATES.has(status);
}

/** Post-pick statuses whose cancel the producer gates behind `OUTBOUND_ADMIN`
 *  (`PICKED|PACKING|PACKED`). The console does NOT pre-gate on role — it shows
 *  a pre-emptive hint and maps a 403 inline. `PICKING` cancel needs only
 *  `OUTBOUND_WRITE`. */
const POST_PICK_STATES = new Set(['PICKED', 'PACKING', 'PACKED']);
export function cancelNeedsAdmin(status: string | undefined): boolean {
  return status !== undefined && POST_PICK_STATES.has(status);
}

/** Manual TMS retry (TASK-PC-FE-087) is reachable ONLY for a SHIPPED order
 *  whose saga is `SHIPPED_NOT_NOTIFIED` (the producer allows § 4.3 only when
 *  `Shipment.tmsStatus == NOTIFY_FAILED`; the saga state is the order-level
 *  read signal — the admin `ShipmentSummary` read-model does not project
 *  `tmsStatus`). A healthy SHIPPED order (saga `COMPLETED`) and any non-SHIPPED
 *  status gate the action off. Producer is the final authority (a 422
 *  STATE_TRANSITION_INVALID is still handled inline). */
export function canRetryTms(
  status: string | undefined,
  saga: string | null | undefined,
): boolean {
  return status === 'SHIPPED' && saga === 'SHIPPED_NOT_NOTIFIED';
}
