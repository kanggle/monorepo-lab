import { SAMPLE_AS_OF, SAMPLE_LABEL_SUFFIX, SAMPLE_TENANT_ID } from '../codes';
import { fixtureNotFound } from '../router';

/**
 * Same shape as `fixtures/index.ts`'s `FixtureHandler` — written out here
 * (rather than imported) so this module has no edge back to `index.ts`, which
 * imports THIS module (`shared/sample/**` must stay a DAG the isolation guard
 * can read — see `iam.ts`/`ecommerce.ts`/`erp.ts`'s identical note).
 *
 * 🔴 D3 (`TASK-PC-FE-286` circular-init trap) — this module must NOT import
 *    `dashboards.ts`, `registry.ts` or `fixtures/index.ts`. `dashboards.ts`
 *    imports THIS module to derive the SCM overview card; the reverse edge
 *    would deadlock module init exactly like 286's `registry.ts → finance.ts`
 *    incident (see that file's D3 note).
 */
type ScmFixtureHandler = (path: string) => unknown;

/**
 * scm domain fixtures (TASK-PC-FE-288 — `ADR-MONO-074` execution 7/8, the
 * LAST domain ticket): the 3 `callScmGateway` → `callFlatEnvelopeGateway`
 * surfaces —
 *   - `scm`               — procurement PO read (list+detail) + inventory-
 *                            visibility (snapshot cross-node/single-node,
 *                            per-SKU breakdown, staleness, node list). All
 *                            GET, all server-side `getDomainFacingToken()`.
 *   - `scm_replenishment` — demand-planning reorder-suggestion read
 *                            (list+detail). approve/dismiss are POST, so the
 *                            router's generic `403 SAMPLE_READ_ONLY` branch
 *                            handles them — no fixture code needed (AC-6).
 *   - `scm_config`        — demand-planning per-SKU reorder-policy +
 *                            sku-supplier-map seed GET (404-as-empty when not
 *                            configured yet). PUT is non-GET → the same
 *                            generic 403 branch.
 *
 * Hand-authored synthetic data (ADR-MONO-074 A4 — no extraction path from any
 * backend). Each handler is parsed by the SAME zod schema the real screen uses
 * (`tests/unit/sample-fixtures-schema-scm.test.ts`).
 *
 * R2ⓐ: human-readable strings end with «(샘플)»; ids, codes, enums, dates and
 * money-decimal values do not (`shared/sample/label-rule.ts` — see that
 * file's TASK-PC-FE-288 additions for the new keys this file introduces).
 * 🔴 This domain has almost no human-readable fields — like 286's finance/
 * ledger finding (D1 there), no scm schema here has a "품목명" (product-name)
 * field: `PoLineSchema.sku` is a CODE, and `SuggestionSchema` has no name
 * field at all. The only two human-readable fields that exist anywhere in
 * these three surfaces are `PurchaseOrderResponse.supplierName` (the resolved
 * supplier master's `name` — `TASK-MONO-677`) and inventory-visibility
 * `NodeRow.name` (a warehouse/3PL facility name) — both carry the suffix.
 *
 * ── ONE SCM WORLD (AC-3 / cross-view) ─────────────────────────────────────
 * A tiny supplier master (`SUPPLIERS`, id + code + name) resolves every PO's
 * `supplierCode`/`supplierName` — mirroring the REAL producer resolution rule
 * (`procurement-api.md` § `PurchaseOrderResponse` — supplier reference
 * fields, TASK-MONO-677): id match first, then code match, else both fields
 * `null` (never the raw id). A UUID-shaped, unmatched `supplierId` therefore
 * renders `이름 확인 불가` on `/scm/procurement`, never the UUID itself.
 *
 * The inventory-visibility world is a SEPARATE small graph (3 nodes, 4
 * cross-node snapshot rows) that the `scm` surface's PO/snapshot/node/
 * staleness handlers all read from — a node id appearing in a snapshot row
 * always resolves on `/api/v1/inventory-visibility/nodes` (AC-8: the overview
 * SCM card derives its node set/names from the SAME node registry).
 *
 * The `scm_replenishment` suggestions reference the SAME SKU codes as the
 * inventory-visibility world (plausible alongside it — not required to be the
 * identical row set, per the task's Cross-view note) and one suggestion's
 * `materializedPoId` points at a REAL procurement PO id in this file's own
 * `SCM_POS` (a suggestion that was approved into a DRAFT PO).
 */

