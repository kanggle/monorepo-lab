/**
 * wms domain sample fixtures parse with the SAME production schemas the real
 * screens use, and behave like the real producer over filters/detail lookups
 * (TASK-PC-FE-287 AC-0…AC-6). Every assertion goes THROUGH `sampleResponse`
 * (never the raw seed arrays inside `fixtures/wms.ts`) — routing, status
 * codes and query-string handling are exercised too, not just data shape.
 *
 * 🔴🔴 AC-3 / TASK-MONO-675 — the live defect this ticket must neither hide
 *    nor reproduce is "the field is present but every code is null" (empty
 *    master-ref tables). This suite proves the OPPOSITE for the sample world:
 *    every inventory row's `locationCode`/`skuCode`/`lotNo`/`warehouseCode`
 *    resolves to a REAL row on `/dashboard/refs/{type}` — fetched through the
 *    router, never by re-reading the fixture's internal arrays (that would
 *    test the test, not the screen).
 * 🔴🔴 Cross-view — the coordinator's prior reviews (284/285/286) each found
 *    the SAME defect class: a screen that looks right alone but contradicts a
 *    sibling. This domain has THREE producers describing the same four
 *    orders (admin read-model, outbound-service, logistics dispatch); every
 *    invariant below crosses that boundary through the router.
 * 🔴 AC-4 — wms is the NESTED error envelope (`callWmsGateway`/`parseWmsError`)
 *    for the `wms`/`wms_outbound` surfaces, but `wms_outbound_logistics`
 *    reaches its producer via `callScmGateway` → `callFlatEnvelopeGateway`
 *    (core `flat`, FLAT envelope) — this suite pins BOTH shapes AND the
 *    `coverage.ts` core-field fix that makes the logistics surface reachable
 *    at all (see `fixtures/wms.ts`'s module header).
 */
import { describe, it, expect } from 'vitest';
import { sampleResponse } from '@/shared/sample/router';
import { findSurfaceCoverage } from '@/shared/sample/coverage';
import { SAMPLE_READ_ONLY, SAMPLE_NOT_READY } from '@/shared/sample/codes';
import { messageForCode } from '@/shared/api/errors';
import { readWmsLagHeader } from '@/shared/api/wms-gateway';
import {
  InventoryPageSchema,
  InventoryRowSchema,
  RefPageSchema,
  ThroughputSchema,
  OrderPageSchema,
  ShipmentPageSchema,
  AsnPageSchema,
  InspectionSchema,
  AdjustmentPageSchema,
  AlertPageSchema,
  SettingPageSchema,
  SettingSchema,
  ProjectionStatusSchema,
} from '@/features/wms-ops/api/types';
import {
  OutboundOrderPageSchema,
  OutboundOrderDetailSchema,
  OutboundSagaSchema,
  PickingRequestListSchema,
  DispatchRefSchema,
} from '@/features/wms-outbound-ops/api/types';

function wms(surface: string, path: string): Response {
  return sampleResponse({ core: 'wms', surface, method: 'GET', path });
}
const admin = (path: string) => wms('wms', path);
const outbound = (path: string) => wms('wms_outbound', path);
function logistics(path: string): Response {
  return sampleResponse({ core: 'flat', surface: 'wms_outbound_logistics', method: 'GET', path });
}

async function json(res: Response): Promise<unknown> {
  return res.json();
}

const ORDER_IDS = [
  'ord-sample-0001',
  'ord-sample-0002',
  'ord-sample-0003',
  'ord-sample-0004',
] as const;

// ---------------------------------------------------------------------------
// AC-0 — re-inventory (recorded in the task file's Implementation notes; this
// suite is the executable half: every GET this cell names is exercised below)
// ---------------------------------------------------------------------------

