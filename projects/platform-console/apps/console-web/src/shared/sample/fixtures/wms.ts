import { fixtureNotFound } from '../router';

/**
 * Same shape as `fixtures/index.ts`'s `FixtureHandler` — written out here
 * (rather than imported) so this module has no edge back to `index.ts`, which
 * imports THIS module (`shared/sample/**` must stay a DAG the isolation guard
 * can read — see `iam.ts`/`ecommerce.ts`/`erp.ts`/`finance.ts`'s identical
 * note).
 *
 * 🔴 **This module must not import `./dashboards`, `./registry` or
 * `./index`** — `dashboards.ts` reads this module's handlers EAGERLY at
 * module load (AC-8, below) to derive the operator-overview wms card, so an
 * edge from THIS file back into that cycle reproduces the exact circular-init
 * crash `TASK-PC-FE-286` D3 found and fixed for finance (`registry.ts` had
 * imported `finance.ts`). Only `dashboards.ts` and the barrel
 * (`fixtures/index.ts`) are allowed to import `wms.ts`.
 */
type WmsFixtureHandler = (path: string) => unknown;

/**
 * wms domain fixtures (TASK-PC-FE-287 — `ADR-MONO-074` execution 6/8): the
 * THREE `callWmsGateway` surfaces —
 *   - `wms`                    — admin-service read-model (11+ GETs, § 1/§ 6.2
 *     of `admin-service-api.md`)
 *   - `wms_outbound`           — outbound-service order lifecycle (§ outbound-
 *     service-api.md § 1-2-5) — also answers ONE admin-shaped path (see
 *     `ADMIN_SHIPMENTS_PATH` below, the Edge Case this ticket names)
 *   - `wms_outbound_logistics` — carrier dispatch, reached via
 *     `callScmGateway` → `callFlatEnvelopeGateway`, i.e. **core `flat`, NOT
 *     `wms`** (see `coverage.ts`'s TASK-PC-FE-287 fix — the row this ticket
 *     found mis-classified `core: 'wms'` since TASK-PC-FE-282, which meant
 *     this surface could never actually become `ready`: the real call site
 *     (`outbound-logistics-api.ts`'s `LOGISTICS_PROFILE`) asks
 *     `sampleGate({core:'flat', …})`, so a `core:'wms'` ledger row NEVER
 *     matches it — `findSurfaceCoverage` always returns `undefined` and the
 *     router always falls through to `503 SAMPLE_NOT_READY` regardless of the
 *     row's `status`. It went unnoticed while `pending`, because `pending`
 *     and "never matches" both produce the identical 503.).
 *
 * Hand-authored synthetic data (ADR-MONO-074 A4 — no extraction path from any
 * backend). Parsed by the SAME zod schemas the real screens use
 * (`tests/unit/sample-fixtures-schema-wms.test.ts`).
 *
 * ── ONE WORLD, THREE READ MODELS (cross-view invariants) ──────────────────
 * The admin read-model (`wms`), the outbound-service order lifecycle
 * (`wms_outbound`) and the logistics dispatch (`wms_outbound_logistics`) are
 * three DIFFERENT producers projecting facts about the SAME four orders. To
 * avoid the TASK-PC-FE-284/285 defect class (two screens telling a visitor
 * two different numbers about the same fact), the admin-side rows below are
 * DERIVED from the outbound-side rows (never hand-duplicated):
 *   - `ADMIN_ORDERS` collapses each `OUTBOUND_ORDERS` row's real status
 *     (`PICKING`/`PICKED`/`PACKING`/`PACKED`/`SHIPPED`/`CANCELLED`) to the
 *     admin read-model's own coarser enum (`RECEIVED`/`SHIPPED`/`CANCELLED`
 *     — `overview-state.ts`'s own comment: "the read model collapses order
 *     status … (picking/packing events are swallowed)").
 *   - `SAMPLE_WMS_SHIPMENTS` (admin `dashboard/shipments`) exists ONLY for
 *     the one `SHIPPED` order, with `orderId`/`orderNo`/`totalQty` read off
 *     that SAME order's lines — never a second, independent number.
 *   - `SAMPLE_WMS_DISPATCH` (logistics) references that SAME shipment's id.
 * `tests/unit/sample-fixtures-schema-wms.test.ts` asks the ROUTER for both
 * sides (never the arrays below directly) and asserts they agree.
 *
 * ── AC-3 / TASK-MONO-675 — codes resolve, never null ──────────────────────
 * `InventoryRowSchema`'s `locationCode`/`skuCode`/`lotNo`/`warehouseCode` are
 * `.nullable()` because the REAL admin-service leaves them null when the
 * denormalizing master-ref projection has not landed yet (`TASK-MONO-675` —
 * a live defect, not a console bug: `MASTER_REF_UNRESOLVED` ("이름 확인 불가")
 * is the CORRECT rendering of that state). A synthetic sample world has no
 * such race — every inventory/ASN/order-line row below is built by reading
 * the code straight off its `WAREHOUSES`/`LOCATIONS`/`SKUS`/`LOTS`/`PARTNERS`
 * ref row (never a duplicated literal), so `TASK-MONO-675`'s null is neither
 * hidden (the code really is present) nor reproduced (the sample never sits
 * mid-projection-lag).
 *
 * R2ⓐ: human-readable strings (`name`, `customerName`, `supplierName`,
 * `reasonNote`, `message`) end with «(샘플)»; ids, codes, enums, dates,
 * quantities do not (`label-rule.ts`'s TASK-PC-FE-287 section).
 *
 * Person-identifying values (AC-3 in `label-rule.ts`'s sense / the task's
 * "Person-identifying values obviously synthetic" instruction): this domain
 * has no operator/customer PERSON names on any read surface (partners and
 * customers are companies — `PartnerRef.name`/`OrderSummary.customerName`
 * are company names, already synthetic + suffixed) — nothing to launder.
 */