const SUFFIX = SAMPLE_LABEL_SUFFIX;
const S5_WARNING = `Not for procurement decisions (S5)${SUFFIX}`;

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

// ===========================================================================
// supplier master (internal resolution table — NOT a console-consumed GET;
// the console has no `GET /api/procurement/suppliers/**` call site at all,
// only the PO responses that carry the RESOLVED code/name — see
// `PurchaseOrderSchema` in `features/scm-ops/api/types.ts`)
// ===========================================================================

interface SupplierSeed {
  id: string;
  code: string;
  name: string;
}

const SUPPLIERS: readonly SupplierSeed[] = [
  {
    id: '11111111-1111-4111-8111-111111111111',
    code: 'SUP-SAMPLE-01',
    name: `대한물산${SUFFIX}`,
  },
  {
    id: '22222222-2222-4222-8222-222222222222',
    code: 'SUP-SAMPLE-02',
    name: `한빛무역${SUFFIX}`,
  },
];

/** `procurement-api.md` § `PurchaseOrderResponse` — supplier reference
 *  fields, resolution rule 2: id match first, then code match, else null. */
function resolveSupplier(supplierId: string | null | undefined): SupplierSeed | null {
  if (!supplierId) return null;
  return (
    SUPPLIERS.find((s) => s.id === supplierId) ??
    SUPPLIERS.find((s) => s.code === supplierId) ??
    null
  );
}

// ===========================================================================
// procurement — PO read (surface `scm`)
//   GET /api/v1/procurement/po (+ /{poId})
// ===========================================================================

const PO_PATH = '/api/v1/procurement/po';

interface PoLineSeed {
  id: string;
  lineNo: number;
  sku: string;
  supplierSku: string | null;
  quantity: string;
  unitPrice: string;
  receivedQuantity: string;
}

interface PoSeed {
  id: string;
  poNumber: string;
  supplierId: string;
  status: string;
  totalAmount: string;
  currency: string;
  submittedAt: string | null;
  acknowledgedAt: string | null;
  confirmedAt: string | null;
  canceledAt: string | null;
  createdAt: string;
  updatedAt: string;
  lines: PoLineSeed[];
}

/**
 * AC-3 world: `po-sample-0001` resolves by ID (a real supplier master id) ·
 * `po-sample-0002` resolves by CODE (the value a from-suggestion PO carries
 * per `scm-procurement-events.md` / ADR-MONO-050 D9 — the id lookup misses,
 * the code lookup matches `SUPPLIERS[1]`) · `po-sample-0003` matches NEITHER
 * (a UUID-shaped value no supplier owns) → both fields `null` → renders
 * `이름 확인 불가`, never the raw UUID (`master-ref-label.ts`) · `po-sample-0004`
 * reuses `po-sample-0001`'s id (CANCELED, proves the list/detail agree for a
 * repeated supplier too).
 */
