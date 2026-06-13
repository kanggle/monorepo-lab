import { z } from 'zod';

/**
 * Feature-local types for the wms `admin-service` dashboard read-model
 * surface + the single alert-ack mutation.
 *
 * Authoritative producer contract (do NOT redefine — consume only):
 *   `wms-platform/specs/contracts/http/admin-service-api.md`
 *   § 1 Dashboard / Read-Model (12 reads) + `POST .../alerts/{alertId}/
 *   acknowledge` + § 6.2 projection-status.
 * Consumer obligation: `console-integration-contract.md` § 2.4.5.
 *
 * These zod schemas are the runtime parsers the api-client / tests assert
 * against. They are feature-local (not cross-feature) per
 * architecture.md § Allowed Dependencies.
 *
 * TOLERANCE invariant (console-integration-contract § 2.4.5 / task Edge
 * Case "Unknown/future enum"): every read shape is permissive — unknown /
 * future enum values (a new `alertType`, `bucket`, ref `{type}`, …) parse
 * to a generic row and NEVER throw. Only the fields the UI strictly needs
 * are required; everything else is passthrough.
 */

// --- shared page envelope -------------------------------------------------
// admin-service-api.md § Pagination: { content, page:{number,size,
// totalElements,totalPages}, sort }. Tolerant: unknown extra fields kept.

export const WmsPageMetaSchema = z
  .object({
    number: z.number().int().nonnegative(),
    size: z.number().int().positive(),
    totalElements: z.number().int().nonnegative(),
    totalPages: z.number().int().nonnegative(),
  })
  .passthrough();
export type WmsPageMeta = z.infer<typeof WmsPageMetaSchema>;

function wmsPage<T extends z.ZodTypeAny>(row: T) {
  return z.object({
    content: z.array(row),
    page: WmsPageMetaSchema,
    sort: z.string().optional(),
  });
}

// --- 1.1 inventory snapshot ----------------------------------------------

export const InventoryRowSchema = z
  .object({
    locationId: z.string(),
    skuId: z.string(),
    lotId: z.string().nullable().optional(),
    warehouseId: z.string(),
    locationCode: z.string().optional(),
    skuCode: z.string().optional(),
    lotNo: z.string().nullable().optional(),
    availableQty: z.number().optional(),
    reservedQty: z.number().optional(),
    damagedQty: z.number().optional(),
    onHandQty: z.number().optional(),
    lowStockFlag: z.boolean().optional(),
    lastAdjustedAt: z.string().optional(),
    lastEventAt: z.string().optional(),
    version: z.number().optional(),
  })
  .passthrough();
export type InventoryRow = z.infer<typeof InventoryRowSchema>;
export const InventoryPageSchema = wmsPage(InventoryRowSchema);
export type InventoryPage = z.infer<typeof InventoryPageSchema>;

// --- 1.2 throughput -------------------------------------------------------

export const ThroughputSchema = z
  .object({
    warehouseId: z.string(),
    from: z.string(),
    to: z.string(),
    days: z.array(
      z
        .object({
          date: z.string(),
          inbound: z.record(z.string(), z.number()).optional(),
          outbound: z.record(z.string(), z.number()).optional(),
        })
        .passthrough(),
    ),
    totals: z
      .object({
        inbound: z.record(z.string(), z.number()).optional(),
        outbound: z.record(z.string(), z.number()).optional(),
      })
      .passthrough()
      .optional(),
  })
  .passthrough();
export type Throughput = z.infer<typeof ThroughputSchema>;

// --- 1.3 orders / shipments ----------------------------------------------
// OrderSummary / ShipmentSummary live in wms domain-model.md; the console
// renders them as a federated read view (not a wms model owner).

export const GenericRowSchema = z.record(z.string(), z.unknown());
export type GenericRow = z.infer<typeof GenericRowSchema>;

export const OrderPageSchema = wmsPage(GenericRowSchema);
export type OrderPage = z.infer<typeof OrderPageSchema>;

