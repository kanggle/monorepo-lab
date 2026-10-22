import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { DomainCard } from '@/features/operator-overview';
import type {
  Card,
  DomainKey,
  OperatorOverview,
} from '@/features/operator-overview';

/**
 * `<DomainCard>` per-status branch coverage (TASK-PC-FE-011 / AC-20;
 * TASK-PC-FE-364 business figures).
 *
 * Asserts the 3 distinct UI shapes per § 2.4.9.1:
 *  - `ok` — ONE business figure per card (the producer's page total), a
 *    one-line description, and a link to the work screen. `0` renders as "0";
 *    the erp card for an account linked to no employee renders «직원 연결 없음»
 *    instead of a number (Failure Scenario 2).
 *  - `degraded` — "data unavailable" placeholder + reason copy +
 *    retry button (the only client-component branch).
 *  - `forbidden` — "not available to your role / tenant" placeholder
 *    + reason copy.
 */

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

function envelopeFor(card: Card): OperatorOverview {
  // The retry button needs an envelope to seed; for unit branch tests
  // the envelope is otherwise unused (we never click; we just render).
  return { asOf: '2026-05-20T10:30:00Z', cards: [card, card, card, card, card] };
}

function renderCard(card: Card) {
  return render(<DomainCard card={card} overviewForRetry={envelopeFor(card)} />, {
    wrapper: wrapper(),
  });
}

/** One producer-shaped `ok` body per card, carrying page total `n`. */
function okBody(domain: DomainKey, n: number): unknown {
  switch (domain) {
    case 'iam':
      return { content: [], totalElements: n, page: 0, size: 1, totalPages: n };
    case 'wms':
      return {
        content: [],
        page: { number: 0, size: 1, totalElements: n, totalPages: n },
        sort: 'receivedAt,desc',
      };
    case 'scm':
      return {
        data: { content: [], page: 0, size: 1, totalElements: n, totalPages: n },
        meta: { timestamp: '2026-10-22T02:00:00Z' },
      };
    case 'finance':
      return {
        data: [],
        meta: { page: 0, size: 1, totalElements: n, totalPages: n, timestamp: 't' },
      };
    case 'erp':
      return {
        data: [],
        meta: { page: 0, size: 1, totalElements: n, totalPages: n, actorEmployeeId: 'emp-7' },
      };
    case 'ecommerce':
      return { content: [], page: 0, size: 1, totalElements: n };
  }
}

const CASES: ReadonlyArray<{
  domain: DomainKey;
  title: string;
  label: string;
  testId: string;
  href: string;
  linkText: string;
}> = [
  {
    domain: 'iam',
    title: 'IAM 계정',
    label: '잠긴 계정 (이 테넌트)',
    testId: 'operator-overview-card-iam-locked',
    href: '/accounts',
    linkText: '계정 운영으로 이동',
  },
  {
    domain: 'wms',
    title: 'WMS 출고',
    label: '미출고 주문',
    testId: 'operator-overview-card-wms-unshipped',
    href: '/wms/outbound',
    linkText: '출고 화면으로 이동',
  },
  {
    domain: 'scm',
    title: 'SCM 발주',
    label: '제출 대기 발주 (초안)',
    testId: 'operator-overview-card-scm-draft-po',
    href: '/scm/procurement',
    linkText: '조달 화면으로 이동',
  },
  {
    domain: 'finance',
    title: 'Finance 대사',
    label: '미해소 대사 차이',
    testId: 'operator-overview-card-finance-open-discrepancies',
    href: '/ledger',
    linkText: '원장 화면으로 이동',
  },
  {
    domain: 'erp',
    title: 'ERP 결재',
    label: '내 결재 대기',
    testId: 'operator-overview-card-erp-inbox',
    href: '/erp/approval',
    linkText: '결재함으로 이동',
  },
  {
    domain: 'ecommerce',
    title: 'ecommerce 주문',
    label: '출고 대기 주문 (결제 완료)',
    testId: 'operator-overview-card-ecommerce-to-ship',
    href: '/ecommerce/orders',
    linkText: '주문 화면으로 이동',
  },
];

