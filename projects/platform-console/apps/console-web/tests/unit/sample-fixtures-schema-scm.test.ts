/**
 * scm domain sample fixtures parse with the SAME production schemas the real
 * screens use, and behave like the real producer over filters/detail lookups
 * (TASK-PC-FE-288 AC-0…AC-6, AC-8 partial — the LAST domain ticket of the
 * `ADR-MONO-074` execution series: after this, no surface anywhere is
 * `pending`). Every assertion goes THROUGH `sampleResponse` (never the raw
 * seed arrays inside `fixtures/scm.ts`) — routing, status codes and
 * query-string handling are exercised too, not just data shape.
 *
 * 🔴🔴 AC-3 / TASK-MONO-677 — the live defect this ticket must neither hide
 *    nor reproduce is a raw supplier UUID painted on `/scm/procurement`. This
 *    suite proves the resolution rule `procurement-api.md` §
 *    `PurchaseOrderResponse` — supplier reference fields actually specifies:
 *    id match first, then code match, else BOTH fields `null` (never the raw
 *    id) — through the router, using the SAME `masterRefLabel` helper the
 *    real screen renders with.
 * 🔴 AC-4 — the 404-as-empty seed lookups (`POLICY_NOT_FOUND` /
 *    `MAPPING_NOT_FOUND`) must 404 for an unconfigured SKU so the client's
 *    `notFoundIsEmpty` short-circuit renders "not configured yet", not a
 *    thrown error.
 * 🔴 AC-5 — no `X-Cache` header on any sample response (a false freshness
 *    hint is worse than an honest absence).
 * 🔴 Cross-view — a replenishment suggestion's `materializedPoId` must
 *    resolve to a REAL procurement PO in this same sample world (not a
 *    dangling id two screens would disagree about).
 */
import { describe, it, expect } from 'vitest';
import { sampleResponse } from '@/shared/sample/router';
import { findSurfaceCoverage } from '@/shared/sample/coverage';
import { SAMPLE_READ_ONLY, SAMPLE_NOT_READY } from '@/shared/sample/codes';
import { messageForCode } from '@/shared/api/errors';
import { masterRefLabel, MASTER_REF_UNRESOLVED } from '@/shared/lib/master-ref-label';
import { readCacheHeader } from '@/features/scm-ops/api/scm-client';
import {
  PoPageSchema,
  PurchaseOrderSchema,
  SnapshotResponseSchema,
  SkuBreakdownSchema,
  StalenessResponseSchema,
  NodesResponseSchema,
  S5_WARNING,
} from '@/features/scm-ops/api/types';
import { SuggestionSchema } from '@/features/scm-replenishment/api/types';
import { ReorderPolicySchema, SupplierMapSchema } from '@/features/scm-config/api/types';

function get(surface: string, path: string): Response {
  return sampleResponse({ core: 'flat', surface, method: 'GET', path });
}
const scm = (path: string) => get('scm', path);
const replenishment = (path: string) => get('scm_replenishment', path);
const config = (path: string) => get('scm_config', path);

async function json(res: Response): Promise<unknown> {
  return res.json();
}

// ---------------------------------------------------------------------------
// AC-0 — re-inventory (recorded in the task file's Implementation notes; this
// suite is the executable half: every GET this cell names is exercised below)
// ---------------------------------------------------------------------------

describe('AC-0 — ledger rows exist and are ready', () => {
  it('scm / scm_replenishment / scm_config are all ready', () => {
    expect(findSurfaceCoverage('flat', 'scm')?.status).toBe('ready');
    expect(findSurfaceCoverage('flat', 'scm_replenishment')?.status).toBe('ready');
    expect(findSurfaceCoverage('flat', 'scm_config')?.status).toBe('ready');
  });
});

// ---------------------------------------------------------------------------
// AC-1 — every GET parses with the REAL production zod schema (the SAME
// unwrap the real client callback performs — `env.data` for the envelope-
// unwrapped view-models, the FULL envelope for inventory-visibility)
// ---------------------------------------------------------------------------