// ---------------------------------------------------------------------------
// path helpers
// ---------------------------------------------------------------------------

const ADMIN = '/api/v1/admin';
const OUTBOUND = '/api/v1/outbound';
const LOGISTICS = '/api/v1/logistics';

function splitPath(path: string): { pathname: string; query: URLSearchParams } {
  const [pathname, qs = ''] = path.split('?');
  return { pathname, query: new URLSearchParams(qs) };
}

function intParam(query: URLSearchParams, key: string, fallback: number): number {
  const raw = query.get(key);
  if (raw === null) return fallback;
  const n = Number(raw);
  return Number.isFinite(n) ? n : fallback;
}

interface PageMeta {
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

function paginate<T>(
  rows: readonly T[],
  query: URLSearchParams,
  sort: string,
): { content: T[]; page: PageMeta; sort: string } {
  const page = intParam(query, 'page', 0);
  const size = Math.max(1, intParam(query, 'size', 20));
  const start = page * size;
  return {
    content: rows.slice(start, start + size),
    page: {
      number: page,
      size,
      totalElements: rows.length,
      totalPages: Math.max(1, Math.ceil(rows.length / size)),
    },
    sort,
  };
}

const T_EARLY = '2026-09-10T02:00:00Z';
const T_MID = '2026-09-12T05:00:00Z';
const T_LATE = '2026-09-14T09:00:00Z';

// ---------------------------------------------------------------------------
// § 1.7 master ref tables — warehouses · zones · locations · skus · lots ·
// partners (`admin-service-api.md` § 1.7, `domain-model.md` § 5)
// ---------------------------------------------------------------------------

const WAREHOUSE = {
  id: 'wh-sample-0001',
  warehouseCode: 'WH01',
  name: '강서 물류센터 (샘플)',
  timezone: 'Asia/Seoul',
  status: 'ACTIVE',
  lastEventAt: T_EARLY,
  version: 1,
} as const;

const ZONES = [
  {
    id: 'zone-sample-0001',
    warehouseId: WAREHOUSE.id,
    zoneCode: 'Z-A',
    name: 'A동 상온 구역 (샘플)',
    zoneType: 'STORAGE',
    status: 'ACTIVE',
    lastEventAt: T_EARLY,
    version: 1,
  },
  {
    id: 'zone-sample-0002',
    warehouseId: WAREHOUSE.id,
    zoneCode: 'Z-B',
    name: 'B동 냉장 구역 (샘플)',
    zoneType: 'STORAGE',
    status: 'ACTIVE',
    lastEventAt: T_EARLY,
    version: 1,
  },
] as const;

const LOCATIONS = [
  {
    id: 'loc-sample-0001',
    locationCode: 'WH01-A-01-01-01',
    warehouseId: WAREHOUSE.id,
    zoneId: ZONES[0].id,
    locationType: 'STORAGE',
    status: 'ACTIVE',
    lastEventAt: T_EARLY,
    version: 1,
  },
  {
    id: 'loc-sample-0002',
    locationCode: 'WH01-A-01-01-02',
    warehouseId: WAREHOUSE.id,
    zoneId: ZONES[0].id,
    locationType: 'STORAGE',
    status: 'ACTIVE',
    lastEventAt: T_EARLY,
    version: 1,
  },
  {
    id: 'loc-sample-0003',
    locationCode: 'WH01-B-01-01-01',
    warehouseId: WAREHOUSE.id,
    zoneId: ZONES[1].id,
    locationType: 'STORAGE',
    status: 'ACTIVE',
    lastEventAt: T_EARLY,
    version: 1,
  },
] as const;

const SKUS = [
  {
    id: 'sku-sample-0001',
    skuCode: 'SKU-APPLE-001',
    name: '사과 한 박스 (샘플)',
    baseUom: 'BOX',
    trackingType: 'LOT',
    status: 'ACTIVE',
    lastEventAt: T_EARLY,
    version: 1,
  },
  {
    id: 'sku-sample-0002',
    skuCode: 'SKU-BANANA-002',
    name: '바나나 한 다발 (샘플)',
    baseUom: 'EA',
    trackingType: 'NONE',
    status: 'ACTIVE',
    lastEventAt: T_EARLY,
    version: 1,
  },
  {
    id: 'sku-sample-0003',
    skuCode: 'SKU-WATER-003',
    name: '생수 24 캔팩 (샘플)',
    baseUom: 'PACK',
    trackingType: 'LOT',
    status: 'ACTIVE',
    lastEventAt: T_EARLY,
    version: 1,
  },
] as const;

const LOTS = [
  {
    id: 'lot-sample-0001',
    skuId: SKUS[0].id,
    lotNo: 'L-20260501-A',
    expiryDate: '2026-11-01',
    status: 'ACTIVE',
    lastEventAt: T_EARLY,
    version: 1,
  },
  {
    id: 'lot-sample-0002',
    skuId: SKUS[2].id,
    lotNo: 'L-20260601-B',
    expiryDate: '2027-01-01',
    status: 'ACTIVE',
    lastEventAt: T_EARLY,
    version: 1,
  },
] as const;

const PARTNERS = [
  {
    id: 'partner-sample-0001',
    partnerCode: 'PTR-0001',
    name: '한빛물류 협력사 (샘플)',
    partnerType: 'SUPPLIER',
    status: 'ACTIVE',
    lastEventAt: T_EARLY,
    version: 1,
  },
  {
    id: 'partner-sample-0002',
    partnerCode: 'PTR-0002',
    name: '그린프레시 유통 (샘플)',
    partnerType: 'CUSTOMER',
    status: 'ACTIVE',
    lastEventAt: T_EARLY,
    version: 1,
  },
] as const;

const REF_TABLES: Readonly<Record<string, readonly Record<string, unknown>[]>> = {
  warehouses: [WAREHOUSE],
  zones: ZONES,
  locations: LOCATIONS,
  skus: SKUS,
  lots: LOTS,
  partners: PARTNERS,
};

function refsFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);
  const m = pathname.match(new RegExp(`^${ADMIN}/dashboard/refs/([^/]+)$`));
  if (!m) return undefined;
  const type = decodeURIComponent(m[1]);
  const table = REF_TABLES[type];
  if (!table) return undefined;
  let rows = [...table];
  const status = query.get('status');
  if (status) rows = rows.filter((r) => r.status === status);
  const q = query.get('q');
  if (q) {
    const needle = q.toLowerCase();
    rows = rows.filter((r) =>
      Object.values(r).some((v) => typeof v === 'string' && v.toLowerCase().includes(needle)),
    );
  }
  return paginate(rows, query, 'lastEventAt,desc');
}

