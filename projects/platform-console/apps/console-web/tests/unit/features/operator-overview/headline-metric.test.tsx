import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import {
  CARD_ORDER,
  DomainCard,
  OperatorOverviewScreen,
  OperatorOverviewSchema,
  type OperatorOverview,
} from '@/features/operator-overview';
import {
  headlineFromMetricsBody,
  HeadlineSchema,
  type Headline,
  type HeadlineDomain,
} from '@/shared/lib/operator-overview-headline';
import { SAMPLE_OPERATOR_OVERVIEW } from '@/shared/sample/fixtures/dashboards';
import {
  ECOMMERCE_FIXTURE_HANDLERS,
} from '@/shared/sample/fixtures/ecommerce';
import { ERP_FIXTURE_HANDLERS } from '@/shared/sample/fixtures/erp';
import { FINANCE_FIXTURE_HANDLERS } from '@/shared/sample/fixtures/finance';
import { IAM_FIXTURE_HANDLERS } from '@/shared/sample/fixtures/iam';
import { SCM_FIXTURE_HANDLERS } from '@/shared/sample/fixtures/scm';
import { WMS_FIXTURE_HANDLERS } from '@/shared/sample/fixtures/wms';

/**
 * TASK-PC-FE-368 — the card headline metric: pure selection (the single
 * definition), the card renderer, and the anonymous sample overview.
 */

const FIXTURE_PATH = resolve(
  dirname(fileURLToPath(import.meta.url)),
  '../../../../../../specs/contracts/fixtures/operator-overview-leg-bodies.json',
);
const fixture = JSON.parse(readFileSync(FIXTURE_PATH, 'utf8')) as {
  legs: Record<string, { body: unknown }>;
  headlineMetrics: Record<string, { body: unknown }>;
};

function wrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

const DOMAINS: readonly HeadlineDomain[] = ['iam', 'wms', 'scm', 'finance', 'erp', 'ecommerce'];

describe('headlineFromMetricsBody — the one selection', () => {
  const expectedKeys: Record<HeadlineDomain, string[]> = {
    iam: ['loginFailure'],
    wms: ['qtyShipped'],
    scm: ['poAmount:KRW', 'poAmount:USD'],
    finance: ['transferAmount:KRW'],
    erp: ['approved'],
    ecommerce: ['revenue'],
  };
  for (const d of DOMAINS) {
    it(`${d} → ${expectedKeys[d].join(' + ')} from the contract fixture`, () => {
      const h = headlineFromMetricsBody(d, fixture.headlineMetrics[d].body);
      expect(h.status).toBe('ok');
      if (h.status !== 'ok') return;
      expect(h.period).toBe('week');
      expect(h.metrics.map((m) => m.key)).toEqual(expectedKeys[d]);
    });
  }

  it('an enveloped procurement body ({ data, meta }) reads like the bare one', () => {
    const bare = fixture.headlineMetrics.scm.body;
    expect(headlineFromMetricsBody('scm', { data: bare, meta: { timestamp: 't' } })).toEqual(
      headlineFromMetricsBody('scm', bare),
    );
  });

  it('degrades — never a 0 — on garbage, a missing key, or a drifted unit', () => {
    expect(headlineFromMetricsBody('wms', null)).toEqual({ status: 'degraded' });
    expect(headlineFromMetricsBody('wms', { nope: 1 })).toEqual({ status: 'degraded' });
    const noKey = { ...(fixture.headlineMetrics.wms.body as object), metrics: [] };
    expect(headlineFromMetricsBody('wms', noKey)).toEqual({ status: 'degraded' });
    const drifted = {
      ...(fixture.headlineMetrics.wms.body as { metrics: unknown[] }),
      metrics: [{ key: 'qtyShipped', unit: 'money', currency: 'KRW', current: '1', previous: '1' }],
    };
    expect(headlineFromMetricsBody('wms', drifted)).toEqual({ status: 'degraded' });
  });
});