describe('AC-1 — procurement PO (surface `scm`) parses with production schemas', () => {
  it('GET /po (list)', async () => {
    const res = scm('/api/v1/procurement/po?page=0&size=20');
    expect(res.status).toBe(200);
    const envelope = (await json(res)) as { data: unknown };
    const page = PoPageSchema.parse(envelope.data);
    expect(page.content.length).toBe(4);
  });

  it('GET /po/{poId} (detail) for every PO', async () => {
    for (const id of ['po-sample-0001', 'po-sample-0002', 'po-sample-0003', 'po-sample-0004']) {
      const res = scm(`/api/v1/procurement/po/${id}`);
      expect(res.status, id).toBe(200);
      const envelope = (await json(res)) as { data: unknown };
      const po = PurchaseOrderSchema.parse(envelope.data);
      expect(po.id).toBe(id);
    }
  });

  it('GET /po/{poId} — unknown id → 404 PO_NOT_FOUND', async () => {
    const res = scm('/api/v1/procurement/po/po-does-not-exist');
    expect(res.status).toBe(404);
    const body = (await json(res)) as { code: string };
    expect(body.code).toBe('PO_NOT_FOUND');
  });
});

describe('AC-1 — inventory-visibility (surface `scm`) parses with production schemas', () => {
  it('GET /snapshot (cross-node list)', async () => {
    const res = scm('/api/v1/inventory-visibility/snapshot?page=0&size=20');
    expect(res.status).toBe(200);
    const parsed = SnapshotResponseSchema.parse(await json(res));
    expect(Array.isArray(parsed.data) ? parsed.data.length : parsed.data.content.length).toBe(4);
    expect(parsed.meta.warning).toContain(S5_WARNING);
  });

  it('GET /snapshot?nodeId= (single-node array form)', async () => {
    const res = scm('/api/v1/inventory-visibility/snapshot?nodeId=node-sample-01');
    expect(res.status).toBe(200);
    const parsed = SnapshotResponseSchema.parse(await json(res));
    expect(Array.isArray(parsed.data)).toBe(true);
    expect((parsed.data as unknown[]).length).toBe(2);
  });

  it('GET /snapshot?nodeId= — unknown node → 404 NODE_NOT_FOUND', async () => {
    const res = scm('/api/v1/inventory-visibility/snapshot?nodeId=node-does-not-exist');
    expect(res.status).toBe(404);
    const body = (await json(res)) as { code: string };
    expect(body.code).toBe('NODE_NOT_FOUND');
  });

  it('GET /sku/{sku} (per-SKU breakdown) — matched SKU', async () => {
    const res = scm('/api/v1/inventory-visibility/sku/SKU-SAMPLE-001');
    expect(res.status).toBe(200);
    const parsed = SkuBreakdownSchema.parse(await json(res));
    expect(parsed.data.nodes.length).toBe(2);
    expect(parsed.data.totalQuantity).toBe(195);
  });

  it('GET /sku/{sku} — unmatched SKU is a real "no data yet" 200, not a 404 (no SKU_NOT_FOUND code exists)', async () => {
    const res = scm('/api/v1/inventory-visibility/sku/SKU-NO-SUCH');
    expect(res.status).toBe(200);
    const parsed = SkuBreakdownSchema.parse(await json(res));
    expect(parsed.data.nodes).toEqual([]);
    expect(parsed.data.totalQuantity).toBe(0);
  });

  it('GET /staleness', async () => {
    const res = scm('/api/v1/inventory-visibility/staleness');
    expect(res.status).toBe(200);
    const parsed = StalenessResponseSchema.parse(await json(res));
    expect(parsed.data.length).toBe(3);
    // honesty — STALE/UNREACHABLE nodes are surfaced, never hidden.
    expect(parsed.data.some((r) => r.stalenessStatus === 'STALE')).toBe(true);
    expect(parsed.data.some((r) => r.stalenessStatus === 'UNREACHABLE')).toBe(true);
    expect(parsed.data.some((r) => r.stalenessStatus === 'FRESH')).toBe(true);
  });

  it('GET /nodes', async () => {
    const res = scm('/api/v1/inventory-visibility/nodes');
    expect(res.status).toBe(200);
    const parsed = NodesResponseSchema.parse(await json(res));
    expect(parsed.data.length).toBe(3);
  });
});