// ---------------------------------------------------------------------------
// § 1.1 inventory snapshot — GET /dashboard/inventory (+ /by-key)
// ---------------------------------------------------------------------------

/**
 * Every code below is read off the ref rows above (`LOCATIONS[i].locationCode`
 * etc.) — never a second, hand-typed literal — so a code changing in one
 * place cannot silently drift from the other (AC-3 / TASK-MONO-675 bite
 * target: corrupt one literal here and the cross-reference test in
 * `sample-fixtures-schema-wms.test.ts` goes red).
 */
const INVENTORY = [
  {
    locationId: LOCATIONS[0].id,
    skuId: SKUS[0].id,
    lotId: LOTS[0].id,
    warehouseId: WAREHOUSE.id,
    locationCode: LOCATIONS[0].locationCode,
    skuCode: SKUS[0].skuCode,
    lotNo: LOTS[0].lotNo,
    warehouseCode: WAREHOUSE.warehouseCode,
    availableQty: 80,
    reservedQty: 20,
    damagedQty: 0,
    onHandQty: 100,
    lowStockFlag: false,
    lastAdjustedAt: T_MID,
    lastEventAt: T_MID,
    version: 5,
  },
  {
    locationId: LOCATIONS[1].id,
    skuId: SKUS[1].id,
    lotId: null as string | null,
    warehouseId: WAREHOUSE.id,
    locationCode: LOCATIONS[1].locationCode,
    skuCode: SKUS[1].skuCode,
    lotNo: null as string | null,
    warehouseCode: WAREHOUSE.warehouseCode,
    availableQty: 3,
    reservedQty: 2,
    damagedQty: 0,
    onHandQty: 5,
    // 🔴 the ONE low-stock row — non-vacuity for AC-8's alert-count derivation
    // (a card counting every row instead of the low ones would still pass a
    // vacuous "count > 0" check if every row were low; this world keeps 2 of
    // 3 rows NOT low so the distinction is measurable).
    lowStockFlag: true,
    lastAdjustedAt: T_LATE,
    lastEventAt: T_LATE,
    version: 3,
  },
  {
    locationId: LOCATIONS[2].id,
    skuId: SKUS[2].id,
    lotId: LOTS[1].id,
    warehouseId: WAREHOUSE.id,
    locationCode: LOCATIONS[2].locationCode,
    skuCode: SKUS[2].skuCode,
    lotNo: LOTS[1].lotNo,
    warehouseCode: WAREHOUSE.warehouseCode,
    availableQty: 40,
    reservedQty: 10,
    damagedQty: 2,
    onHandQty: 52,
    lowStockFlag: false,
    lastAdjustedAt: T_MID,
    lastEventAt: T_MID,
    version: 2,
  },
] as const;

function inventoryListFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);
  if (!new RegExp(`^${ADMIN}/dashboard/inventory$`).test(pathname)) return undefined;
  let rows = [...INVENTORY];
  const warehouseId = query.get('warehouseId');
  const locationId = query.get('locationId');
  const skuId = query.get('skuId');
  const lotId = query.get('lotId');
  const lowStockOnly = query.get('lowStockOnly') === 'true';
  const minOnHand = query.get('minOnHand');
  if (warehouseId) rows = rows.filter((r) => r.warehouseId === warehouseId);
  if (locationId) rows = rows.filter((r) => r.locationId === locationId);
  if (skuId) rows = rows.filter((r) => r.skuId === skuId);
  if (lotId) rows = rows.filter((r) => r.lotId === lotId);
  if (lowStockOnly) rows = rows.filter((r) => r.lowStockFlag === true);
  if (minOnHand !== null) rows = rows.filter((r) => r.onHandQty >= Number(minOnHand));
  return paginate(rows, query, 'lastEventAt,desc');
}

function inventoryByKeyFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);
  if (!new RegExp(`^${ADMIN}/dashboard/inventory/by-key$`).test(pathname)) return undefined;
  const locationId = query.get('locationId');
  const skuId = query.get('skuId');
  const lotId = query.get('lotId');
  const row = INVENTORY.find(
    (r) =>
      r.locationId === locationId &&
      r.skuId === skuId &&
      (lotId ? r.lotId === lotId : true),
  );
  if (!row) return fixtureNotFound('NOT_FOUND', 'no inventory row for this key');
  return row;
}

// ---------------------------------------------------------------------------
// § 1.2 throughput — GET /dashboard/throughput
// ---------------------------------------------------------------------------

function throughputFixture(path: string): unknown {
  const { pathname } = splitPath(path);
  if (!new RegExp(`^${ADMIN}/dashboard/throughput$`).test(pathname)) return undefined;
  return {
    warehouseId: WAREHOUSE.id,
    from: '2026-09-01',
    to: '2026-09-15',
    days: [
      {
        date: '2026-09-14',
        inbound: { putawayCount: 12, qtyReceived: 480 },
        outbound: { shipmentCount: 9, qtyShipped: 312 },
      },
      {
        date: '2026-09-15',
        inbound: { putawayCount: 8, qtyReceived: 300 },
        outbound: { shipmentCount: 6, qtyShipped: 200 },
      },
    ],
    totals: {
      inbound: { putawayCount: 20, qtyReceived: 780 },
      outbound: { shipmentCount: 15, qtyShipped: 512 },
    },
  };
}