const SCM_POS: readonly PoSeed[] = [
  {
    id: 'po-sample-0001',
    poNumber: 'PO-SAMPLE-0001',
    supplierId: SUPPLIERS[0].id,
    status: 'DRAFT',
    totalAmount: '125000.00',
    currency: 'KRW',
    submittedAt: null,
    acknowledgedAt: null,
    confirmedAt: null,
    canceledAt: null,
    createdAt: '2026-09-10T01:00:00Z',
    updatedAt: '2026-09-10T01:00:00Z',
    lines: [
      {
        id: 'poln-sample-0001-1',
        lineNo: 1,
        sku: 'SKU-SAMPLE-001',
        supplierSku: 'SUP01-SKU-001',
        quantity: '10.0000',
        unitPrice: '12500.00',
        receivedQuantity: '0.0000',
      },
    ],
  },
  {
    id: 'po-sample-0002',
    poNumber: 'PO-SAMPLE-0002',
    // A from-suggestion PO — the value is the supplier CODE (ADR-MONO-050
    // D9), not an id. Resolution rule 2's code-fallback proves out here.
    supplierId: SUPPLIERS[1].code,
    status: 'SUBMITTED',
    totalAmount: '100000.00',
    currency: 'KRW',
    submittedAt: '2026-09-11T02:00:00Z',
    acknowledgedAt: null,
    confirmedAt: null,
    canceledAt: null,
    createdAt: '2026-09-11T01:00:00Z',
    updatedAt: '2026-09-11T02:00:00Z',
    lines: [
      {
        id: 'poln-sample-0002-1',
        lineNo: 1,
        sku: 'SKU-SAMPLE-002',
        supplierSku: 'SUP02-SKU-002',
        quantity: '20.0000',
        unitPrice: '5000.00',
        receivedQuantity: '0.0000',
      },
    ],
  },
  {
    id: 'po-sample-0003',
    poNumber: 'PO-SAMPLE-0003',
    // TASK-MONO-677 AC-3 headline case — a UUID-SHAPED value that matches NO
    // supplier by id or by code. `masterRefLabel` renders `이름 확인 불가`, and
    // the raw id above never surfaces as the visible cell text.
    supplierId: '99999999-9999-4999-8999-999999999999',
    status: 'CONFIRMED',
    totalAmount: '62500.00',
    currency: 'KRW',
    submittedAt: '2026-09-05T02:00:00Z',
    acknowledgedAt: '2026-09-06T00:00:00Z',
    confirmedAt: '2026-09-07T00:00:00Z',
    canceledAt: null,
    createdAt: '2026-09-05T01:00:00Z',
    updatedAt: '2026-09-07T00:00:00Z',
    lines: [
      {
        id: 'poln-sample-0003-1',
        lineNo: 1,
        sku: 'SKU-SAMPLE-001',
        supplierSku: null,
        quantity: '5.0000',
        unitPrice: '12500.00',
        receivedQuantity: '5.0000',
      },
    ],
  },
  {
    id: 'po-sample-0004',
    poNumber: 'PO-SAMPLE-0004',
    supplierId: SUPPLIERS[0].id,
    status: 'CANCELED',
    totalAmount: '24000.00',
    currency: 'KRW',
    submittedAt: '2026-08-20T02:00:00Z',
    acknowledgedAt: null,
    confirmedAt: null,
    canceledAt: '2026-08-21T00:00:00Z',
    createdAt: '2026-08-20T01:00:00Z',
    updatedAt: '2026-08-21T00:00:00Z',
    lines: [
      {
        id: 'poln-sample-0004-1',
        lineNo: 1,
        sku: 'SKU-SAMPLE-003',
        supplierSku: null,
        quantity: '8.0000',
        unitPrice: '3000.00',
        receivedQuantity: '0.0000',
      },
    ],
  },
];

/** The wire view of one PO — `supplierCode`/`supplierName` are COMPUTED here
 *  (never stored on `SCM_POS`), exactly as procurement-api.md rule 5 states. */