describe('AC-0 — ledger rows exist and are ready', () => {
  it('wms / wms_outbound / (flat) wms_outbound_logistics are all ready', () => {
    expect(findSurfaceCoverage('wms', 'wms')?.status).toBe('ready');
    expect(findSurfaceCoverage('wms', 'wms_outbound')?.status).toBe('ready');
    expect(findSurfaceCoverage('flat', 'wms_outbound_logistics')?.status).toBe('ready');
  });

  it('🔴🔴 wms_outbound_logistics is core=flat, NOT core=wms — the coverage.ts fix this ticket makes', () => {
    // `outbound-logistics-api.ts`'s `LOGISTICS_PROFILE` reaches this surface
    // via `callScmGateway` → `callFlatEnvelopeGateway`, which asks
    // `sampleGate({core:'flat', …})` — `callWmsGateway` is never in this call
    // path. A `core:'wms'` ledger row can NEVER match that real request
    // (`findSurfaceCoverage` compares both fields), so it would 503 forever
    // regardless of `status`. `sample-coverage-ledger.test.ts`'s generic
    // "ledger promises what the router does" check cannot catch this — it
    // always calls `sampleResponse` with the row's OWN `core` field, so a
    // self-consistently-wrong row still "agrees with itself".
    expect(findSurfaceCoverage('wms', 'wms_outbound_logistics')).toBeUndefined();
  });
});

// ---------------------------------------------------------------------------
// AC-1 — every GET parses with the REAL production zod schema
// ---------------------------------------------------------------------------