// ---------------------------------------------------------------------------
// outbound orders — the ONE world both the outbound-service surface
// (`wms_outbound`) and the admin read-model surface (`wms`) project facts
// about (§ outbound-service-api.md § 1 / admin-service-api.md § 1.3)
// ---------------------------------------------------------------------------

interface OrderLine {
  orderLineId: string;
  lineNo: number;
  skuId: string;
  skuCode: string;
  lotId: string | null;
  qtyOrdered: number;
}

interface OutboundOrder {
  orderId: string;
  orderNo: string;
  source: string;
  customerPartnerId: string;
  warehouseId: string;
  status: string;
  sagaState: string;
  lines: OrderLine[];
  version: number;
  createdAt: string;
  updatedAt: string;
  requiredShipDate: string;
}

const OUTBOUND_ORDERS: readonly OutboundOrder[] = [
  {
    orderId: 'ord-sample-0001',
    orderNo: 'ORD-20260501-0001',
    source: 'MANUAL',
    customerPartnerId: PARTNERS[1].id,
    warehouseId: WAREHOUSE.id,
    status: 'PICKING',
    sagaState: 'RESERVED',
    lines: [
      {
        orderLineId: 'ol-sample-0001-1',
        lineNo: 1,
        skuId: SKUS[0].id,
        skuCode: SKUS[0].skuCode,
        lotId: LOTS[0].id,
        qtyOrdered: 50,
      },
    ],
    version: 1,
    createdAt: T_EARLY,
    updatedAt: T_EARLY,
    requiredShipDate: '2026-09-20',
  },
  {
    orderId: 'ord-sample-0002',
    orderNo: 'ORD-20260502-0002',
    source: 'WEBHOOK_ERP',
    customerPartnerId: PARTNERS[1].id,
    warehouseId: WAREHOUSE.id,
    status: 'PICKED',
    sagaState: 'PICKING_CONFIRMED',
    lines: [
      {
        orderLineId: 'ol-sample-0002-1',
        lineNo: 1,
        skuId: SKUS[1].id,
        skuCode: SKUS[1].skuCode,
        lotId: null,
        qtyOrdered: 30,
      },
    ],
    version: 2,
    createdAt: T_MID,
    updatedAt: T_MID,
    requiredShipDate: '2026-09-21',
  },
  {
    orderId: 'ord-sample-0003',
    orderNo: 'ORD-20260503-0003',
    source: 'MANUAL',
    customerPartnerId: PARTNERS[1].id,
    warehouseId: WAREHOUSE.id,
    status: 'SHIPPED',
    sagaState: 'SHIPPED',
    lines: [
      {
        orderLineId: 'ol-sample-0003-1',
        lineNo: 1,
        skuId: SKUS[2].id,
        skuCode: SKUS[2].skuCode,
        lotId: LOTS[1].id,
        qtyOrdered: 20,
      },
    ],
    version: 4,
    createdAt: T_EARLY,
    updatedAt: T_LATE,
    requiredShipDate: '2026-09-13',
  },
  {
    orderId: 'ord-sample-0004',
    orderNo: 'ORD-20260504-0004',
    source: 'MANUAL',
    customerPartnerId: PARTNERS[1].id,
    warehouseId: WAREHOUSE.id,
    status: 'CANCELLED',
    sagaState: 'CANCELLED',
    lines: [
      {
        orderLineId: 'ol-sample-0004-1',
        lineNo: 1,
        skuId: SKUS[0].id,
        skuCode: SKUS[0].skuCode,
        lotId: LOTS[0].id,
        qtyOrdered: 10,
      },
    ],
    version: 2,
    createdAt: T_MID,
    updatedAt: T_MID,
    requiredShipDate: '2026-09-18',
  },
];

function orderById(id: string): OutboundOrder | undefined {
  return OUTBOUND_ORDERS.find((o) => o.orderId === id);
}

/**
 * The admin read-model's coarser status (`overview-state.ts`'s own comment:
 * "the read model collapses order status to RECEIVED/SHIPPED/CANCELLED").
 * DERIVED, never a second hand-typed enum — see module header.
 */
function collapseStatus(status: string): 'RECEIVED' | 'SHIPPED' | 'CANCELLED' {
  if (status === 'SHIPPED') return 'SHIPPED';
  if (status === 'CANCELLED') return 'CANCELLED';
  return 'RECEIVED';
}

const ADMIN_ORDERS = OUTBOUND_ORDERS.map((o) => ({
  orderId: o.orderId,
  orderNo: o.orderNo,
  warehouseId: o.warehouseId,
  customerPartnerId: o.customerPartnerId,
  customerName: PARTNERS.find((p) => p.id === o.customerPartnerId)?.name,
  status: collapseStatus(o.status),
  source: o.source,
  requiredShipDate: o.requiredShipDate,
  lineCount: o.lines.length,
  sagaState: o.sagaState,
  receivedAt: o.createdAt,
  shippedAt: o.status === 'SHIPPED' ? o.updatedAt : null,
  lastEventAt: o.updatedAt,
  version: 1,
}));

function ordersFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);
  if (!new RegExp(`^${ADMIN}/dashboard/orders$`).test(pathname)) return undefined;
  let rows = [...ADMIN_ORDERS];
  const warehouseId = query.get('warehouseId');
  const customerPartnerId = query.get('customerPartnerId');
  const status = query.get('status');
  const sagaState = query.get('sagaState');
  if (warehouseId) rows = rows.filter((r) => r.warehouseId === warehouseId);
  if (customerPartnerId) rows = rows.filter((r) => r.customerPartnerId === customerPartnerId);
  if (status) rows = rows.filter((r) => r.status === status);
  if (sagaState) rows = rows.filter((r) => r.sagaState === sagaState);
  return paginate(rows, query, 'receivedAt,desc');
}