function poView(po: PoSeed) {
  const resolved = resolveSupplier(po.supplierId);
  return {
    id: po.id,
    tenantId: SAMPLE_TENANT_ID,
    poNumber: po.poNumber,
    supplierId: po.supplierId,
    supplierCode: resolved?.code ?? null,
    supplierName: resolved?.name ?? null,
    buyerAccountId: null,
    status: po.status,
    totalAmount: po.totalAmount,
    currency: po.currency,
    submittedAt: po.submittedAt,
    acknowledgedAt: po.acknowledgedAt,
    confirmedAt: po.confirmedAt,
    canceledAt: po.canceledAt,
    createdAt: po.createdAt,
    updatedAt: po.updatedAt,
    lines: po.lines,
  };
}

function poFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);

  const detailMatch = pathname.match(new RegExp(`^${PO_PATH}/([^/]+)$`));
  if (detailMatch) {
    const id = decodeURIComponent(detailMatch[1]);
    const found = SCM_POS.find((p) => p.id === id);
    // procurement-api.md § GET /po/{poId} — 404 PO_NOT_FOUND.
    if (!found) return fixtureNotFound('PO_NOT_FOUND', 'purchase order not found');
    return { data: poView(found), meta: { timestamp: SAMPLE_AS_OF } };
  }

  if (pathname !== PO_PATH) return undefined;
  const status = query.get('status');
  // rule 6 — `supplierId` filter matches the STORED value exactly, never the
  // resolved code/name.
  const supplierId = query.get('supplierId');
  const page = intParam(query, 'page', 0);
  const size = intParam(query, 'size', 20);
  let rows = [...SCM_POS];
  if (status) rows = rows.filter((p) => p.status === status);
  if (supplierId) rows = rows.filter((p) => p.supplierId === supplierId);
  const start = page * size;
  return {
    data: {
      content: rows.slice(start, start + size).map(poView),
      page,
      size,
      totalElements: rows.length,
      totalPages: Math.max(1, Math.ceil(rows.length / Math.max(1, size))),
    },
    meta: { timestamp: SAMPLE_AS_OF },
  };
}

// ===========================================================================
// inventory-visibility — nodes (surface `scm`)
//   GET /api/v1/inventory-visibility/nodes
// ===========================================================================

const NODES_PATH = '/api/v1/inventory-visibility/nodes';

interface NodeSeed {
  id: string;
  nodeExternalId: string;
  nodeType: string;
  name: string;
  status: string;
}

/**
 * AC-8 — these are the SAME 3 nodes the hand-typed overview card used to name
 * (`sample-node-01..03` / 평택·이천·부산) BEFORE this ticket, now derived from
 * this ONE registry instead of duplicated in `dashboards.ts`.
 */
export const SCM_NODES: readonly NodeSeed[] = [
  {
    id: 'node-sample-01',
    nodeExternalId: 'PT-DC-01',
    nodeType: 'WMS_WAREHOUSE',
    name: `평택 물류센터${SUFFIX}`,
    status: 'ACTIVE',
  },
  {
    id: 'node-sample-02',
    nodeExternalId: 'IC-DC-01',
    nodeType: 'WMS_WAREHOUSE',
    name: `이천 물류센터${SUFFIX}`,
    status: 'ACTIVE',
  },
  {
    id: 'node-sample-03',
    nodeExternalId: 'BS-PORT-01',
    nodeType: 'THIRD_PARTY_LOGISTICS',
    name: `부산 항만창고${SUFFIX}`,
    status: 'ACTIVE',
  },
];

function nodesFixture(path: string): unknown {
  const { pathname } = splitPath(path);
  if (pathname !== NODES_PATH) return undefined;
  return { data: SCM_NODES, meta: { timestamp: SAMPLE_AS_OF, warning: S5_WARNING } };
}

// ===========================================================================
// inventory-visibility — snapshot (surface `scm`)
//   GET /api/v1/inventory-visibility/snapshot (cross-node list, or a single
//   node's array form via `?nodeId=`)
// ===========================================================================

const SNAPSHOT_PATH = '/api/v1/inventory-visibility/snapshot';

interface SnapshotRowSeed {
  id: string;
  nodeId: string;
  sku: string;
  quantity: number;
  lastEventAt: string | null;
  version: number;
  staleness: string;
}