describe('AC-1 — wms admin read-model (surface `wms`) parses with production schemas', () => {
  it('GET /dashboard/inventory (list)', async () => {
    const res = admin('/api/v1/admin/dashboard/inventory?page=0&size=20');
    expect(res.status).toBe(200);
    const page = InventoryPageSchema.parse(await json(res));
    expect(page.content.length).toBeGreaterThan(0);
  });

  it('GET /dashboard/inventory/by-key', async () => {
    const res = admin(
      '/api/v1/admin/dashboard/inventory/by-key?locationId=loc-sample-0001&skuId=sku-sample-0001&lotId=lot-sample-0001',
    );
    expect(res.status).toBe(200);
    const row = InventoryRowSchema.parse(await json(res));
    expect(row.locationId).toBe('loc-sample-0001');
  });

  it('GET /dashboard/inventory/by-key — unknown key → 404 NOT_FOUND (nested envelope)', async () => {
    const res = admin(
      '/api/v1/admin/dashboard/inventory/by-key?locationId=loc-none&skuId=sku-none',
    );
    expect(res.status).toBe(404);
    const body = (await json(res)) as { error: { code: string } };
    expect(body.error.code).toBe('NOT_FOUND');
  });

  it('GET /dashboard/throughput', async () => {
    const res = admin(
      '/api/v1/admin/dashboard/throughput?warehouseId=wh-sample-0001&from=2026-09-01&to=2026-09-15',
    );
    expect(res.status).toBe(200);
    ThroughputSchema.parse(await json(res));
  });

  it('GET /dashboard/orders', async () => {
    const res = admin('/api/v1/admin/dashboard/orders?page=0&size=20');
    expect(res.status).toBe(200);
    const page = OrderPageSchema.parse(await json(res));
    expect(page.content.length).toBe(4);
  });

  it('GET /dashboard/shipments', async () => {
    const res = admin('/api/v1/admin/dashboard/shipments?page=0&size=20');
    expect(res.status).toBe(200);
    const page = ShipmentPageSchema.parse(await json(res));
    expect(page.content.length).toBe(1);
  });

  it('GET /dashboard/asns (list) + /dashboard/asns/{id}/inspection (both branches)', async () => {
    const listRes = admin('/api/v1/admin/dashboard/asns?page=0&size=20');
    expect(listRes.status).toBe(200);
    const page = AsnPageSchema.parse(await json(listRes));
    expect(page.content.length).toBe(2);

    const closedAsn = page.content.find((a) => a.status === 'CLOSED')!;
    const inspectedRes = admin(
      `/api/v1/admin/dashboard/asns/${closedAsn.asnId}/inspection`,
    );
    expect(inspectedRes.status).toBe(200);
    InspectionSchema.parse(await json(inspectedRes));

    const openAsn = page.content.find((a) => a.status !== 'CLOSED')!;
    const notYetRes = admin(`/api/v1/admin/dashboard/asns/${openAsn.asnId}/inspection`);
    expect(notYetRes.status).toBe(404);
    const notYetBody = (await json(notYetRes)) as { error: { code: string } };
    expect(notYetBody.error.code).toBe('NOT_FOUND');
  });

  it('GET /dashboard/adjustments', async () => {
    const res = admin('/api/v1/admin/dashboard/adjustments?page=0&size=20');
    expect(res.status).toBe(200);
    const page = AdjustmentPageSchema.parse(await json(res));
    expect(page.content.length).toBeGreaterThan(0);
  });

  it('GET /dashboard/alerts', async () => {
    const res = admin('/api/v1/admin/dashboard/alerts?page=0&size=20');
    expect(res.status).toBe(200);
    const page = AlertPageSchema.parse(await json(res));
    expect(page.content.length).toBeGreaterThan(0);
  });

  it('GET /dashboard/refs/{type} for all 6 types', async () => {
    for (const type of ['warehouses', 'zones', 'locations', 'skus', 'lots', 'partners']) {
      const res = admin(`/api/v1/admin/dashboard/refs/${type}?page=0&size=20`);
      expect(res.status, type).toBe(200);
      const page = RefPageSchema.parse(await json(res));
      expect(page.content.length, type).toBeGreaterThan(0);
    }
  });

  it('GET /settings (list) + /settings/{key} (single)', async () => {
    const listRes = admin('/api/v1/admin/settings?page=0&size=20');
    expect(listRes.status).toBe(200);
    const page = SettingPageSchema.parse(await json(listRes));
    expect(page.content.length).toBe(2);

    const key = page.content[0].key;
    const singleRes = admin(`/api/v1/admin/settings/${encodeURIComponent(key)}`);
    expect(singleRes.status).toBe(200);
    SettingSchema.parse(await json(singleRes));
  });

  it('GET /settings/{key} — unknown key → 404 SETTING_NOT_FOUND', async () => {
    const res = admin('/api/v1/admin/settings/no.such.key');
    expect(res.status).toBe(404);
    const body = (await json(res)) as { error: { code: string } };
    expect(body.error.code).toBe('SETTING_NOT_FOUND');
  });

  it('GET /operations/projection-status', async () => {
    const res = admin('/api/v1/admin/operations/projection-status');
    expect(res.status).toBe(200);
    ProjectionStatusSchema.parse(await json(res));
  });
});