describe('AC-1 — demand-planning suggestions (surface `scm_replenishment`) parse with production schema', () => {
  it('GET /suggestions (list) — every item parses with SuggestionSchema', async () => {
    const res = replenishment('/api/v1/demand-planning/suggestions?page=0&size=20');
    expect(res.status).toBe(200);
    const envelope = (await json(res)) as { data: unknown[]; meta: { totalElements: number } };
    expect(envelope.data.length).toBe(4);
    expect(envelope.meta.totalElements).toBe(4);
    for (const item of envelope.data) SuggestionSchema.parse(item);
  });

  it('GET /suggestions/{id} (detail) for every suggestion', async () => {
    for (const id of [
      'sugg-sample-0001',
      'sugg-sample-0002',
      'sugg-sample-0003',
      'sugg-sample-0004',
    ]) {
      const res = replenishment(`/api/v1/demand-planning/suggestions/${id}`);
      expect(res.status, id).toBe(200);
      const envelope = (await json(res)) as { data: unknown };
      const suggestion = SuggestionSchema.parse(envelope.data);
      expect(suggestion.id).toBe(id);
    }
  });

  it('GET /suggestions/{id} — unknown id → 404 SUGGESTION_NOT_FOUND', async () => {
    const res = replenishment('/api/v1/demand-planning/suggestions/sugg-does-not-exist');
    expect(res.status).toBe(404);
    const body = (await json(res)) as { code: string };
    expect(body.code).toBe('SUGGESTION_NOT_FOUND');
  });

  it('status/skuCode filters narrow the list on the SAME rows (not vacuous)', async () => {
    const all = (await json(
      replenishment('/api/v1/demand-planning/suggestions?page=0&size=20'),
    )) as { meta: { totalElements: number } };
    const suggested = (await json(
      replenishment('/api/v1/demand-planning/suggestions?status=SUGGESTED&page=0&size=20'),
    )) as { data: { status: string }[]; meta: { totalElements: number } };
    expect(suggested.meta.totalElements).toBeGreaterThan(0);
    expect(suggested.meta.totalElements).toBeLessThan(all.meta.totalElements);
    expect(suggested.data.every((s) => s.status === 'SUGGESTED')).toBe(true);
  });
});

describe('AC-1 — demand-planning seed/config (surface `scm_config`) parses with production schema', () => {
  it('GET /policies/{skuCode} — configured SKU', async () => {
    const res = config('/api/v1/demand-planning/policies/SKU-SAMPLE-001');
    expect(res.status).toBe(200);
    const envelope = (await json(res)) as { data: unknown };
    const policy = ReorderPolicySchema.parse(envelope.data);
    expect(policy.reorderPoint).toBe(10);
  });

  it('GET /sku-supplier-map/{skuCode} — configured SKU', async () => {
    const res = config('/api/v1/demand-planning/sku-supplier-map/SKU-SAMPLE-001');
    expect(res.status).toBe(200);
    const envelope = (await json(res)) as { data: unknown };
    const map = SupplierMapSchema.parse(envelope.data);
    expect(map.supplierId).toBe('SUP-SAMPLE-01');
  });
});

// ---------------------------------------------------------------------------
// AC-3 / TASK-MONO-677 — supplier reference resolution, through the router
// ---------------------------------------------------------------------------

const SUP_SAMPLE_01_ID = '11111111-1111-4111-8111-111111111111';