function overviewWith(headlines: Partial<Record<HeadlineDomain, Headline>>): OperatorOverview {
  return OperatorOverviewSchema.parse({
    asOf: '2026-10-24T05:00:00Z',
    cards: CARD_ORDER.map((domain) => ({
      domain,
      status: 'ok',
      data: fixture.legs[domain].body,
      ...(headlines[domain] ? { headline: headlines[domain] } : {}),
    })),
  });
}

describe('card renderer — the headline is its own cell', () => {
  const okAll = Object.fromEntries(
    DOMAINS.map((d) => [d, headlineFromMetricsBody(d, fixture.headlineMetrics[d].body)]),
  ) as Record<HeadlineDomain, Headline>;

  it('renders this week\'s value and the change vs the previous window on every card, in the unchanged order', () => {
    const overview = overviewWith(okAll);
    render(<OperatorOverviewScreen overview={overview} />, { wrapper: wrapper() });

    const cards = screen.getByTestId('operator-overview-cards');
    const order = within(cards)
      .getAllByTestId(/^operator-overview-card-(iam|wms|scm|finance|erp|ecommerce)$/)
      .map((el) => el.getAttribute('data-domain'));
    expect(order).toEqual([...CARD_ORDER]);

    const value = (d: string, key: string) =>
      screen.getByTestId(`operator-overview-card-${d}-headline-${key}-value`).textContent;
    const delta = (d: string, key: string) =>
      screen.getByTestId(`operator-overview-card-${d}-headline-${key}-delta`).textContent;

    expect(value('iam', 'loginFailure')).toBe('7');
    expect(delta('iam', 'loginFailure')).toBe('▲ 75.0%');
    expect(value('wms', 'qtyShipped')).toBe('640');
    expect(delta('wms', 'qtyShipped')).toBe('▼ 8.6%');
    expect(value('scm', 'poAmount:KRW')).toMatch(/2,450,000/);
    expect(value('scm', 'poAmount:USD')).toMatch(/12/); // two currencies, two lines
    expect(delta('scm', 'poAmount:USD')).toBe('—'); // previous 0 → no percentage, not Infinity
    expect(value('finance', 'transferAmount:KRW')).toMatch(/1,250,000/);
    expect(value('erp', 'approved')).toBe('8');
    expect(delta('erp', 'approved')).toBe('– 0.0%');
    expect(value('ecommerce', 'revenue')).toMatch(/3,210,000/);

    // The cards' own numbers are still there.
    expect(screen.getByTestId('operator-overview-card-iam-locked').textContent).toBe('3');
  });

  it('degraded / forbidden headline say so in words, show no number, and leave the card\'s own number', () => {
    const overview = overviewWith({
      ...okAll,
      wms: { status: 'degraded' },
      erp: { status: 'forbidden' },
    });
    render(<OperatorOverviewScreen overview={overview} />, { wrapper: wrapper() });

    const wms = screen.getByTestId('operator-overview-card-wms-headline-degraded');
    expect(wms.textContent).toContain('실적을 불러올 수 없습니다');
    expect(screen.queryByTestId('operator-overview-card-wms-headline')).toBeNull();
    const erp = screen.getByTestId('operator-overview-card-erp-headline-forbidden');
    expect(erp.textContent).toContain('실적을 볼 권한이 없습니다');
    expect(screen.queryByTestId('operator-overview-card-erp-headline-approved-value')).toBeNull();

    expect(screen.getByTestId('operator-overview-card-wms-unshipped').textContent).toBe('14');
    expect(screen.getByTestId('operator-overview-card-erp-inbox').textContent).toBe('2');
    // The other cards keep their headline.
    expect(screen.getByTestId('operator-overview-card-iam-headline-loginFailure-value').textContent).toBe('7');
  });

  it('a degraded CARD still shows its healthy headline (and a payload without headline renders none)', () => {
    const overview = OperatorOverviewSchema.parse({
      asOf: '2026-10-24T05:00:00Z',
      cards: CARD_ORDER.map((domain) =>
        domain === 'scm'
          ? { domain, status: 'degraded', reason: 'DOWNSTREAM_ERROR', headline: okAll.scm }
          : { domain, status: 'ok', data: fixture.legs[domain].body },
      ),
    });
    render(<OperatorOverviewScreen overview={overview} />, { wrapper: wrapper() });
    expect(screen.getByTestId('operator-overview-card-scm-degraded')).toBeTruthy();
    expect(screen.getByTestId('operator-overview-card-scm-headline-poAmount:KRW-value')).toBeTruthy();
    expect(screen.queryByTestId('operator-overview-card-iam-headline')).toBeNull();
    expect(screen.queryByTestId('operator-overview-card-iam-headline-degraded')).toBeNull();
  });

  it('DomainCard alone: forbidden card + ok headline', () => {
    const overview = overviewWith(okAll);
    render(
      <DomainCard
        card={{ domain: 'finance', status: 'forbidden', reason: 'PERMISSION_DENIED', headline: okAll.finance }}
        overviewForRetry={overview}
      />,
      { wrapper: wrapper() },
    );
    expect(screen.getByTestId('operator-overview-card-finance-forbidden')).toBeTruthy();
    expect(screen.getByTestId('operator-overview-card-finance-headline-transferAmount:KRW-value')).toBeTruthy();
  });
});