// ShipmentSummary (admin-service domain-model.md § 9) — projected from
// `outbound.shipping.confirmed`. Typed (vs. generic) so the UI can render the
// carrier fields the operator needs, but TOLERANT (§ 2.4.5 tolerance
// invariant): only `shipmentId` is required; `carrierCode`/`trackingNo` are
// NULLABLE (a shipment confirmed before a carrier is assigned); every other
// field is optional and unknown/future fields pass through (never throws).
export const ShipmentRowSchema = z
  .object({
    shipmentId: z.string(),
    orderId: z.string().optional(),
    orderNo: z.string().nullable().optional(),
    warehouseId: z.string().optional(),
    shipmentNo: z.string().nullable().optional(),
    carrierCode: z.string().nullable().optional(),
    trackingNo: z.string().nullable().optional(),
    shippedAt: z.string().nullable().optional(),
    totalQty: z.number().nullable().optional(),
  })
  .passthrough();
export type ShipmentRow = z.infer<typeof ShipmentRowSchema>;
export const ShipmentPageSchema = wmsPage(ShipmentRowSchema);
export type ShipmentPage = z.infer<typeof ShipmentPageSchema>;

// --- 1.4 asns + inspection -----------------------------------------------

export const AsnPageSchema = wmsPage(GenericRowSchema);
export type AsnPage = z.infer<typeof AsnPageSchema>;

export const InspectionSchema = GenericRowSchema;
export type Inspection = z.infer<typeof InspectionSchema>;

// --- 1.5 adjustments audit (append-only — read only) ---------------------

export const AdjustmentPageSchema = wmsPage(GenericRowSchema);
export type AdjustmentPage = z.infer<typeof AdjustmentPageSchema>;

// --- 1.6 alerts -----------------------------------------------------------

export const AlertRowSchema = z
  .object({
    alertId: z.string(),
    alertType: z.string().optional(),
    warehouseId: z.string().nullable().optional(),
    message: z.string().nullable().optional(),
    detectedAt: z.string().optional(),
    acknowledged: z.boolean().optional(),
    acknowledgedAt: z.string().nullable().optional(),
    acknowledgedBy: z.string().nullable().optional(),
  })
  .passthrough();
export type AlertRow = z.infer<typeof AlertRowSchema>;
export const AlertPageSchema = wmsPage(AlertRowSchema);
export type AlertPage = z.infer<typeof AlertPageSchema>;

/** `POST .../alerts/{alertId}/acknowledge` → the updated AlertLog row. */
export const AckResultSchema = AlertRowSchema;
export type AckResult = z.infer<typeof AckResultSchema>;

// --- 1.7 master refs ------------------------------------------------------

export const RefPageSchema = wmsPage(GenericRowSchema);
export type RefPage = z.infer<typeof RefPageSchema>;

/** `{type}` ∈ warehouses|zones|locations|skus|lots|partners. Tolerant —
 *  an unknown `{type}` is still passed through (producer is the authority;
 *  the console never hard-codes the closed set as a gate). */
export const REF_TYPES = [
  'warehouses',
  'zones',
  'locations',
  'skus',
  'lots',
  'partners',
] as const;
export type RefType = (typeof REF_TYPES)[number];

// --- 6.2 projection status ------------------------------------------------

export const ProjectionStatusSchema = z
  .object({
    projections: z.array(
      z
        .object({
          topic: z.string(),
          consumerGroup: z.string().optional(),
          lagSeconds: z.number().optional(),
          lastEventAt: z.string().nullable().optional(),
          lastProjectedAt: z.string().nullable().optional(),
          lifetimeApplied: z.number().optional(),
          lifetimeIgnoredDuplicate: z.number().optional(),
          lifetimeFailed: z.number().optional(),
        })
        .passthrough(),
    ),
    worstLagSeconds: z.number().optional(),
  })
  .passthrough();
export type ProjectionStatus = z.infer<typeof ProjectionStatusSchema>;

// --- query params ---------------------------------------------------------

export const WMS_DEFAULT_PAGE_SIZE = 20;
export const WMS_MAX_PAGE_SIZE = 100;

export interface InventoryQueryParams {
  warehouseId?: string;
  locationId?: string;
  skuId?: string;
  lotId?: string;
  lowStockOnly?: boolean;
  minOnHand?: number;
  page?: number;
  size?: number;
}

export interface AlertQueryParams {
  alertType?: string;
  warehouseId?: string;
  acknowledged?: boolean;
  page?: number;
  size?: number;
}

export interface ShipmentQueryParams {
  warehouseId?: string;
  carrierCode?: string;
  page?: number;
  size?: number;
}