/** Every `nodeId` here is a real `SCM_NODES` id (AC-8 world consistency).
 *  3 distinct nodes across 4 rows — not vacuous for a "distinct node count"
 *  assertion (287's identical "not vacuous" discipline). */
export const SCM_SNAPSHOT_ROWS: readonly SnapshotRowSeed[] = [
  {
    id: 'snap-sample-0001',
    nodeId: 'node-sample-01',
    sku: 'SKU-SAMPLE-001',
    quantity: 120,
    lastEventAt: '2026-09-14T02:00:00Z',
    version: 3,
    staleness: 'FRESH',
  },
  {
    id: 'snap-sample-0002',
    nodeId: 'node-sample-01',
    sku: 'SKU-SAMPLE-002',
    quantity: 40,
    lastEventAt: '2026-09-14T02:00:00Z',
    version: 2,
    staleness: 'FRESH',
  },
  {
    id: 'snap-sample-0003',
    nodeId: 'node-sample-02',
    sku: 'SKU-SAMPLE-001',
    quantity: 75,
    lastEventAt: '2026-09-10T02:00:00Z',
    version: 5,
    staleness: 'STALE',
  },
  {
    id: 'snap-sample-0004',
    nodeId: 'node-sample-03',
    sku: 'SKU-SAMPLE-003',
    quantity: 10,
    lastEventAt: null,
    version: 1,
    staleness: 'UNREACHABLE',
  },
];

/** Cross-node `meta.staleness` — a coarse rollup (contract only documents the
 *  all-fresh example value; this is the honest generalisation for a mixed
 *  set, tolerated by the consumer's `.optional()` string schema). */
function overallStaleness(rows: readonly SnapshotRowSeed[]): string {
  if (rows.some((r) => r.staleness === 'UNREACHABLE')) return 'PARTIAL_UNREACHABLE';
  if (rows.some((r) => r.staleness === 'STALE')) return 'PARTIAL_STALE';
  return 'ALL_FRESH';
}

function snapshotFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);
  if (pathname !== SNAPSHOT_PATH) return undefined;

  const nodeId = query.get('nodeId');
  if (nodeId) {
    const node = SCM_NODES.find((n) => n.id === nodeId);
    if (!node) return fixtureNotFound('NODE_NOT_FOUND', 'inventory node not found');
    const rows = SCM_SNAPSHOT_ROWS.filter((r) => r.nodeId === nodeId);
    return {
      data: rows,
      meta: {
        timestamp: SAMPLE_AS_OF,
        warning: S5_WARNING,
        nodeId,
        count: rows.length,
        staleness: overallStaleness(rows),
      },
    };
  }

  const page = intParam(query, 'page', 0);
  const size = intParam(query, 'size', 20);
  const start = page * size;
  return {
    data: {
      content: SCM_SNAPSHOT_ROWS.slice(start, start + size),
      page,
      size,
      totalElements: SCM_SNAPSHOT_ROWS.length,
      totalPages: Math.max(1, Math.ceil(SCM_SNAPSHOT_ROWS.length / Math.max(1, size))),
    },
    meta: {
      timestamp: SAMPLE_AS_OF,
      warning: S5_WARNING,
      staleness: overallStaleness(SCM_SNAPSHOT_ROWS),
    },
  };
}

// ===========================================================================
// inventory-visibility — per-SKU breakdown (surface `scm`)
//   GET /api/v1/inventory-visibility/sku/{sku}
// ===========================================================================

const SKU_PATH = '/api/v1/inventory-visibility/sku';