describe('sample (anonymous) overview — headline from the existing sample fixtures', () => {
  const SAMPLE_PATHS: Record<HeadlineDomain, [((p: string) => unknown) | undefined, string]> = {
    iam: [IAM_FIXTURE_HANDLERS['iam:iam_metrics'], '/api/admin/metrics?period=week&tenantId=sample'],
    wms: [WMS_FIXTURE_HANDLERS['wms:wms'], '/api/v1/admin/dashboard/metrics?period=week'],
    scm: [SCM_FIXTURE_HANDLERS['flat:scm'], '/api/v1/procurement/po/metrics?period=week'],
    finance: [FINANCE_FIXTURE_HANDLERS['flat:finance'], '/api/finance/accounts/metrics?period=week'],
    erp: [ERP_FIXTURE_HANDLERS['flat:erp_approval'], '/api/erp/approval/metrics?period=week'],
    ecommerce: [ECOMMERCE_FIXTURE_HANDLERS['ecommerce:ecommerce_order'], '/api/admin/orders/metrics?period=week'],
  };

  it('every sample card carries an ok headline that parses with the production schema', () => {
    const parsed = OperatorOverviewSchema.parse(SAMPLE_OPERATOR_OVERVIEW);
    expect(parsed.cards.map((c) => c.domain)).toEqual([...CARD_ORDER]);
    for (const c of parsed.cards) {
      expect(HeadlineSchema.safeParse(c.headline).success).toBe(true);
      expect(c.headline?.status).toBe('ok');
    }
  });

  for (const d of DOMAINS) {
    it(`🔵 pin — ${d}: the sample's restated key selection equals the production selection over the same fixture answer`, () => {
      const [handler, path] = SAMPLE_PATHS[d];
      const production = headlineFromMetricsBody(d, handler?.(path));
      expect(production.status).toBe('ok');
      const card = OperatorOverviewSchema.parse(SAMPLE_OPERATOR_OVERVIEW).cards.find((c) => c.domain === d)!;
      expect(card.headline).toEqual(production);
    });
  }

  it('renders the sample headline on the landing cards', () => {
    const overview = OperatorOverviewSchema.parse(SAMPLE_OPERATOR_OVERVIEW);
    render(<OperatorOverviewScreen overview={overview} />, { wrapper: wrapper() });
    for (const d of DOMAINS) {
      const card = screen.getByTestId(`operator-overview-card-${d}`);
      expect(within(card).getByTestId(`operator-overview-card-${d}-headline`)).toBeTruthy();
    }
  });
});