// ---------------------------------------------------------------------------
// admin dashboard/shipments — reached under TWO surfaces: `wms` (the
// `/wms/outbound` 택배/출고 read section, `listShipments`) AND `wms_outbound`
// (the per-call baseUrl override in `resolveShipmentIdForOrder` — this
// ticket's Edge Case: "샘플 라우터 매칭이 base 가 아니라 경로로 되는지"). ONE shared
// fixture function so both answer the SAME data.
// ---------------------------------------------------------------------------

const SAMPLE_WMS_SHIPMENTS = OUTBOUND_ORDERS.filter((o) => o.status === 'SHIPPED').map((o) => ({
  shipmentId: 'ship-sample-0001',
  orderId: o.orderId,
  orderNo: o.orderNo,
  warehouseId: o.warehouseId,
  shipmentNo: 'SHP-20260505-0001',
  carrierCode: 'CJ-LOGISTICS',
  trackingNo: 'TRACK-SAMPLE-0001',
  shippedAt: o.updatedAt,
  totalQty: o.lines.reduce((sum, l) => sum + l.qtyOrdered, 0),
  lastEventAt: o.updatedAt,
  version: 1,
}));

function shipmentsQueryFixture(query: URLSearchParams): unknown {
  let rows = [...SAMPLE_WMS_SHIPMENTS];
  const warehouseId = query.get('warehouseId');
  const orderId = query.get('orderId');
  const carrierCode = query.get('carrierCode');
  if (warehouseId) rows = rows.filter((r) => r.warehouseId === warehouseId);
  if (orderId) rows = rows.filter((r) => r.orderId === orderId);
  if (carrierCode) rows = rows.filter((r) => r.carrierCode === carrierCode);
  return paginate(rows, query, 'shippedAt,desc');
}

function shipmentsFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);
  if (!new RegExp(`^${ADMIN}/dashboard/shipments$`).test(pathname)) return undefined;
  return shipmentsQueryFixture(query);
}

// ---------------------------------------------------------------------------
// § 1.4 inbound (ASN) summary + inspection
// ---------------------------------------------------------------------------

const ASNS = [
  {
    asnId: 'asn-sample-0001',
    asnNo: 'ASN-20260420-0001',
    warehouseId: WAREHOUSE.id,
    warehouseCode: WAREHOUSE.warehouseCode,
    supplierPartnerId: PARTNERS[0].id,
    supplierName: PARTNERS[0].name,
    status: 'RECEIVED',
    source: 'MANUAL',
    expectedArriveDate: '2026-09-10',
    lineCount: 3,
    receivedAt: T_MID,
    closedAt: null as string | null,
    lastEventAt: T_MID,
    version: 1,
  },
  {
    asnId: 'asn-sample-0002',
    asnNo: 'ASN-20260415-0002',
    warehouseId: WAREHOUSE.id,
    warehouseCode: WAREHOUSE.warehouseCode,
    supplierPartnerId: PARTNERS[0].id,
    supplierName: PARTNERS[0].name,
    status: 'CLOSED',
    source: 'WEBHOOK_ERP',
    expectedArriveDate: '2026-09-05',
    lineCount: 5,
    receivedAt: T_EARLY,
    closedAt: T_MID,
    lastEventAt: T_MID,
    version: 2,
  },
] as const;

function asnsFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);
  if (!new RegExp(`^${ADMIN}/dashboard/asns$`).test(pathname)) return undefined;
  let rows = [...ASNS];
  const warehouseId = query.get('warehouseId');
  const supplierPartnerId = query.get('supplierPartnerId');
  const status = query.get('status');
  const source = query.get('source');
  if (warehouseId) rows = rows.filter((r) => r.warehouseId === warehouseId);
  if (supplierPartnerId) rows = rows.filter((r) => r.supplierPartnerId === supplierPartnerId);
  if (status) rows = rows.filter((r) => r.status === status);
  if (source) rows = rows.filter((r) => r.source === source);
  return paginate(rows, query, 'receivedAt,desc');
}

/** Only the CLOSED ASN has a completed inspection — the RECEIVED one is
 *  honestly "not yet inspected" (§ 1.4 `NOT_FOUND` — the same shape a real
 *  visitor sees mid-lifecycle, not a synthetic always-happy path). */
function asnInspectionFixture(path: string): unknown {
  const { pathname } = splitPath(path);
  const m = pathname.match(new RegExp(`^${ADMIN}/dashboard/asns/([^/]+)/inspection$`));
  if (!m) return undefined;
  const asnId = decodeURIComponent(m[1]);
  if (asnId === ASNS[1].asnId) {
    return {
      asnId: ASNS[1].asnId,
      warehouseId: WAREHOUSE.id,
      inspectionCompletedAt: T_MID,
      inspectorId: 'insp-sample-0001',
      totalLines: 5,
      discrepancyCount: 0,
      totalQtyExpected: 500,
      totalQtyPassed: 500,
      totalQtyDamaged: 0,
      totalQtyShort: 0,
    };
  }
  return fixtureNotFound('NOT_FOUND', 'inspection not yet completed');
}

// ---------------------------------------------------------------------------
// § 1.5 adjustment audit — GET /dashboard/adjustments
// ---------------------------------------------------------------------------

const ADJUSTMENTS = [
  {
    id: 'adj-sample-0001',
    locationId: LOCATIONS[0].id,
    skuId: SKUS[0].id,
    lotId: LOTS[0].id,
    warehouseId: WAREHOUSE.id,
    bucket: 'AVAILABLE',
    delta: -5,
    reasonCode: 'CYCLE_COUNT',
    reasonNote: '재고 실사 조정 (샘플)',
    occurredAt: T_MID,
  },
  {
    id: 'adj-sample-0002',
    locationId: LOCATIONS[1].id,
    skuId: SKUS[1].id,
    lotId: null as string | null,
    warehouseId: WAREHOUSE.id,
    bucket: 'DAMAGED',
    delta: 2,
    reasonCode: 'DAMAGE_FOUND',
    reasonNote: '입고 중 손상품 발견 (샘플)',
    occurredAt: T_LATE,
  },
] as const;

function adjustmentsFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);
  if (!new RegExp(`^${ADMIN}/dashboard/adjustments$`).test(pathname)) return undefined;
  let rows = [...ADJUSTMENTS];
  const warehouseId = query.get('warehouseId');
  const locationId = query.get('locationId');
  const skuId = query.get('skuId');
  const bucket = query.get('bucket');
  const reasonCode = query.get('reasonCode');
  if (warehouseId) rows = rows.filter((r) => r.warehouseId === warehouseId);
  if (locationId) rows = rows.filter((r) => r.locationId === locationId);
  if (skuId) rows = rows.filter((r) => r.skuId === skuId);
  if (bucket) rows = rows.filter((r) => r.bucket === bucket);
  if (reasonCode) rows = rows.filter((r) => r.reasonCode === reasonCode);
  return paginate(rows, query, 'occurredAt,desc');
}

// ---------------------------------------------------------------------------
// § 1.6 alerts — GET /dashboard/alerts
// ---------------------------------------------------------------------------

const ALERTS = [
  {
    alertId: 'alert-sample-0001',
    alertType: 'LOW_STOCK',
    warehouseId: WAREHOUSE.id,
    message: `${SKUS[1].name.replace(' (샘플)', '')} 재고가 임계치 이하로 떨어졌습니다 (샘플)`,
    detectedAt: T_LATE,
    acknowledged: false,
    acknowledgedAt: null as string | null,
    acknowledgedBy: null as string | null,
  },
  {
    alertId: 'alert-sample-0002',
    alertType: 'LOW_STOCK',
    warehouseId: WAREHOUSE.id,
    message: `${SKUS[1].name.replace(' (샘플)', '')} 재고가 임계치 이하로 떨어졌습니다 (샘플)`,
    detectedAt: T_MID,
    acknowledged: true,
    acknowledgedAt: T_MID,
    acknowledgedBy: 'op-sample-0001',
  },
] as const;

function alertsFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);
  if (!new RegExp(`^${ADMIN}/dashboard/alerts$`).test(pathname)) return undefined;
  let rows = [...ALERTS];
  const alertType = query.get('alertType');
  const warehouseId = query.get('warehouseId');
  const acknowledged = query.get('acknowledged');
  if (alertType) rows = rows.filter((r) => r.alertType === alertType);
  if (warehouseId) rows = rows.filter((r) => r.warehouseId === warehouseId);
  if (acknowledged !== null) {
    const want = acknowledged === 'true';
    rows = rows.filter((r) => r.acknowledged === want);
  }
  return paginate(rows, query, 'detectedAt,desc');
}

// ---------------------------------------------------------------------------
// § 5 settings — GET /settings (+ /settings/{key})
// ---------------------------------------------------------------------------

const SETTINGS = [
  {
    key: 'inventory.reservation.ttl_hours',
    scope: 'GLOBAL',
    warehouseId: null as string | null,
    valueJson: 24,
    description: '예약 유지 시간(시간 단위) (샘플)',
    version: 3,
    updatedAt: T_MID,
    updatedBy: 'op-sample-0001',
  },
  {
    key: 'inventory.low_stock.default_threshold_qty',
    scope: 'GLOBAL',
    warehouseId: null as string | null,
    valueJson: 5,
    description: '재고 부족 경고 임계 수량 (샘플)',
    version: 2,
    updatedAt: T_EARLY,
    updatedBy: 'op-sample-0001',
  },
] as const;

function settingsListFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);
  if (!new RegExp(`^${ADMIN}/settings$`).test(pathname)) return undefined;
  let rows = [...SETTINGS];
  const keyPrefix = query.get('keyPrefix');
  const scope = query.get('scope');
  const warehouseId = query.get('warehouseId');
  if (keyPrefix) rows = rows.filter((r) => r.key.startsWith(keyPrefix));
  if (scope) rows = rows.filter((r) => r.scope === scope);
  if (warehouseId) rows = rows.filter((r) => r.warehouseId === warehouseId);
  return paginate(rows, query, 'key,asc');
}

function settingSingleFixture(path: string): unknown {
  const { pathname } = splitPath(path);
  const m = pathname.match(new RegExp(`^${ADMIN}/settings/([^/]+)$`));
  if (!m) return undefined;
  const key = decodeURIComponent(m[1]);
  const row = SETTINGS.find((s) => s.key === key);
  if (!row) return fixtureNotFound('SETTING_NOT_FOUND', 'setting not found');
  return row;
}

// ---------------------------------------------------------------------------
// § 6.2 projection status — GET /operations/projection-status
// ---------------------------------------------------------------------------

function projectionStatusFixture(path: string): unknown {
  const { pathname } = splitPath(path);
  if (!new RegExp(`^${ADMIN}/operations/projection-status$`).test(pathname)) return undefined;
  return {
    projections: [
      {
        topic: 'wms.inventory.adjusted.v1',
        consumerGroup: 'admin-projection',
        lagSeconds: 1.4,
        lastEventAt: T_LATE,
        lastProjectedAt: T_LATE,
        lifetimeApplied: 12048,
        lifetimeIgnoredDuplicate: 17,
        lifetimeFailed: 0,
      },
      {
        topic: 'wms.master.sku.v1',
        consumerGroup: 'admin-projection',
        lagSeconds: 0.2,
        lastEventAt: T_MID,
        lastProjectedAt: T_MID,
        lifetimeApplied: 500,
        lifetimeIgnoredDuplicate: 2,
        lifetimeFailed: 0,
      },
    ],
    worstLagSeconds: 1.4,
  };
}

