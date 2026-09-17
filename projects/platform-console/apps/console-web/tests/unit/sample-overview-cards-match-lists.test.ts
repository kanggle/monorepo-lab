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
 * 🔵 scm's card is not listed yet — its domain fixture does not exist.
 *    TASK-PC-FE-288 adds its row here when it derives its card.
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

  it('Finance «잔액» (TASK-PC-FE-286 AC-7) = the balance on /finance/accounts for the SAME account', async () => {
    const card = (await overviewCard('finance')) as {
      balance: { amount: string; currency: string };
      accountId: string;
    };
    // Edge Case 1 — the account the card names is a REAL, browsable account
    // (not just a number that happens to match).
    expect(card.accountId).toBe(SAMPLE_FINANCE_DEFAULT_ACCOUNT_ID);
    const balances = (await body({
      core: 'flat',
      surface: 'finance',
      path: `/api/finance/accounts/${card.accountId}/balances`,
    })) as { data: { currency: string; ledger: string }[] };
    expect(balances.data.length).toBeGreaterThan(0);
    expect(card.balance.amount).toBe(balances.data[0].ledger);
    expect(card.balance.currency).toBe(balances.data[0].currency);
  });

  it('WMS «총 재고 · 알림» (TASK-PC-FE-287 AC-8) = the SAME /wms/inventory query the console-bff adapter sends', async () => {
    const card = (await overviewCard('wms')) as {
      inventorySnapshot: { totalStockUnits: number; alertCount: number };
    };
    // `WmsInventoryReadAdapter.read()` calls this EXACT path — no page/size —
    // so this is the identical query the real leg makes, not a convenient
    // stand-in (TASK-PC-FE-287's AC-8 wording: "같은 경로를 … 물어 파생한다").
    const list = (await body({
      core: 'wms',
      surface: 'wms',
      path: '/api/v1/admin/dashboard/inventory',
    })) as { content: { onHandQty: number; lowStockFlag: boolean }[] };
    expect(list.content.length).toBeGreaterThan(0);
    const totalStockUnits = list.content.reduce((sum, r) => sum + r.onHandQty, 0);
    const alertCount = list.content.filter((r) => r.lowStockFlag).length;
    // 🔴 not vacuous: the list must also hold a row that is NOT low-stock, or
    //    "flagged" and "every row" could not be told apart.
    expect(alertCount).toBeGreaterThan(0);
    expect(list.content.length).toBeGreaterThan(alertCount);
    expect(card.inventorySnapshot.totalStockUnits).toBe(totalStockUnits);
    expect(card.inventorySnapshot.alertCount).toBe(alertCount);
  });
});