function skuFixture(path: string): unknown {
  const { pathname } = splitPath(path);
  const m = pathname.match(new RegExp(`^${SKU_PATH}/([^/]+)$`));
  if (!m) return undefined;
  const sku = decodeURIComponent(m[1]);
  // An aggregation query, not a lookup-by-id record — an unmatched SKU is a
  // real "no data yet" 200 with an empty breakdown, never a 404 (no
  // `SKU_NOT_FOUND` code exists in inventory-visibility-api.md § Error Codes).
  const rows = SCM_SNAPSHOT_ROWS.filter((r) => r.sku === sku);
  const nodes = rows.map((r) => ({
    nodeId: r.nodeId,
    quantity: r.quantity,
    staleness: r.staleness,
  }));
  const totalQuantity = rows.reduce((sum, r) => sum + r.quantity, 0);
  return {
    data: { sku, nodes, totalQuantity },
    meta: { timestamp: SAMPLE_AS_OF, warning: S5_WARNING },
  };
}

// ===========================================================================
// inventory-visibility — staleness (surface `scm`)
//   GET /api/v1/inventory-visibility/staleness
// ===========================================================================

const STALENESS_PATH = '/api/v1/inventory-visibility/staleness';

function stalenessFixture(path: string): unknown {
  const { pathname } = splitPath(path);
  if (pathname !== STALENESS_PATH) return undefined;
  const rows = SCM_NODES.map((n) => {
    const nodeRows = SCM_SNAPSHOT_ROWS.filter((r) => r.nodeId === n.id);
    const worst = nodeRows.some((r) => r.staleness === 'UNREACHABLE')
      ? 'UNREACHABLE'
      : nodeRows.some((r) => r.staleness === 'STALE')
        ? 'STALE'
        : 'FRESH';
    const lastEventAt =
      nodeRows
        .map((r) => r.lastEventAt)
        .filter((v): v is string => v !== null)
        .sort()
        .at(-1) ?? null;
    return {
      nodeId: n.id,
      stalenessStatus: worst,
      lastEventAt,
      lastCheckedAt: SAMPLE_AS_OF,
    };
  });
  return { data: rows, meta: { timestamp: SAMPLE_AS_OF, warning: S5_WARNING } };
}

// ===========================================================================
// scm surface — dispatch (PO + inventory-visibility, ALL under the single
// `flat:scm` fixture key — see `scm-client.ts`'s `logPrefix: 'scm'`)
// ===========================================================================

function scmFixture(path: string): unknown {
  return (
    poFixture(path) ??
    nodesFixture(path) ??
    snapshotFixture(path) ??
    skuFixture(path) ??
    stalenessFixture(path)
  );
}

// ===========================================================================
// scm_replenishment — demand-planning suggestions read
//   GET /api/v1/demand-planning/suggestions (+ /{id})
// ===========================================================================

const SUGGESTIONS_PATH = '/api/v1/demand-planning/suggestions';

interface SuggestionSeed {
  id: string;
  skuCode: string;
  warehouseId: string | null;
  warehouseCode: string | null;
  supplierId: string | null;
  suggestedQty: number;
  status: string;
  source: string;
  triggerAvailableQty: number | null;
  materializedPoId: string | null;
  createdAt: string;
}

/**
 * `sugg-sample-0001/0002/0004` carry a real supplier CODE (`SUP-SAMPLE-0*`,
 * matching `SUPPLIERS` above — `ReplenishmentTable`'s `supplierCodeRef` shows
 * it as a plain code, this surface has no name field to join against).
 * `sugg-sample-0003` is the BATCH-sourced edge case: no `warehouseCode`
 * (`SuggestionSchema`'s own nullable comment — BATCH-origin rows predate the
 * code backfill) AND a UUID-SHAPED `supplierId` matching no known code — the
 * production `supplierCodeRef` treats that as unresolved (`이름 확인 불가`,
 * `TASK-MONO-683`). Its `materializedPoId` points at a REAL PO in `SCM_POS`
 * (`po-sample-0002`) — the suggestion that was approved into that DRAFT PO.
 */