describe('DomainCard — ok branch: one business figure per card (TASK-PC-FE-364)', () => {
  it.each(CASES)('$domain ok → title, label and the page total', (c) => {
    renderCard({ domain: c.domain, status: 'ok', data: okBody(c.domain, 12345) });
    const section = screen.getByTestId(`operator-overview-card-${c.domain}`);
    expect(section).toHaveAttribute('data-status', 'ok');
    expect(within(section).getByRole('heading', { level: 2 })).toHaveTextContent(c.title);
    expect(within(section).getByText(c.label)).toBeInTheDocument();
    expect(screen.getByTestId(c.testId)).toHaveTextContent('12,345');
    expect(
      screen.getByTestId(`operator-overview-card-${c.domain}-description`).textContent,
    ).not.toBe('');
  });

  it.each(CASES)('$domain ok with a 0 total → renders "0", not the "—" fallback', (c) => {
    renderCard({ domain: c.domain, status: 'ok', data: okBody(c.domain, 0) });
    const value = screen.getByTestId(c.testId);
    expect(value).toHaveTextContent(/^0$/);
  });

  it.each(CASES)('$domain ok → links to its work screen ($href) with no filter param', (c) => {
    renderCard({ domain: c.domain, status: 'ok', data: okBody(c.domain, 1) });
    const link = screen.getByTestId(`operator-overview-card-${c.domain}-work-link`);
    // 🔴 verbatim, no query: the destinations do not seed a status filter from
    // the URL, so a `?status=…` would land on the unfiltered list silently.
    expect(link).toHaveAttribute('href', c.href);
    expect(link).toHaveTextContent(c.linkText);
  });

  it.each(CASES)('$domain ok but an unreadable body → "—" placeholder, never a crash', (c) => {
    renderCard({ domain: c.domain, status: 'ok', data: 'not-an-object' });
    // erp: an unparseable body is not «not linked» either — it is «—».
    expect(screen.getByTestId(c.testId)).toHaveTextContent('—');
  });

  it('gap ok → the heading still drills down to the IAM detail (/dashboards) (TASK-PC-FE-034 AC-5)', () => {
    renderCard({ domain: 'iam', status: 'ok', data: okBody('iam', 3) });
    const link = screen.getByTestId('operator-overview-card-iam-drilldown');
    expect(link).toHaveAttribute('href', '/dashboards');
    expect(link).toHaveAccessibleName(/IAM 계정/);
  });

  it.each(['wms', 'scm', 'finance', 'erp', 'ecommerce'] as const)(
    '%s ok → does NOT render the heading drill-down (AC-6: IAM card only)',
    (domain) => {
      renderCard({ domain, status: 'ok', data: okBody(domain, 1) });
      expect(
        screen.queryByTestId('operator-overview-card-iam-drilldown'),
      ).not.toBeInTheDocument();
    },
  );

  it('wms ok → keeps the low-stock LINK (TASK-PC-FE-296 ⓒ — zero calls), next to the outbound link', () => {
    // 🔴 The href is asserted VERBATIM on purpose. `wms/inventory/page.tsx`
    //    seeds its 저재고 filter from exactly `?lowStockOnly=true`.
    renderCard({ domain: 'wms', status: 'ok', data: okBody('wms', 14) });
    expect(screen.getByTestId('operator-overview-card-wms-lowstock-link')).toHaveAttribute(
      'href',
      '/wms/inventory?lowStockOnly=true',
    );
    expect(screen.getByTestId('operator-overview-card-wms-unshipped')).toHaveTextContent('14');
    expect(screen.queryByTestId('operator-overview-card-wms-alerts')).not.toBeInTheDocument();
  });

  it('wms ok → the count is page.totalElements, never a sum over content[] (295 AC-6)', () => {
    renderCard({
      domain: 'wms',
      status: 'ok',
      data: {
        content: [{ lineCount: 5 }, { lineCount: 7 }],
        page: { number: 0, size: 2, totalElements: 9, totalPages: 5 },
      },
    });
    expect(screen.getByTestId('operator-overview-card-wms-unshipped')).toHaveTextContent(/^9$/);
  });

  it('finance ok → shows the count only; no minor-units string from a row reaches the DOM (F5)', () => {
    renderCard({
      domain: 'finance',
      status: 'ok',
      data: {
        data: [{ expectedMinor: '1234500', actualMinor: '1234600', currency: 'KRW' }],
        meta: { totalElements: 1 },
      },
    });
    expect(
      screen.getByTestId('operator-overview-card-finance-open-discrepancies'),
    ).toHaveTextContent('1');
    for (const s of ['1234500', '1,234,500', '1234600', '1,234,600', 'KRW']) {
      expect(screen.queryByText(s, { exact: false })).not.toBeInTheDocument();
    }
  });

  it('finance ok → no balance / default-account wording is left on the card', () => {
    renderCard({ domain: 'finance', status: 'ok', data: okBody('finance', 2) });
    const section = screen.getByTestId('operator-overview-card-finance');
    expect(section.textContent).not.toMatch(/잔액|기본 finance 계정/);
  });
});