describe('AC-1 — wms outbound-service order lifecycle (surface `wms_outbound`) parses with production schemas', () => {
  it('GET /orders (list)', async () => {
    const res = outbound('/api/v1/outbound/orders?page=0&size=20');
    expect(res.status).toBe(200);
    const page = OutboundOrderPageSchema.parse(await json(res));
    expect(page.content.length).toBe(4);
  });

  it('GET /orders/{id} (detail) for every order', async () => {
    for (const id of ORDER_IDS) {
      const res = outbound(`/api/v1/outbound/orders/${id}`);
      expect(res.status, id).toBe(200);
      OutboundOrderDetailSchema.parse(await json(res));
    }
  });

  it('GET /orders/{id} — unknown id → 404 ORDER_NOT_FOUND (nested envelope)', async () => {
    const res = outbound('/api/v1/outbound/orders/ord-does-not-exist');
    expect(res.status).toBe(404);
    const body = (await json(res)) as { error: { code: string } };
    expect(body.error.code).toBe('ORDER_NOT_FOUND');
  });

  it('GET /orders/{id}/saga for every order', async () => {
    for (const id of ORDER_IDS) {
      const res = outbound(`/api/v1/outbound/orders/${id}/saga`);
      expect(res.status, id).toBe(200);
      const saga = OutboundSagaSchema.parse(await json(res));
      expect(saga.orderId).toBe(id);
    }
  });

  it('GET /orders/{id}/picking-requests — populated AND the documented empty-array case', async () => {
    const populated = outbound('/api/v1/outbound/orders/ord-sample-0001/picking-requests');
    expect(populated.status).toBe(200);
    const populatedList = PickingRequestListSchema.parse(await json(populated));
    expect(populatedList.content.length).toBe(1);

    // ord-sample-0004 is CANCELLED before the saga reached RESERVED — the
    // contract's own documented `{content: []}` case (§ outbound-service-api.md
    // § 2.4), not 404.
    const empty = outbound('/api/v1/outbound/orders/ord-sample-0004/picking-requests');
    expect(empty.status).toBe(200);
    const emptyList = PickingRequestListSchema.parse(await json(empty));
    expect(emptyList.content).toEqual([]);
  });
});

describe('AC-1 — wms_outbound_logistics (flat envelope) parses with production schema', () => {
  it('GET /dispatches/by-shipment/{shipmentId}', async () => {
    const res = logistics('/api/v1/logistics/dispatches/by-shipment/ship-sample-0001');
    expect(res.status).toBe(200);
    const body = (await json(res)) as { data: unknown };
    const dispatch = DispatchRefSchema.parse(body.data);
    expect(dispatch.shipmentId).toBe('ship-sample-0001');
  });

  it('GET /dispatches/by-shipment/{shipmentId} — unknown shipment → 404 DISPATCH_NOT_FOUND (FLAT envelope)', async () => {
    const res = logistics('/api/v1/logistics/dispatches/by-shipment/ship-none');
    expect(res.status).toBe(404);
    const body = (await json(res)) as { code: string };
    expect(body.code).toBe('DISPATCH_NOT_FOUND');
  });
});

// ---------------------------------------------------------------------------
// AC-3 / TASK-MONO-675 — inventory codes resolve inside the master fixtures,
// through the router (never null, never a duplicated literal)
// ---------------------------------------------------------------------------