const SCM_SUGGESTIONS: readonly SuggestionSeed[] = [
  {
    id: 'sugg-sample-0001',
    skuCode: 'SKU-SAMPLE-001',
    warehouseId: 'wh-sample-scm-0001',
    warehouseCode: 'WH01',
    supplierId: SUPPLIERS[0].code,
    suggestedQty: 50,
    status: 'SUGGESTED',
    source: 'ALERT',
    triggerAvailableQty: 5,
    materializedPoId: null,
    createdAt: '2026-09-13T00:00:00Z',
  },
  {
    id: 'sugg-sample-0002',
    skuCode: 'SKU-SAMPLE-002',
    warehouseId: 'wh-sample-scm-0001',
    warehouseCode: 'WH01',
    supplierId: SUPPLIERS[1].code,
    suggestedQty: 30,
    status: 'APPROVED',
    source: 'ALERT',
    triggerAvailableQty: 8,
    materializedPoId: null,
    createdAt: '2026-09-12T00:00:00Z',
  },
  {
    id: 'sugg-sample-0003',
    skuCode: 'SKU-SAMPLE-003',
    warehouseId: 'wh-sample-scm-0002',
    warehouseCode: null,
    supplierId: '33333333-3333-4333-8333-333333333333',
    suggestedQty: 15,
    status: 'MATERIALIZED',
    source: 'BATCH',
    triggerAvailableQty: 2,
    materializedPoId: 'po-sample-0002',
    createdAt: '2026-09-08T00:00:00Z',
  },
  {
    id: 'sugg-sample-0004',
    skuCode: 'SKU-SAMPLE-001',
    warehouseId: 'wh-sample-scm-0001',
    warehouseCode: 'WH01',
    supplierId: SUPPLIERS[0].code,
    suggestedQty: 25,
    status: 'DISMISSED',
    source: 'ALERT',
    triggerAvailableQty: 6,
    materializedPoId: null,
    createdAt: '2026-09-01T00:00:00Z',
  },
];

function replenishmentFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);

  const detailMatch = pathname.match(new RegExp(`^${SUGGESTIONS_PATH}/([^/]+)$`));
  if (detailMatch) {
    const id = decodeURIComponent(detailMatch[1]);
    const found = SCM_SUGGESTIONS.find((s) => s.id === id);
    if (!found) return fixtureNotFound('SUGGESTION_NOT_FOUND', 'suggestion not found');
    return { data: found, meta: { timestamp: SAMPLE_AS_OF } };
  }

  if (pathname !== SUGGESTIONS_PATH) return undefined;
  const status = query.get('status');
  const skuCode = query.get('skuCode');
  const page = intParam(query, 'page', 0);
  const size = intParam(query, 'size', 20);
  let rows = [...SCM_SUGGESTIONS];
  if (status) rows = rows.filter((s) => s.status === status);
  if (skuCode) rows = rows.filter((s) => s.skuCode === skuCode);
  const start = page * size;
  return {
    data: rows.slice(start, start + size),
    meta: {
      page,
      size,
      totalElements: rows.length,
      totalPages: Math.max(1, Math.ceil(rows.length / Math.max(1, size))),
    },
  };
}

// ===========================================================================
// scm_config — demand-planning per-SKU reorder-policy + sku-supplier-map seed
//   GET /api/v1/demand-planning/policies/{skuCode}
//   GET /api/v1/demand-planning/sku-supplier-map/{skuCode}
// ===========================================================================

const POLICIES_PATH = '/api/v1/demand-planning/policies';
const SUPPLIER_MAP_PATH = '/api/v1/demand-planning/sku-supplier-map';

interface ReorderPolicySeed {
  reorderPoint: number;
  safetyStock: number;
  reorderQty: number;
}

interface SupplierMapSeed {
  supplierId: string;
  defaultOrderQty: number;
  leadTimeDays: number;
  currency: string;
}

