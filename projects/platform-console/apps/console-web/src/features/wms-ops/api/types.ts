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
    // Nullable: the admin read model leaves the denormalized master codes
    // NULL when the corresponding master ref (admin_{location,sku}_ref) has not
    // projected yet — e.g. a freshly received/reserved SKU before its master
    // event lands. The table already falls back to the id (`code ?? id`), so
    // null must PARSE (not throw → whole-section degrade). Mirrors `lotNo`
    // and every shipment field. (TASK-PC-FE-185)
    locationCode: z.string().nullable().optional(),
    skuCode: z.string().nullable().optional(),
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
// AsnSummary / InspectionSummary (admin-service domain-model.md § 6/§ 7) —
// projected from `wms.inbound.*` events. TASK-PC-FE-222 surfaces the
// previously-uncoded `GET /dashboard/asns` + `.../inspection` reads on the
// dedicated `/wms/inbound` screen. Typed (vs. the prior GenericRowSchema)
// so the ASN table + inspection panel can render named fields, but TOLERANT
// (§ 2.4.5 tolerance invariant): only `asnId` is required on a row; every
// other field is nullable/optional and unknown/future fields pass through
// (never throws — the Failure Scenario this task guards against).

export const AsnRowSchema = z
  .object({
    asnId: z.string(),
    asnNo: z.string().nullable().optional(),
    warehouseId: z.string().optional(),
    supplierPartnerId: z.string().nullable().optional(),
    supplierName: z.string().nullable().optional(),
    status: z.string().optional(),
    source: z.string().nullable().optional(),
    expectedArriveDate: z.string().nullable().optional(),
    lineCount: z.number().nullable().optional(),
    receivedAt: z.string().nullable().optional(),
    closedAt: z.string().nullable().optional(),
  })
  .passthrough();
export type AsnRow = z.infer<typeof AsnRowSchema>;
export const AsnPageSchema = wmsPage(AsnRowSchema);
export type AsnPage = z.infer<typeof AsnPageSchema>;

/** `GET /dashboard/asns/{asnId}/inspection` — the InspectionSummary (1:1 per
 *  ASN, aggregate line-count/qty totals, not a per-line breakdown — the
 *  admin read-model projects only the summary). `404` (not yet projected) is
 *  handled by the caller as "검수 내역 없음", never parsed here. */
export const InspectionSchema = z
  .object({
    asnId: z.string().optional(),
    warehouseId: z.string().nullable().optional(),
    inspectionCompletedAt: z.string().nullable().optional(),
    inspectorId: z.string().nullable().optional(),
    totalLines: z.number().nullable().optional(),
    discrepancyCount: z.number().nullable().optional(),
    totalQtyExpected: z.number().nullable().optional(),
    totalQtyPassed: z.number().nullable().optional(),
    totalQtyDamaged: z.number().nullable().optional(),
    totalQtyShort: z.number().nullable().optional(),
  })
  .passthrough();
export type Inspection = z.infer<typeof InspectionSchema>;

// --- 1.5 adjustments audit (append-only — read only) ---------------------
// AdjustmentAuditResponse (admin-service § dashboard/adjustments) — projected
// from `inventory.adjusted`. Typed (vs. generic) so the 개요 "최근 재고 조정"
// glance (PC-FE-186) can render bucket/delta/reason, but TOLERANT (§ 2.4.5):
// every field is optional/nullable and unknown/future fields pass through.
export const AdjustmentRowSchema = z
  .object({
    id: z.string().optional(),
    skuId: z.string().nullable().optional(),
    locationId: z.string().nullable().optional(),
    warehouseId: z.string().nullable().optional(),
    bucket: z.string().nullable().optional(),
    delta: z.number().nullable().optional(),
    reasonCode: z.string().nullable().optional(),
    reasonNote: z.string().nullable().optional(),
    occurredAt: z.string().nullable().optional(),
  })
  .passthrough();
export type AdjustmentRow = z.infer<typeof AdjustmentRowSchema>;
export const AdjustmentPageSchema = wmsPage(AdjustmentRowSchema);
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