describe('AC-3 — supplier reference resolves per procurement-api.md rule (through the router, via masterRefLabel)', () => {
  async function poDetail(id: string) {
    const res = scm(`/api/v1/procurement/po/${id}`);
    const envelope = (await json(res)) as { data: unknown };
    return PurchaseOrderSchema.parse(envelope.data);
  }

  it('id match wins — po-sample-0001 resolves supplierCode/supplierName from an id lookup', async () => {
    const po = await poDetail('po-sample-0001');
    expect(po.supplierCode).toBe('SUP-SAMPLE-01');
    expect(po.supplierName).toBe('대한물산 (샘플)');
    expect(
      masterRefLabel(po.supplierId, { code: po.supplierCode, name: po.supplierName }),
    ).toBe('SUP-SAMPLE-01 · 대한물산 (샘플)');
  });

  it('code fallback — po-sample-0002 carries a CODE (from-suggestion shape) and still resolves', async () => {
    const po = await poDetail('po-sample-0002');
    expect(po.supplierId).toBe('SUP-SAMPLE-02');
    expect(po.supplierCode).toBe('SUP-SAMPLE-02');
    expect(po.supplierName).toBe('한빛무역 (샘플)');
  });

  it('🔴🔴 unmatched (UUID-shaped, no owner) — po-sample-0003 is null/null, renders 이름 확인 불가, NEVER the raw UUID', async () => {
    const po = await poDetail('po-sample-0003');
    expect(po.supplierCode).toBeNull();
    expect(po.supplierName).toBeNull();
    const label = masterRefLabel(po.supplierId, { code: po.supplierCode, name: po.supplierName });
    expect(label).toBe(MASTER_REF_UNRESOLVED);
    expect(label).not.toContain(po.supplierId);
  });

  it('list resolves the SAME way as detail (a page has ≤2 batched lookups worth of behaviour, not per-row drift)', async () => {
    const res = scm('/api/v1/procurement/po?page=0&size=20');
    const envelope = (await json(res)) as { data: unknown };
    const page = PoPageSchema.parse(envelope.data);
    const byId = Object.fromEntries(page.content.map((p) => [p.id, p]));
    expect(byId['po-sample-0001']?.supplierCode).toBe('SUP-SAMPLE-01');
    expect(byId['po-sample-0002']?.supplierCode).toBe('SUP-SAMPLE-02');
    expect(byId['po-sample-0003']?.supplierCode).toBeNull();
  });

  it('the `supplierId` filter matches the STORED value exactly (rule 6 — never a resolved code/name)', async () => {
    const res = scm(`/api/v1/procurement/po?supplierId=${SUP_SAMPLE_01_ID}&page=0&size=20`);
    const envelope = (await json(res)) as { data: unknown };
    const page = PoPageSchema.parse(envelope.data);
    // po-sample-0001 AND po-sample-0004 both store this exact id.
    expect(page.content.map((p) => p.id).sort()).toEqual(['po-sample-0001', 'po-sample-0004']);
  });
});

// ---------------------------------------------------------------------------
// AC-4 — /scm/config 404-as-empty: a deliberately-unconfigured SKU
// ---------------------------------------------------------------------------

