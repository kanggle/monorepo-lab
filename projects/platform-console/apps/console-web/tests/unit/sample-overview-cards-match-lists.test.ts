/**
 * The landing overview's cards say the same thing as the lists they summarise
 * (ADR-MONO-074 — TASK-PC-FE-285, coordinator review; TASK-PC-FE-364 business
 * figures).
 *
 * `/` → `/dashboards/overview` is the FIRST screen an anonymous visitor sees.
 * Its cards used to be typed numbers (IAM 128 · ERP 12 · E-Commerce 342) while
 * the list screens they summarise held 5 · 3 · 3 — each screen looked fine on
 * its own and the two contradicted each other (the TASK-PC-FE-284 defect class).
 *
 * Both sides go THROUGH the router: the overview as the console-composition
 * surface answers it, the list as the domain surface answers the list screen.
 * The list side is counted from the rows actually returned (a page large enough
 * to hold every row) and filtered HERE by the card's own predicate, not taken
 * from the fixture's own `totalElements` — so a fixture whose meta and rows
 * disagreed, or whose filter was ignored, would not pass by agreeing with itself.
 *
 * 🔴 Each cell is also non-vacuous for the card's MEANING: the unfiltered list
 * must hold a row the predicate EXCLUDES, so a card that counted every row
 * (the filter silently dropped) could not pass.
 */
import { describe, it, expect } from 'vitest';
import { sampleResponse, type SampleRequest } from '@/shared/sample/router';

async function body(req: Omit<SampleRequest, 'method'>): Promise<Record<string, unknown>> {
  const res = sampleResponse({ ...req, method: 'GET' });
  expect(res.status, `${req.core}:${req.surface} ${req.path}`).toBe(200);
  return (await res.json()) as Record<string, unknown>;
}

async function overviewCard(domain: string): Promise<Record<string, unknown>> {
  const overview = (await body({
    core: 'console-composition',
    surface: 'operator-overview',
    path: '/api/console/dashboards/operator-overview',
  })) as { cards: { domain: string; status: string; data: Record<string, unknown> }[] };
  const card = overview.cards.find((c) => c.domain === domain);
  expect(card, `overview card ${domain}`).toBeDefined();
  expect(card!.status).toBe('ok');
  return card!.data;
}

/** The predicate count, plus the non-vacuity check that it excludes something. */
function countWhere<T>(rows: readonly T[], pred: (r: T) => boolean, what: string): number {
  const n = rows.filter(pred).length;
  expect(n, `${what}: the sample world must hold at least one`).toBeGreaterThan(0);
  expect(rows.length, `${what}: the list must also hold a row the card excludes`).toBeGreaterThan(n);
  return n;
}

describe('overview card = the list it summarises (through the router)', () => {
  it('IAM «잠긴 계정» = LOCKED rows on the accounts list', async () => {
    const card = (await overviewCard('iam')) as { totalElements: unknown };
    const list = (await body({
      core: 'iam',
      surface: 'accounts',
      path: '/api/admin/accounts?page=0&size=100',
    })) as { content: { status: string }[] };
    expect(card.totalElements).toBe(
      countWhere(list.content, (a) => a.status === 'LOCKED', 'iam LOCKED'),
    );
  });

  it('WMS «미출고 주문» = RECEIVED rows on the outbound dashboard list', async () => {
    const card = (await overviewCard('wms')) as { page: { totalElements: unknown } };
    const list = (await body({
      core: 'wms',
      surface: 'wms',
      path: '/api/v1/admin/dashboard/orders?page=0&size=100',
    })) as { content: { status: string }[] };
    expect(card.page.totalElements).toBe(
      countWhere(list.content, (o) => o.status === 'RECEIVED', 'wms RECEIVED'),
    );
  });

  it('SCM «제출 대기 발주» = DRAFT rows on the PO list', async () => {
    const card = (await overviewCard('scm')) as { data: { totalElements: unknown } };
    const list = (await body({
      core: 'flat',
      surface: 'scm',
      path: '/api/v1/procurement/po?page=0&size=100',
    })) as { data: { content: { status: string }[] } };
    expect(card.data.totalElements).toBe(
      countWhere(list.data.content, (p) => p.status === 'DRAFT', 'scm DRAFT'),
    );
  });

  it('Finance «미해소 대사 차이» = OPEN rows on the discrepancy queue', async () => {
    const card = (await overviewCard('finance')) as { meta: { totalElements: unknown } };
    const list = (await body({
      core: 'flat',
      surface: 'ledger',
      path: '/api/finance/ledger/reconciliation/discrepancies?page=0&size=100',
    })) as { data: { status: string }[] };
    expect(card.meta.totalElements).toBe(
      countWhere(list.data, (d) => d.status === 'OPEN', 'finance OPEN'),
    );
  });

  it('ERP «내 결재 대기» = the inbox rows the approval screen shows, for a LINKED caller', async () => {
    const card = (await overviewCard('erp')) as {
      meta: { totalElements: unknown; actorEmployeeId?: unknown };
    };
    const inbox = (await body({
      core: 'flat',
      surface: 'erp_approval',
      path: '/api/erp/approval/inbox?page=0&size=100',
    })) as { data: unknown[]; meta: { actorEmployeeId?: unknown } };
    expect(inbox.data.length).toBeGreaterThan(0);
    expect(card.meta.totalElements).toBe(inbox.data.length);
    // 🔴 not vacuous for the «not linked» state: both sides say whose queue it is.
    expect(typeof card.meta.actorEmployeeId).toBe('string');
    expect(card.meta.actorEmployeeId).toBe(inbox.meta.actorEmployeeId);
    // …and the inbox is narrower than all requests (it is «mine, pending»).
    const all = (await body({
      core: 'flat',
      surface: 'erp_approval',
      path: '/api/erp/approval/requests?page=0&size=100',
    })) as { data: unknown[] };
    expect(all.data.length).toBeGreaterThan(inbox.data.length);
  });

  it('E-Commerce «출고 대기 주문» = CONFIRMED rows on the orders list', async () => {
    const card = (await overviewCard('ecommerce')) as { totalElements: unknown };
    const list = (await body({
      core: 'ecommerce',
      surface: 'ecommerce_order',
      path: '/api/admin/orders?page=0&size=100',
    })) as { content: { status: string }[] };
    expect(card.totalElements).toBe(
      countWhere(list.content, (o) => o.status === 'CONFIRMED', 'ecommerce CONFIRMED'),
    );
  });
});