/** TASK-PC-FE-223 — the `/wms/master` screen's seeded tab (server-side
 *  first-page seed, mirrors `inbound-state.ts`'s single-read seed). `locations`
 *  is the most operationally-consulted ref during putaway/pick lookups. */
export const DEFAULT_REF_TYPE: RefType = 'locations';

// --- 5. settings ------------------------------------------------------------
// Setting (admin-service-api.md § 5.1/5.2) — TASK-PC-FE-224 surfaces the
// previously-unconsumed settings read on the dedicated `/wms/operations`
// screen (예약 TTL `inventory.reservation.ttl_hours` + 저재고 기본 임계치
// `inventory.low_stock.default_threshold_qty`). TOLERANT (§ 2.4.5 tolerance
// invariant): only `key` is required; `valueJson`/`schemaJson` are `unknown`
// (the schema shape is per-key, not fixed) and every other field is
// optional/nullable so an unrecognised or future settings key still parses
// (never throws — the screen filters down to the KNOWN operational keys it
// renders; task Edge Case "설정 키 미제공 → 해당 행 생략").
export const SettingSchema = z
  .object({
    key: z.string(),
    scope: z.string().optional(),
    warehouseId: z.string().nullable().optional(),
    valueJson: z.unknown().optional(),
    schemaJson: z.unknown().optional(),
    description: z.string().nullable().optional(),
    version: z.number().optional(),
    updatedAt: z.string().nullable().optional(),
    updatedBy: z.string().nullable().optional(),
  })
  .passthrough();
export type Setting = z.infer<typeof SettingSchema>;
export const SettingPageSchema = wmsPage(SettingSchema);
export type SettingPage = z.infer<typeof SettingPageSchema>;

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
  /** ISO instant lower bound on `shippedAt` (inclusive) — the wms
   *  `admin-service-api.md` § 1.3 `shippedAtFrom` window. Used by the overview
   *  배송 period-to-date metrics (PC-FE-174). */
  shippedAtFrom?: string;
  /** ISO instant upper bound on `shippedAt` — `shippedAtTo` (§ 1.3). */
  shippedAtTo?: string;
  page?: number;
  size?: number;
}

/** `GET /dashboard/asns` query params (admin-service-api.md § 1.4 — TASK-PC-FE-222
 *  dedicated `/wms/inbound` screen). `source` is a producer filter (`MANUAL` /
 *  `WEBHOOK_ERP`) not surfaced as a form field in this task's scope. */
export interface AsnQueryParams {
  warehouseId?: string;
  supplierPartnerId?: string;
  status?: string;
  /** ISO local-date lower bound on `expectedArriveDate` (inclusive). */
  expectedArriveDateFrom?: string;
  /** ISO local-date upper bound on `expectedArriveDate` (inclusive). */
  expectedArriveDateTo?: string;
  page?: number;
  size?: number;
}

/** `GET /dashboard/refs/{type}` query params (admin-service-api.md § 1.7 —
 *  TASK-PC-FE-223 dedicated `/wms/master` screen). § 1.7 states "Query
 *  parameters vary by `{type}`" WITHOUT enumerating names — this feature
 *  adopts the same `q` (substring, mirrors § 2.2 `GET /users`) + `status`
 *  filter convention already used elsewhere on this producer's list
 *  endpoints, forwarded tolerantly (an unrecognised param is expected to be
 *  ignored server-side, not rejected — ordinary Spring Data list-endpoint
 *  behaviour). Both are OPTIONAL and per Edge Case, an empty `q` is never
 *  sent (no `q=` on the wire). */
export interface RefQueryParams {
  q?: string;
  status?: string;
  page?: number;
  size?: number;
}

/** `GET /settings` query params (admin-service-api.md § 5.1 — TASK-PC-FE-224
 *  dedicated `/wms/operations` screen). All optional filters; the operations
 *  screen fetches the unfiltered page-0 list and filters client-side to the
 *  KNOWN operational keys it renders. */
export interface SettingQueryParams {
  keyPrefix?: string;
  scope?: string;
  warehouseId?: string;
  page?: number;
  size?: number;
}