// ---------------------------------------------------------------------------
// `wms:wms` — admin-service dispatch
// ---------------------------------------------------------------------------

function wmsAdminFixture(path: string): unknown {
  return (
    inventoryByKeyFixture(path) ??
    inventoryListFixture(path) ??
    throughputFixture(path) ??
    ordersFixture(path) ??
    shipmentsFixture(path) ??
    asnInspectionFixture(path) ??
    asnsFixture(path) ??
    adjustmentsFixture(path) ??
    alertsFixture(path) ??
    refsFixture(path) ??
    settingSingleFixture(path) ??
    settingsListFixture(path) ??
    projectionStatusFixture(path)
  );
}

// ---------------------------------------------------------------------------
// `wms:wms_outbound` — outbound-service order lifecycle + the admin-shaped
// shipment-id-resolver override (Edge Case — matched by PATH, not base)
// ---------------------------------------------------------------------------

const OUTBOUND_ORDER_SUMMARIES = OUTBOUND_ORDERS.map((o) => ({
  orderId: o.orderId,
  orderNo: o.orderNo,
  source: o.source,
  customerPartnerId: o.customerPartnerId,
  warehouseId: o.warehouseId,
  status: o.status,
  sagaState: o.sagaState,
  lineCount: o.lines.length,
  totalQtyOrdered: o.lines.reduce((sum, l) => sum + l.qtyOrdered, 0),
  requiredShipDate: o.requiredShipDate,
  createdAt: o.createdAt,
  updatedAt: o.updatedAt,
}));

function ordersListFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);
  if (!new RegExp(`^${OUTBOUND}/orders$`).test(pathname)) return undefined;
  let rows = [...OUTBOUND_ORDER_SUMMARIES];
  const status = query.get('status');
  const warehouseId = query.get('warehouseId');
  const orderNo = query.get('orderNo');
  const source = query.get('source');
  if (status) rows = rows.filter((r) => r.status === status);
  if (warehouseId) rows = rows.filter((r) => r.warehouseId === warehouseId);
  if (orderNo) rows = rows.filter((r) => r.orderNo === orderNo);
  if (source) rows = rows.filter((r) => r.source === source);
  return paginate(rows, query, 'updatedAt,desc');
}

function orderDetailFixture(path: string): unknown {
  const { pathname } = splitPath(path);
  const m = pathname.match(new RegExp(`^${OUTBOUND}/orders/([^/]+)$`));
  if (!m) return undefined;
  const order = orderById(decodeURIComponent(m[1]));
  if (!order) return fixtureNotFound('ORDER_NOT_FOUND', 'order not found');
  // `OutboundOrderDetailSchema` has no `requiredShipDate` field of its own
  // (`.passthrough()` tolerates the extra one this fixture's shared `order`
  // object carries for `OUTBOUND_ORDER_SUMMARIES`'s benefit).
  return order;
}

const SAGAS: Readonly<Record<string, { sagaId: string; version: number; startedAt: string }>> = {
  'ord-sample-0001': { sagaId: 'saga-sample-0001', version: 1, startedAt: T_EARLY },
  'ord-sample-0002': { sagaId: 'saga-sample-0002', version: 2, startedAt: T_MID },
  'ord-sample-0003': { sagaId: 'saga-sample-0003', version: 4, startedAt: T_EARLY },
  'ord-sample-0004': { sagaId: 'saga-sample-0004', version: 2, startedAt: T_MID },
};

function sagaFixture(path: string): unknown {
  const { pathname } = splitPath(path);
  const m = pathname.match(new RegExp(`^${OUTBOUND}/orders/([^/]+)/saga$`));
  if (!m) return undefined;
  const orderId = decodeURIComponent(m[1]);
  const order = orderById(orderId);
  const saga = SAGAS[orderId];
  if (!order || !saga) return fixtureNotFound('ORDER_NOT_FOUND', 'order not found');
  return {
    sagaId: saga.sagaId,
    orderId,
    state: order.sagaState,
    failureReason: null,
    startedAt: saga.startedAt,
    lastTransitionAt: order.updatedAt,
    version: saga.version,
  };
}

/** § 2.4 — every order in this world has already had its (v1: single) picking
 *  request created, EXCEPT the CANCELLED one (cancelled before the saga
 *  reached `RESERVED`) — the contract's own documented `{content: []}` case,
 *  not a synthetic always-populated world. */
const PICKING_REQUESTS: Readonly<Record<string, unknown[]>> = {
  'ord-sample-0001': [
    {
      pickingRequestId: 'pr-sample-0001',
      orderId: 'ord-sample-0001',
      sagaId: SAGAS['ord-sample-0001'].sagaId,
      warehouseId: WAREHOUSE.id,
      status: 'SUBMITTED',
      lines: [
        {
          pickingRequestLineId: 'prl-sample-0001',
          orderLineId: 'ol-sample-0001-1',
          skuId: SKUS[0].id,
          lotId: LOTS[0].id,
          locationId: LOCATIONS[0].id,
          qtyToPick: 50,
        },
      ],
      version: 0,
      createdAt: T_EARLY,
      updatedAt: T_EARLY,
    },
  ],
  'ord-sample-0002': [
    {
      pickingRequestId: 'pr-sample-0002',
      orderId: 'ord-sample-0002',
      sagaId: SAGAS['ord-sample-0002'].sagaId,
      warehouseId: WAREHOUSE.id,
      status: 'CONFIRMED',
      lines: [
        {
          pickingRequestLineId: 'prl-sample-0002',
          orderLineId: 'ol-sample-0002-1',
          skuId: SKUS[1].id,
          lotId: null,
          locationId: LOCATIONS[1].id,
          qtyToPick: 30,
        },
      ],
      version: 1,
      createdAt: T_MID,
      updatedAt: T_MID,
    },
  ],
  'ord-sample-0003': [
    {
      pickingRequestId: 'pr-sample-0003',
      orderId: 'ord-sample-0003',
      sagaId: SAGAS['ord-sample-0003'].sagaId,
      warehouseId: WAREHOUSE.id,
      status: 'CONFIRMED',
      lines: [
        {
          pickingRequestLineId: 'prl-sample-0003',
          orderLineId: 'ol-sample-0003-1',
          skuId: SKUS[2].id,
          lotId: LOTS[1].id,
          locationId: LOCATIONS[2].id,
          qtyToPick: 20,
        },
      ],
      version: 1,
      createdAt: T_EARLY,
      updatedAt: T_LATE,
    },
  ],
  'ord-sample-0004': [],
};

