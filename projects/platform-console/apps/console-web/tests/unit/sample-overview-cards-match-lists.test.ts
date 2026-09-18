/**
 * The landing overview's cards say the same thing as the lists they summarise
 * (ADR-MONO-074 — TASK-PC-FE-285, coordinator review).
 *
 * `/` → `/dashboards/overview` is the FIRST screen an anonymous visitor sees.
 * Its cards used to be typed numbers (IAM 128 · ERP 12 · E-Commerce 342) while
 * the list screens they summarise held 5 · 3 · 3 — each screen looked fine on
 * its own and the two contradicted each other (the TASK-PC-FE-284 defect class).
 *
 * Both sides go THROUGH the router: the overview as the console-bff proxy
 * surface answers it, the list as the domain surface answers the list screen.
 * The list side is counted from the rows actually returned (a page large enough
 * to hold every row), not from the fixture's own `totalElements`, so a fixture
 * whose meta and rows disagreed would not pass by agreeing with itself.
 *
 */
import { describe, it, expect } from 'vitest';
import { sampleResponse, type SampleRequest } from '@/shared/sample/router';
import { SAMPLE_FINANCE_DEFAULT_ACCOUNT_ID } from '@/shared/sample/fixtures/finance';

async function body(req: Omit<SampleRequest, 'method'>): Promise<Record<string, unknown>> {
  const res = sampleResponse({ ...req, method: 'GET' });
  expect(res.status, `${req.core}:${req.surface} ${req.path}`).toBe(200);
  return (await res.json()) as Record<string, unknown>;
}

async function overviewCard(domain: string): Promise<Record<string, unknown>> {
  const overview = (await body({
    core: 'console-bff',
    surface: 'operator-overview',
    path: '/api/console/dashboards/operator-overview',
  })) as { cards: { domain: string; status: string; data: Record<string, unknown> }[] };
  const card = overview.cards.find((c) => c.domain === domain);
  expect(card, `overview card ${domain}`).toBeDefined();
  expect(card!.status).toBe('ok');
  return card!.data;
}

describe('overview card = the list it summarises (through the router)', () => {
  it('IAM «전체 계정» = rows on the accounts list', async () => {
    const card = await overviewCard('iam');
    const list = (await body({
      core: 'iam',
      surface: 'accounts',
      path: '/api/admin/accounts?page=0&size=100',
    })) as { content: unknown[] };
    expect(list.content.length).toBeGreaterThan(0);
    expect(card.totalElements).toBe(list.content.length);
  });

  it('ERP «활성 부서 수» = ACTIVE rows on the departments list', async () => {
    const card = (await overviewCard('erp')) as { meta: { totalElements: unknown } };
    const list = (await body({
      core: 'flat',
      surface: 'erp',
      path: '/api/erp/masterdata/departments?page=0&size=100',
    })) as { data: { status: string }[] };
    const active = list.data.filter((d) => d.status === 'ACTIVE').length;
    expect(active).toBeGreaterThan(0);
    // 🔴 not vacuous: the list must also hold a non-ACTIVE row, or «active» and
    //    «all» could not be told apart and a card counting every row would pass.
    expect(list.data.length).toBeGreaterThan(active);
    expect(card.meta.totalElements).toBe(active);
  });

  it('E-Commerce «상품 수» = rows on the products list', async () => {
    const card = await overviewCard('ecommerce');
    const list = (await body({
      core: 'ecommerce',
      surface: 'ecommerce',
      path: '/api/admin/products?page=0&size=100',
    })) as { content: unknown[] };
    expect(list.content.length).toBeGreaterThan(0);
    expect(card.totalElements).toBe(list.content.length);
  });

  it('Finance «잔액» (286 AC-7 · 295) = the SAME balances body the console-bff leg carries', async () => {
    // TASK-PC-FE-295: the card's `data` IS the producer's balances envelope,
    // so "the card agrees with the list" is now an identity rather than a
    // derivation — the card cannot drift from the query without the query
    // itself changing. The previous version compared a hand-derived
    // `{ balance: { amount, currency } }` against the same query; that
    // comparison passed while the real card rendered «잔액 정보 없음».
    const card = (await overviewCard('finance')) as {
      data: { currency: string; ledger: string }[];
    };
    const balances = (await body({
      core: 'flat',
      surface: 'finance',
      path: `/api/finance/accounts/${SAMPLE_FINANCE_DEFAULT_ACCOUNT_ID}/balances`,
    })) as { data: { currency: string; ledger: string }[] };
    expect(balances.data.length).toBeGreaterThan(0);
    expect(card.data).toEqual(balances.data);
    // F5: the money fields stay strings all the way to the card.
    expect(typeof card.data[0].ledger).toBe('string');
  });

  it('WMS «재고 행 수» (287 AC-8 · 295) = page.totalElements of the SAME query the adapter sends', async () => {
    // `WmsInventoryReadAdapter.read()` calls this EXACT path — no page/size —
    // so this is the identical query the real leg makes (287's AC-8 wording:
    // "같은 경로를 … 물어 파생한다"), and 295 makes the card carry its body.
    const card = (await overviewCard('wms')) as {
      page: { totalElements: number };
      content: unknown[];
    };
    const list = (await body({
      core: 'wms',
      surface: 'wms',
      path: '/api/v1/admin/dashboard/inventory',
    })) as { page: { totalElements: number }; content: { lowStockFlag: boolean }[] };
    expect(list.page.totalElements).toBeGreaterThan(0);
    expect(card.page.totalElements).toBe(list.page.totalElements);
    // 🔴 not vacuous for the count's MEANING: the page must hold a low-stock
    //    row and a non-low-stock row, so "rows" cannot be mistaken for
    //    "alerts" — the tile that used to claim an alert count is gone
    //    precisely because this query cannot answer it beyond one page.
    const flagged = list.content.filter((r) => r.lowStockFlag).length;
    expect(flagged).toBeGreaterThan(0);
    expect(list.content.length).toBeGreaterThan(flagged);
    expect(card.page.totalElements).not.toBe(flagged);
  });

  it('SCM «스냅샷 행 수» (288 AC-8 · 295) = data.totalElements + the S5 warning of the SAME snapshot', async () => {
    const card = (await overviewCard('scm')) as {
      data: { content: { nodeId: string }[]; totalElements: number };
      meta: { warning: string };
    };
    const snapshot = (await body({
      core: 'flat',
      surface: 'scm',
      path: '/api/v1/inventory-visibility/snapshot',
    })) as {
      data: { content: { nodeId: string }[]; totalElements: number };
      meta: { warning: string };
    };
    expect(snapshot.data.totalElements).toBeGreaterThan(0);
    expect(card.data.totalElements).toBe(snapshot.data.totalElements);
    expect(card.meta.warning).toBe(snapshot.meta.warning);
    // 🔴 not vacuous: the snapshot must span more than one node, so "rows" and
    //    "distinct nodes" are different numbers — 295 relabelled this tile
    //    because a page cannot answer the node question at all.
    const distinctNodeIds = new Set(snapshot.data.content.map((r) => r.nodeId));
    expect(distinctNodeIds.size).toBeGreaterThan(1);
    expect(card.data.totalElements).not.toBe(distinctNodeIds.size);
  });
});