describe('AC-3 — inventory row codes resolve to a REAL master-ref row (through the router)', () => {
  it('every inventory row’s locationCode/skuCode/lotNo/warehouseCode is non-null and matches a real ref row', async () => {
    const invRes = admin('/api/v1/admin/dashboard/inventory?page=0&size=20');
    const invPage = InventoryPageSchema.parse(await json(invRes));
    expect(invPage.content.length).toBeGreaterThan(0);

    const locsRes = admin('/api/v1/admin/dashboard/refs/locations?page=0&size=20');
    const skusRes = admin('/api/v1/admin/dashboard/refs/skus?page=0&size=20');
    const lotsRes = admin('/api/v1/admin/dashboard/refs/lots?page=0&size=20');
    const warehousesRes = admin('/api/v1/admin/dashboard/refs/warehouses?page=0&size=20');
    const locs = RefPageSchema.parse(await json(locsRes)).content as { id: string; locationCode: string }[];
    const skus = RefPageSchema.parse(await json(skusRes)).content as { id: string; skuCode: string }[];
    const lots = RefPageSchema.parse(await json(lotsRes)).content as { id: string; lotNo: string }[];
    const warehouses = RefPageSchema.parse(await json(warehousesRes)).content as {
      id: string;
      warehouseCode: string;
    }[];

    for (const row of invPage.content) {
      expect(row.locationCode, row.locationId).not.toBeNull();
      expect(row.skuCode, row.skuId).not.toBeNull();
      expect(row.warehouseCode, row.warehouseId).not.toBeNull();

      const loc = locs.find((l) => l.id === row.locationId);
      const sku = skus.find((s) => s.id === row.skuId);
      const wh = warehouses.find((w) => w.id === row.warehouseId);
      expect(loc, `locationId ${row.locationId} must resolve`).toBeDefined();
      expect(sku, `skuId ${row.skuId} must resolve`).toBeDefined();
      expect(wh, `warehouseId ${row.warehouseId} must resolve`).toBeDefined();
      expect(row.locationCode).toBe(loc!.locationCode);
      expect(row.skuCode).toBe(sku!.skuCode);
      expect(row.warehouseCode).toBe(wh!.warehouseCode);

      if (row.lotId) {
        expect(row.lotNo).not.toBeNull();
        const lot = lots.find((l) => l.id === row.lotId);
        expect(lot, `lotId ${row.lotId} must resolve`).toBeDefined();
        expect(row.lotNo).toBe(lot!.lotNo);
      }
    }
  });

  it('outbound order lines also carry a resolved (non-null) skuCode', async () => {
    const skusRes = admin('/api/v1/admin/dashboard/refs/skus?page=0&size=20');
    const skus = RefPageSchema.parse(await json(skusRes)).content as { id: string; skuCode: string }[];
    for (const id of ORDER_IDS) {
      const res = outbound(`/api/v1/outbound/orders/${id}`);
      const detail = OutboundOrderDetailSchema.parse(await json(res));
      for (const line of detail.lines) {
        expect(line.skuCode, `${id} line ${line.orderLineId}`).not.toBeNull();
        const sku = skus.find((s) => s.id === line.skuId);
        expect(sku, `skuId ${line.skuId} must resolve`).toBeDefined();
        expect(line.skuCode).toBe(sku!.skuCode);
      }
    }
  });
});

// ---------------------------------------------------------------------------
// AC-4 — SAMPLE_READ_ONLY / SAMPLE_NOT_READY survive the correct envelope
// ---------------------------------------------------------------------------

describe('AC-4 — SAMPLE_READ_ONLY / SAMPLE_NOT_READY preserve the code through the RIGHT envelope', () => {
  it('wms (NESTED) — any write → 403 SAMPLE_READ_ONLY, code readable at error.code', async () => {
    const write = sampleResponse({
      core: 'wms',
      surface: 'wms',
      method: 'POST',
      path: '/api/v1/admin/dashboard/alerts/alert-sample-0001/acknowledge',
    });
    expect(write.status).toBe(403);
    const body = (await json(write)) as { error: { code: string; message: string } };
    expect(body.error.code).toBe(SAMPLE_READ_ONLY);
    expect(messageForCode(body.error.code)).toBe(
      '샘플 화면에서는 실행되지 않습니다. 로그인하면 실제로 실행됩니다',
    );
  });

  it('wms_outbound (NESTED) — any write → 403 SAMPLE_READ_ONLY', async () => {
    const write = sampleResponse({
      core: 'wms',
      surface: 'wms_outbound',
      method: 'POST',
      path: '/api/v1/outbound/orders/ord-sample-0002/shipments',
    });
    expect(write.status).toBe(403);
    const body = (await json(write)) as { error: { code: string } };
    expect(body.error.code).toBe(SAMPLE_READ_ONLY);
  });

  it('wms_outbound_logistics (FLAT) — any write → 403 SAMPLE_READ_ONLY, code at top level (NOT nested)', async () => {
    const write = sampleResponse({
      core: 'flat',
      surface: 'wms_outbound_logistics',
      method: 'POST',
      path: '/api/v1/logistics/dispatches/dispatch-sample-0001:retry',
    });
    expect(write.status).toBe(403);
    const body = (await json(write)) as { code: string; error?: unknown };
    expect(body.code).toBe(SAMPLE_READ_ONLY);
    // 🔴 bite target — a NESTED envelope here would put the code at
    // `body.error.code` and leave `body.code` undefined; `parseFlatEnvelopeError`
    // (the REAL parser this surface's producer client uses) only ever reads
    // the top-level field.
    expect(body.error).toBeUndefined();
  });

  it('a pending-shaped path on a genuinely-unimplemented surface still 503s SAMPLE_NOT_READY through the NESTED envelope', async () => {
    const res = sampleResponse({
      core: 'wms',
      surface: 'no-such-wms-surface',
      method: 'GET',
      path: '/anything',
    });
    expect(res.status).toBe(503);
    const body = (await json(res)) as { error: { code: string } };
    expect(body.error.code).toBe(SAMPLE_NOT_READY);
  });
});