describe('AC-4 — config seed lookups 404 for an unconfigured SKU (client turns this into "not configured yet")', () => {
  it('GET /policies/{skuCode} — SKU-SAMPLE-003 (deliberately absent) → 404 POLICY_NOT_FOUND', async () => {
    const res = config('/api/v1/demand-planning/policies/SKU-SAMPLE-003');
    expect(res.status).toBe(404);
    const body = (await json(res)) as { code: string };
    expect(body.code).toBe('POLICY_NOT_FOUND');
  });

  it('GET /sku-supplier-map/{skuCode} — SKU-SAMPLE-003 (deliberately absent) → 404 MAPPING_NOT_FOUND', async () => {
    const res = config('/api/v1/demand-planning/sku-supplier-map/SKU-SAMPLE-003');
    expect(res.status).toBe(404);
    const body = (await json(res)) as { code: string };
    expect(body.code).toBe('MAPPING_NOT_FOUND');
  });

  it('🔵 SKU-SAMPLE-003 is the SAME SKU the replenishment world already tells "not fully configured" about (no re-typed literal, one world)', async () => {
    const res = replenishment('/api/v1/demand-planning/suggestions/sugg-sample-0003');
    const envelope = (await json(res)) as { data: unknown };
    const suggestion = SuggestionSchema.parse(envelope.data);
    expect(suggestion.skuCode).toBe('SKU-SAMPLE-003');
    expect(suggestion.warehouseCode).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// AC-5 — no X-Cache header anywhere in sample mode (a false freshness hint)
// ---------------------------------------------------------------------------

describe('AC-5 — X-Cache is absent on every sample response (no false freshness hint)', () => {
  it('the per-SKU breakdown read (the ONE real endpoint that carries X-Cache) has none in sample mode', () => {
    const res = scm('/api/v1/inventory-visibility/sku/SKU-SAMPLE-001');
    expect(res.headers.get('X-Cache')).toBeNull();
    expect(readCacheHeader(res)).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// AC-6 — a representative write (제안 승인 = approveSuggestion) is rejected
// ---------------------------------------------------------------------------

describe('AC-6 — 제안 승인 (approveSuggestion) is rejected with the visitor-facing copy', () => {
  it('POST /suggestions/{id}/approve → 403 SAMPLE_READ_ONLY → "샘플 화면에서는 실행되지 않습니다"', async () => {
    const res = sampleResponse({
      core: 'flat',
      surface: 'scm_replenishment',
      method: 'POST',
      path: '/api/v1/demand-planning/suggestions/sugg-sample-0001/approve',
    });
    expect(res.status).toBe(403);
    const body = (await json(res)) as { code: string };
    expect(body.code).toBe(SAMPLE_READ_ONLY);
    expect(messageForCode(body.code)).toBe(
      '샘플 화면에서는 실행되지 않습니다. 로그인하면 실제로 실행됩니다',
    );
  });

  it('a pending-shaped path on a genuinely-unimplemented surface still 503s SAMPLE_NOT_READY', async () => {
    const res = sampleResponse({
      core: 'flat',
      surface: 'no-such-scm-surface',
      method: 'GET',
      path: '/anything',
    });
    expect(res.status).toBe(503);
    const body = (await json(res)) as { code: string };
    expect(body.code).toBe(SAMPLE_NOT_READY);
  });
});

// ---------------------------------------------------------------------------
// Edge case — the sample router never returns 429 (the backoff path stays
// covered by the authenticated tests, not the sample world)
// ---------------------------------------------------------------------------

describe('Edge Case — the sample router never 429s', () => {
  it('every ready GET this file exercises returns 200/404, never 429', async () => {
    const paths = [
      scm('/api/v1/procurement/po?page=0&size=20'),
      scm('/api/v1/inventory-visibility/snapshot?page=0&size=20'),
      replenishment('/api/v1/demand-planning/suggestions?page=0&size=20'),
      config('/api/v1/demand-planning/policies/SKU-SAMPLE-001'),
    ];
    for (const res of paths) expect(res.status).not.toBe(429);
  });
});

// ---------------------------------------------------------------------------
// Cross-view — replenishment ↔ procurement (a materialised suggestion points
// at a REAL PO in this SAME sample world)
// ---------------------------------------------------------------------------

describe('Cross-view — a MATERIALIZED suggestion’s materializedPoId resolves to a REAL PO', () => {
  it('sugg-sample-0003 (MATERIALIZED) → po-sample-0002, and that PO is a real, fetchable PO', async () => {
    const suggRes = replenishment('/api/v1/demand-planning/suggestions/sugg-sample-0003');
    const suggEnvelope = (await json(suggRes)) as { data: unknown };
    const suggestion = SuggestionSchema.parse(suggEnvelope.data);
    expect(suggestion.status).toBe('MATERIALIZED');
    expect(suggestion.materializedPoId).toBe('po-sample-0002');

    const poRes = scm(`/api/v1/procurement/po/${suggestion.materializedPoId}`);
    expect(poRes.status).toBe(200);
    const poEnvelope = (await json(poRes)) as { data: unknown };
    const po = PurchaseOrderSchema.parse(poEnvelope.data);
    expect(po.id).toBe('po-sample-0002');
  });

  it('every non-null nodeId referenced by the inventory snapshot resolves on /nodes (AC-8 world consistency)', async () => {
    const snapRes = scm('/api/v1/inventory-visibility/snapshot?page=0&size=20');
    const snapshot = SnapshotResponseSchema.parse(await json(snapRes));
    const rows = Array.isArray(snapshot.data) ? snapshot.data : snapshot.data.content;
    const nodesRes = scm('/api/v1/inventory-visibility/nodes');
    const nodes = NodesResponseSchema.parse(await json(nodesRes));
    const nodeIds = new Set(nodes.data.map((n) => n.id));
    for (const row of rows) {
      expect(row.nodeId, `snapshot row ${row.id}`).toBeDefined();
      expect(nodeIds.has(row.nodeId as string), `nodeId ${row.nodeId} must resolve`).toBe(true);
    }
  });
});