function pickingRequestsFixture(path: string): unknown {
  const { pathname } = splitPath(path);
  const m = pathname.match(new RegExp(`^${OUTBOUND}/orders/([^/]+)/picking-requests$`));
  if (!m) return undefined;
  const orderId = decodeURIComponent(m[1]);
  const order = orderById(orderId);
  if (!order) return fixtureNotFound('ORDER_NOT_FOUND', 'order not found');
  return { content: PICKING_REQUESTS[orderId] ?? [] };
}

/**
 * `resolveShipmentIdForOrder` (`outbound-tms-api.ts`) overrides the request's
 * `baseUrl` to `WMS_ADMIN_BASE_URL` but keeps the `wms_outbound` PROFILE (its
 * `logPrefix` never changes) — so this surface, not `wms`, is what actually
 * answers `GET /api/v1/admin/dashboard/shipments?orderId=…` in sample mode.
 * The router matches on the FULL constructed path, not the base, so this
 * works exactly like the real gateway (this ticket's Edge Case).
 */
function adminShipmentOverrideFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);
  if (!new RegExp(`^${ADMIN}/dashboard/shipments$`).test(pathname)) return undefined;
  return shipmentsQueryFixture(query);
}

function wmsOutboundFixture(path: string): unknown {
  return (
    adminShipmentOverrideFixture(path) ??
    sagaFixture(path) ??
    pickingRequestsFixture(path) ??
    orderDetailFixture(path) ??
    ordersListFixture(path)
  );
}

// ---------------------------------------------------------------------------
// `flat:wms_outbound_logistics` — carrier dispatch (`callScmGateway` →
// `callFlatEnvelopeGateway`, core `flat` — see module header)
// ---------------------------------------------------------------------------

const SAMPLE_WMS_DISPATCH = {
  id: 'dispatch-sample-0001',
  shipmentId: SAMPLE_WMS_SHIPMENTS[0]?.shipmentId,
  status: 'DISPATCHED',
  trackingNo: 'TRACK-SAMPLE-0001',
  carrierCode: 'CJ-LOGISTICS',
};

function dispatchByShipmentFixture(path: string): unknown {
  const { pathname } = splitPath(path);
  const m = pathname.match(new RegExp(`^${LOGISTICS}/dispatches/by-shipment/([^/]+)$`));
  if (!m) return undefined;
  const shipmentId = decodeURIComponent(m[1]);
  if (shipmentId !== SAMPLE_WMS_DISPATCH.shipmentId) {
    return fixtureNotFound('DISPATCH_NOT_FOUND', 'no dispatch for this shipment');
  }
  return { data: SAMPLE_WMS_DISPATCH };
}

function wmsOutboundLogisticsFixture(path: string): unknown {
  return dispatchByShipmentFixture(path);
}

// ---------------------------------------------------------------------------
// exports
// ---------------------------------------------------------------------------

export const WMS_FIXTURE_HANDLERS: Readonly<Record<string, WmsFixtureHandler>> = {
  'wms:wms': wmsAdminFixture,
  'wms:wms_outbound': wmsOutboundFixture,
  'flat:wms_outbound_logistics': wmsOutboundLogisticsFixture,
};

/**
 * The AGGREGATE of every seed array on these three surfaces, for the R2ⓐ
 * label guard (same reasoning as `iam.ts`/`ecommerce.ts`/`erp.ts`/`finance.ts`).
 * `PICKING_REQUESTS` is a `Record<orderId, PickingRequest[]>` — flattened with
 * `Object.values(...).flat()` (TASK-PC-FE-283 D7's lesson: an id-keyed MAP
 * left nested lets the guard classify a string by the MAP's id key instead of
 * the row's own field key). `SAGAS` is exposed via the router-shaped
 * `sagaFixture` output instead of its raw internal record, for the same
 * reason (its raw shape has no `state`/`failureReason` fields to classify).
 */
export const WMS_FIXTURE_DOCUMENTS: Readonly<Record<string, unknown>> = {
  'wms:wms': {
    warehouses: [WAREHOUSE],
    zones: ZONES,
    locations: LOCATIONS,
    skus: SKUS,
    lots: LOTS,
    partners: PARTNERS,
    inventory: INVENTORY,
    throughput: throughputFixture(`${ADMIN}/dashboard/throughput`),
    orders: ADMIN_ORDERS,
    shipments: SAMPLE_WMS_SHIPMENTS,
    asns: ASNS,
    inspection: asnInspectionFixture(`${ADMIN}/dashboard/asns/${ASNS[1].asnId}/inspection`),
    adjustments: ADJUSTMENTS,
    alerts: ALERTS,
    settings: SETTINGS,
    projectionStatus: projectionStatusFixture(`${ADMIN}/operations/projection-status`),
  },
  'wms:wms_outbound': {
    orders: OUTBOUND_ORDERS,
    summaries: OUTBOUND_ORDER_SUMMARIES,
    sagas: Object.keys(SAGAS).map((orderId) => sagaFixture(`${OUTBOUND}/orders/${orderId}/saga`)),
    pickingRequests: Object.values(PICKING_REQUESTS).flat(),
  },
  'flat:wms_outbound_logistics': {
    dispatch: SAMPLE_WMS_DISPATCH,
  },
};