// ---------------------------------------------------------------------------
// AC-5 — no read-model-lag header in sample mode; lagSeconds computes to null
// ---------------------------------------------------------------------------

describe('AC-5 — X-Read-Model-Lag-Seconds is absent; lagSeconds computes to null (no false warning)', () => {
  it('a ready GET carries no lag header', () => {
    const res = admin('/api/v1/admin/dashboard/inventory?page=0&size=20');
    expect(res.headers.get('X-Read-Model-Lag-Seconds')).toBeNull();
    expect(readWmsLagHeader(res)).toBeNull();
  });

  it('the section-state lagSeconds formula (mirrors inventory-state.ts) resolves to null, never a false banner', () => {
    const res = admin('/api/v1/admin/dashboard/inventory?page=0&size=20');
    const lag = readWmsLagHeader(res);
    // Same formula `inventory-state.ts` / `wms-state.ts` / … apply to the
    // gateway's `lagSeconds` before handing it to `WmsLagHint` (renders
    // nothing when the message is falsy — `message: string | null`).
    const sectionLagSeconds = lag && lag > 0 ? lag : null;
    expect(sectionLagSeconds).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// AC-6 — a representative write (출고 확정 = confirmShipping) is rejected
// ---------------------------------------------------------------------------

describe('AC-6 — 출고 확정 (confirmShipping) is rejected with the visitor-facing copy', () => {
  it('POST /orders/{id}/shipments → 403 SAMPLE_READ_ONLY → "샘플 화면에서는 실행되지 않습니다"', async () => {
    const res = sampleResponse({
      core: 'wms',
      surface: 'wms_outbound',
      method: 'POST',
      path: '/api/v1/outbound/orders/ord-sample-0002/shipments',
    });
    expect(res.status).toBe(403);
    const body = (await json(res)) as { error: { code: string } };
    expect(body.error.code).toBe(SAMPLE_READ_ONLY);
    expect(messageForCode(body.error.code)).toContain('샘플 화면에서는 실행되지 않습니다');
  });
});

// ---------------------------------------------------------------------------
// Edge Case — outbound per-call baseUrl override; the router matches PATH,
// not base (`resolveShipmentIdForOrder` overrides baseUrl to WMS_ADMIN_BASE_URL
// but keeps the `wms_outbound` surface/logPrefix)
// ---------------------------------------------------------------------------

describe('Edge Case — outbound per-call baseUrl override matches by PATH', () => {
  it('the wms_outbound surface answers the admin-shaped /dashboard/shipments?orderId= path', async () => {
    const res = outbound('/api/v1/admin/dashboard/shipments?orderId=ord-sample-0003&size=1');
    expect(res.status).toBe(200);
    const page = ShipmentPageSchema.parse(await json(res));
    expect(page.content.length).toBe(1);
    expect(page.content[0].shipmentId).toBe('ship-sample-0001');
    expect(page.content[0].orderId).toBe('ord-sample-0003');
  });

  it('bite target — removing the admin-shaped branch would 503 this exact real-production call shape', async () => {
    // Sanity check that the SAME path under the WRONG surface (`wms`, not
    // `wms_outbound`) also answers — proving both the admin screen's own
    // shipments read AND the outbound resolver's override see the SAME data
    // (cross-view, not two independent fixtures that happen to agree).
    const viaAdmin = admin('/api/v1/admin/dashboard/shipments?orderId=ord-sample-0003&size=1');
    const viaOutbound = outbound('/api/v1/admin/dashboard/shipments?orderId=ord-sample-0003&size=1');
    expect(viaAdmin.status).toBe(200);
    expect(viaOutbound.status).toBe(200);
    const a = ShipmentPageSchema.parse(await json(viaAdmin));
    const b = ShipmentPageSchema.parse(await json(viaOutbound));
    expect(a.content).toEqual(b.content);
  });
});

// ---------------------------------------------------------------------------
// Cross-view — the SAME four orders, told consistently by THREE producers
// ---------------------------------------------------------------------------

describe('Cross-view — admin read-model orders agree with outbound-service orders (through the router)', () => {
  it('admin dashboard/orders collapses each REAL outbound status to RECEIVED/SHIPPED/CANCELLED, never a 4th value', async () => {
    const outboundRes = outbound('/api/v1/outbound/orders?page=0&size=20');
    const outboundPage = OutboundOrderPageSchema.parse(await json(outboundRes));
    const adminRes = admin('/api/v1/admin/dashboard/orders?page=0&size=20');
    const adminPage = OrderPageSchema.parse(await json(adminRes));

    expect(adminPage.content.length).toBe(outboundPage.content.length);
    for (const outboundOrder of outboundPage.content) {
      const adminOrder = adminPage.content.find((o) => o.orderId === outboundOrder.orderId) as
        | { status: string }
        | undefined;
      expect(adminOrder, outboundOrder.orderId).toBeDefined();
      if (outboundOrder.status === 'SHIPPED') {
        expect(adminOrder!.status).toBe('SHIPPED');
      } else if (outboundOrder.status === 'CANCELLED') {
        expect(adminOrder!.status).toBe('CANCELLED');
      } else {
        // PICKING / PICKED / PACKING / PACKED all collapse to RECEIVED.
        expect(adminOrder!.status).toBe('RECEIVED');
      }
    }
  });

  it('🔴 not vacuous — "미출고 주문" (status=RECEIVED) is a STRICT SUBSET, matching the overview tile’s own query', async () => {
    const openRes = admin(
      '/api/v1/admin/dashboard/orders?warehouseId=wh-sample-0001&status=RECEIVED&page=0&size=1',
    );
    const openPage = OrderPageSchema.parse(await json(openRes));
    const allRes = admin('/api/v1/admin/dashboard/orders?page=0&size=20');
    const allPage = OrderPageSchema.parse(await json(allRes));
    expect(openPage.page.totalElements).toBeGreaterThan(0);
    expect(allPage.content.length).toBeGreaterThan(openPage.page.totalElements);
    expect(openPage.page.totalElements).toBe(2);
  });

  it('the SHIPPED order’s shipment (admin) references the SAME order as the dispatch (logistics)', async () => {
    const shipRes = admin('/api/v1/admin/dashboard/shipments?page=0&size=20');
    const shipPage = ShipmentPageSchema.parse(await json(shipRes));
    expect(shipPage.content.length).toBe(1);
    const shipment = shipPage.content[0];

    const dispatchRes = logistics(
      `/api/v1/logistics/dispatches/by-shipment/${shipment.shipmentId}`,
    );
    expect(dispatchRes.status).toBe(200);
    const dispatchBody = (await dispatchRes.json()) as { data: { shipmentId: string } };
    expect(dispatchBody.data.shipmentId).toBe(shipment.shipmentId);

    const orderRes = outbound(`/api/v1/outbound/orders/${shipment.orderId}`);
    const order = OutboundOrderDetailSchema.parse(await orderRes.json());
    expect(order.status).toBe('SHIPPED');
    expect(order.orderNo).toBe(shipment.orderNo);
  });
});