describe('DomainCard — erp «직원 연결 없음» (TASK-PC-FE-364 Failure Scenario 2)', () => {
  it('🔴 meta.actorEmployeeId absent → «직원 연결 없음», NO number and NO 0', () => {
    renderCard({
      domain: 'erp',
      status: 'ok',
      data: { data: [], meta: { page: 0, size: 1, totalElements: 0, totalPages: 0 } },
    });
    expect(screen.getByTestId('operator-overview-card-erp-not-linked')).toHaveTextContent(
      '직원 연결 없음',
    );
    expect(screen.queryByTestId('operator-overview-card-erp-inbox')).not.toBeInTheDocument();
    expect(screen.getByTestId('operator-overview-card-erp').textContent).not.toMatch(/\b0\b/);
    // The work screen is still one click away.
    expect(screen.getByTestId('operator-overview-card-erp-work-link')).toHaveAttribute(
      'href',
      '/erp/approval',
    );
  });

  it('meta.actorEmployeeId null → also «not linked» (never a failed parse into «—»)', () => {
    renderCard({
      domain: 'erp',
      status: 'ok',
      data: { data: [], meta: { totalElements: 0, actorEmployeeId: null } },
    });
    expect(screen.getByTestId('operator-overview-card-erp-not-linked')).toBeInTheDocument();
  });

  it('linked with nothing waiting → a real "0" (a different state from «not linked»)', () => {
    renderCard({
      domain: 'erp',
      status: 'ok',
      data: { data: [], meta: { totalElements: 0, actorEmployeeId: 'emp-7' } },
    });
    expect(screen.getByTestId('operator-overview-card-erp-inbox')).toHaveTextContent(/^0$/);
    expect(screen.queryByTestId('operator-overview-card-erp-not-linked')).not.toBeInTheDocument();
  });

  it('the employee id is never rendered', () => {
    renderCard({
      domain: 'erp',
      status: 'ok',
      data: { data: [], meta: { totalElements: 2, actorEmployeeId: 'emp-secret-77' } },
    });
    expect(screen.queryByText('emp-secret-77', { exact: false })).not.toBeInTheDocument();
  });
});

describe('DomainCard — degraded branch', () => {
  it.each([
    ['iam', 'DOWNSTREAM_ERROR'],
    ['wms', 'TIMEOUT'],
    ['scm', 'CIRCUIT_OPEN'],
    ['finance', 'DOWNSTREAM_ERROR'],
    ['erp', 'TIMEOUT'],
    ['ecommerce', 'CIRCUIT_OPEN'],
  ] as const)(
    '%s degraded/%s → placeholder + reason + retry button, no work link',
    (domain, reason) => {
      renderCard({ domain, status: 'degraded', reason });
      expect(
        screen.getByTestId(`operator-overview-card-${domain}-degraded`),
      ).toBeInTheDocument();
      expect(
        screen.getByTestId(
          `operator-overview-card-${domain}-degraded-reason`,
        ),
      ).toHaveTextContent(reason);
      expect(
        screen.getByTestId(`operator-overview-retry-${domain}-degraded`),
      ).toBeInTheDocument();
      expect(
        screen.queryByTestId(`operator-overview-card-${domain}-work-link`),
      ).not.toBeInTheDocument();
    },
  );

  it('gap degraded → drill-down link is SUPPRESSED (AC-6: link only on ok)', () => {
    renderCard({ domain: 'iam', status: 'degraded', reason: 'TIMEOUT' });
    expect(
      screen.queryByTestId('operator-overview-card-iam-drilldown'),
    ).not.toBeInTheDocument();
  });

  it('degraded card status attribute is "degraded"', () => {
    renderCard({ domain: 'iam', status: 'degraded', reason: 'TIMEOUT' });
    expect(screen.getByTestId('operator-overview-card-iam')).toHaveAttribute(
      'data-status',
      'degraded',
    );
  });
});

describe('DomainCard — forbidden branch', () => {
  it.each([
    ['iam', 'PERMISSION_DENIED'],
    ['wms', 'TENANT_FORBIDDEN'],
    ['scm', 'PERMISSION_DENIED'],
    ['finance', 'PERMISSION_DENIED'],
    ['erp', 'TENANT_FORBIDDEN'],
    ['ecommerce', 'PERMISSION_DENIED'],
  ] as const)(
    '%s forbidden/%s → placeholder + reason, no number and no work link',
    (domain, reason) => {
      renderCard({ domain, status: 'forbidden', reason });
      expect(
        screen.getByTestId(`operator-overview-card-${domain}-forbidden`),
      ).toBeInTheDocument();
      expect(
        screen.getByTestId(
          `operator-overview-card-${domain}-forbidden-reason`,
        ),
      ).toHaveTextContent(reason);
      expect(
        screen.queryByTestId(`operator-overview-card-${domain}-work-link`),
      ).not.toBeInTheDocument();
    },
  );

  it('gap forbidden → drill-down link is SUPPRESSED (AC-6: link only on ok)', () => {
    renderCard({ domain: 'iam', status: 'forbidden', reason: 'PERMISSION_DENIED' });
    expect(
      screen.queryByTestId('operator-overview-card-iam-drilldown'),
    ).not.toBeInTheDocument();
  });

  it('MISSING_PREREQUISITE (still in the wire vocabulary) → generic copy, no finance-account hint', () => {
    // TASK-PC-FE-364: this route no longer produces it; a consumer must still
    // render it without breaking, and the old «set a default finance account»
    // hint is gone with the prerequisite.
    renderCard({ domain: 'finance', status: 'forbidden', reason: 'MISSING_PREREQUISITE' });
    expect(
      screen.getByTestId('operator-overview-card-finance-forbidden-reason'),
    ).toHaveTextContent('MISSING_PREREQUISITE');
    expect(
      screen.queryByTestId('operator-overview-card-finance-missing-hint'),
    ).not.toBeInTheDocument();
  });
});