/**
 * AC-4 — `SKU-SAMPLE-001`/`SKU-SAMPLE-002` are fully configured (both rows);
 * `SKU-SAMPLE-003` is DELIBERATELY absent from BOTH maps — the same SKU whose
 * replenishment suggestion (`sugg-sample-0003`) already tells the "not fully
 * configured yet" story (no `warehouseCode`, an unresolved supplier). A
 * lookup on it (or on any other SKU code) 404s `POLICY_NOT_FOUND` /
 * `MAPPING_NOT_FOUND`, which `getPolicy`/`getSupplierMap`'s `notFoundIsEmpty`
 * turns into the screen's `{found:false}` "not configured yet" empty state
 * (`PolicyForm`/`SupplierMapForm`'s `*-not-configured` testid).
 */
const POLICIES: Readonly<Record<string, ReorderPolicySeed>> = {
  'SKU-SAMPLE-001': { reorderPoint: 10, safetyStock: 5, reorderQty: 100 },
  'SKU-SAMPLE-002': { reorderPoint: 20, safetyStock: 10, reorderQty: 50 },
};

const SUPPLIER_MAPS: Readonly<Record<string, SupplierMapSeed>> = {
  'SKU-SAMPLE-001': {
    supplierId: SUPPLIERS[0].code,
    defaultOrderQty: 100,
    leadTimeDays: 7,
    currency: 'KRW',
  },
  'SKU-SAMPLE-002': {
    supplierId: SUPPLIERS[1].code,
    defaultOrderQty: 50,
    leadTimeDays: 5,
    currency: 'KRW',
  },
};

function policyFixture(path: string): unknown {
  const { pathname } = splitPath(path);
  const m = pathname.match(new RegExp(`^${POLICIES_PATH}/([^/]+)$`));
  if (!m) return undefined;
  const skuCode = decodeURIComponent(m[1]);
  const policy = POLICIES[skuCode];
  if (!policy) return fixtureNotFound('POLICY_NOT_FOUND', 'reorder policy not configured');
  return { data: policy, meta: { timestamp: SAMPLE_AS_OF } };
}

function supplierMapFixture(path: string): unknown {
  const { pathname } = splitPath(path);
  const m = pathname.match(new RegExp(`^${SUPPLIER_MAP_PATH}/([^/]+)$`));
  if (!m) return undefined;
  const skuCode = decodeURIComponent(m[1]);
  const map = SUPPLIER_MAPS[skuCode];
  if (!map) return fixtureNotFound('MAPPING_NOT_FOUND', 'sku-supplier mapping not configured');
  return { data: map, meta: { timestamp: SAMPLE_AS_OF } };
}

function configFixture(path: string): unknown {
  return policyFixture(path) ?? supplierMapFixture(path);
}

// ===========================================================================
// exports
// ===========================================================================

export const SCM_FIXTURE_HANDLERS: Readonly<Record<string, ScmFixtureHandler>> = {
  'flat:scm': scmFixture,
  'flat:scm_replenishment': replenishmentFixture,
  'flat:scm_config': configFixture,
};

/**
 * The AGGREGATE of every seed array/map per surface (not just one branch's
 * output), for the R2ⓐ label guard — same reasoning as `iam.ts`/`erp.ts`.
 * `purchaseOrders` is the RESOLVED view (`poView`), not the raw `SCM_POS`
 * seed — the resolved `supplierName` string (human-readable, suffixed) only
 * exists after resolution, and the guard must see it.
 */
export const SCM_FIXTURE_DOCUMENTS: Readonly<Record<string, unknown>> = {
  'flat:scm': {
    purchaseOrders: SCM_POS.map(poView),
    nodes: SCM_NODES,
    snapshot: SCM_SNAPSHOT_ROWS,
    warning: S5_WARNING,
  },
  'flat:scm_replenishment': { suggestions: SCM_SUGGESTIONS },
  'flat:scm_config': { policies: POLICIES, supplierMaps: SUPPLIER_MAPS },
};
