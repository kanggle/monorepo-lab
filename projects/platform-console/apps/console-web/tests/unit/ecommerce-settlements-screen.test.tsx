import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { SettlementsScreen } from '@/features/ecommerce-ops';
import type {
  AccrualsResponse,
  PeriodsResponse,
} from '@/features/ecommerce-ops';

/**
 * `SettlementsScreen` behaviour (TASK-PC-FE-221 Phase A AC-2/AC-6):
 *   - renders the seeded accrual lines + settlement periods (both sections);
 *   - the 4 sections (라인 / 잔액 / 수수료율 / 기간) are present;
 *   - empty seeds render the empty-state (not a crash);
 *   - the seller-balance lookup gates on submit → renders ₩-formatted balance;
 *   - REVERSAL money renders negative (sign-preserving formatter).
 *
 * The client sections call the same-origin `/api/ecommerce/settlements/**` proxy
 * via `fetch` (mocked, routed by URL). Seeded page-0 sections do not fetch.
 */

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), refresh: vi.fn() }),
  usePathname: () => '/ecommerce/settlements',
}));

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

const ACCRUALS: AccrualsResponse = {
  items: [
    {
      accrualId: 'ac-1',
      orderId: 'ord-1',
      paymentId: 'pay-1',
      sellerId: 'acme-corp',
      type: 'ACCRUAL',
      grossMinor: 100000,
      rateBps: 1000,
      commissionMinor: 10000,
      sellerNetMinor: 90000,
      occurredAt: '2026-06-14T00:00:00Z',
    },
    {
      accrualId: 'ac-2',
      orderId: 'ord-2',
      paymentId: 'pay-2',
      sellerId: 'acme-corp',
      type: 'REVERSAL',
      grossMinor: -50000,
      rateBps: 1000,
      commissionMinor: -5000,
      sellerNetMinor: -45000,
      occurredAt: '2026-06-15T00:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 2,
};

const PERIODS: PeriodsResponse = {
  items: [
    {
      periodId: '2026-06',
      from: '2026-06-01T00:00:00Z',
      to: '2026-06-30T23:59:59Z',
      status: 'OPEN',
      closedAt: null,
      sellerCount: 3,
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
};

const EMPTY_ACCRUALS: AccrualsResponse = {
  items: [],
  page: 0,
  size: 20,
  totalElements: 0,
};
const EMPTY_PERIODS: PeriodsResponse = {
  items: [],
  page: 0,
  size: 20,
  totalElements: 0,
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('SettlementsScreen — seeded render + section presence', () => {
  it('renders the 4 sections + seeded accrual & period rows', () => {
    render(<SettlementsScreen accruals={ACCRUALS} periods={PERIODS} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.getByRole('heading', { name: 'E-Commerce 정산' }),
    ).toBeInTheDocument();
    // section headings (getByRole disambiguates from same-text submit buttons)
    expect(
      screen.getByRole('heading', { name: '정산 라인 (append-only ledger)' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: '셀러 잔액 조회' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: '수수료율 조회' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '정산 기간' })).toBeInTheDocument();
    // seeded rows
    expect(screen.getByTestId('accrual-row-0')).toBeInTheDocument();
    expect(screen.getByTestId('accrual-row-1')).toBeInTheDocument();
    expect(screen.getByTestId('period-row-0')).toBeInTheDocument();
  });

  it('renders REVERSAL money as a negative ₩ amount (sign-preserving)', () => {
    render(<SettlementsScreen accruals={ACCRUALS} periods={PERIODS} />, {
      wrapper: wrapper(),
    });
    const reversalRow = screen.getByTestId('accrual-row-1');
    expect(reversalRow).toHaveTextContent('-₩5,000');
    expect(reversalRow).toHaveTextContent('REVERSAL');
    // rate 1000 bps → 10.00%
    expect(reversalRow).toHaveTextContent('10.00%');
  });

  it('renders the empty-state when both seeds are empty (no crash)', () => {
    render(
      <SettlementsScreen accruals={EMPTY_ACCRUALS} periods={EMPTY_PERIODS} />,
      { wrapper: wrapper() },
    );
    expect(screen.getByTestId('settlements-accruals-empty')).toBeInTheDocument();
    expect(screen.getByTestId('settlements-periods-empty')).toBeInTheDocument();
  });
});

describe('SettlementsScreen — seller balance lookup gates on submit', () => {
  it('fetches + renders a ₩-formatted balance after submitting a sellerId', async () => {
    // A fresh `Response` per call (not one shared static object) — the
    // sellers-list GET fired by `<SellerSelect>` on mount and the balance GET
    // fired on submit each read their OWN body; a shared `Response` would
    // throw "body already used" on the second read (TASK-PC-FE-336).
    const fetchMock = vi.fn().mockImplementation(() =>
      Promise.resolve(
        jsonResponse({
          sellerId: 'acme-corp',
          accruedNetMinor: 90000,
          platformCommissionMinor: 10000,
          grossMinor: 100000,
          accrualCount: 1,
          asOf: '2026-06-14T00:00:00Z',
        }),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    render(<SettlementsScreen accruals={EMPTY_ACCRUALS} periods={EMPTY_PERIODS} />, {
      wrapper: wrapper(),
    });

    // no query until submit
    expect(screen.queryByTestId('settlements-balance-detail')).toBeNull();

    // TASK-PC-FE-336 — the seller field is a `<SellerSelect>` now; the mocked
    // `fetch` above answers EVERY call (incl. the sellers-list GET) with the
    // balance shape, which fails `SellerListSchema` → the selector falls back
    // to the old free-text id input (same testid, AC-3).
    await screen.findByTestId('settlements-balance-seller-input-fallback');
    await userEvent.type(
      screen.getByTestId('settlements-balance-seller-input'),
      'acme-corp',
    );
    await userEvent.click(screen.getByTestId('settlements-balance-submit'));

    await waitFor(() =>
      expect(screen.getByTestId('settlements-balance-detail')).toBeInTheDocument(),
    );
    const detail = screen.getByTestId('settlements-balance-detail');
    expect(detail).toHaveTextContent('₩90,000');
    const balanceCall = fetchMock.mock.calls.find((c) =>
      String(c[0]).includes('/api/ecommerce/settlements/sellers/acme-corp/balance'),
    );
    expect(balanceCall).toBeDefined();
  });
});

// ===========================================================================
// TASK-PC-FE-336 — `<SellerSelect>` (AC-1/AC-2/AC-3/AC-6 for the settlement
// screens; the operator-groups datalist AC-4 is covered separately).
// ===========================================================================

function sellerListJson(overrides: Partial<{ totalElements: number }> = {}) {
  return {
    content: [
      {
        sellerId: 'acme-corp',
        displayName: 'ACME Corp',
        status: 'ACTIVE',
        createdAt: '2026-01-01T00:00:00Z',
      },
      {
        sellerId: 'beta-co',
        displayName: 'Beta Co',
        status: 'SUSPENDED',
        createdAt: '2026-01-02T00:00:00Z',
      },
    ],
    page: 0,
    size: 100,
    totalElements: overrides.totalElements ?? 2,
  };
}

describe('SellerSelect — real list render + chosen sellerId reaches the request (AC-1)', () => {
  it('renders «표시명 · sellerId · 상태» options and the chosen sellerId reaches the balance request', async () => {
    const fetchMock = vi.fn((url: string) => {
      const u = String(url);
      if (u.includes('/api/ecommerce/sellers?')) {
        return Promise.resolve(jsonResponse(sellerListJson()));
      }
      if (u.includes('/balance')) {
        return Promise.resolve(
          jsonResponse({
            sellerId: 'acme-corp',
            accruedNetMinor: 90000,
            platformCommissionMinor: 10000,
            grossMinor: 100000,
            accrualCount: 1,
            asOf: '2026-06-14T00:00:00Z',
          }),
        );
      }
      return Promise.resolve(jsonResponse({ code: 'NOT_FOUND', message: 'nf' }, 404));
    });
    vi.stubGlobal('fetch', fetchMock);

    render(<SettlementsScreen accruals={EMPTY_ACCRUALS} periods={EMPTY_PERIODS} />, {
      wrapper: wrapper(),
    });

    const select = (await screen.findByTestId(
      'settlements-balance-seller-input',
    )) as HTMLSelectElement;
    expect(select.tagName).toBe('SELECT');
    await waitFor(() => expect(select.options.length).toBe(3)); // blank + 2 sellers
    expect(select.options[1].textContent).toBe('ACME Corp · acme-corp · ACTIVE');
    expect(select.options[2].textContent).toBe('Beta Co · beta-co · SUSPENDED');

    await userEvent.selectOptions(select, 'acme-corp');
    await userEvent.click(screen.getByTestId('settlements-balance-submit'));

    await waitFor(() =>
      expect(screen.getByTestId('settlements-balance-detail')).toBeInTheDocument(),
    );
    const balanceCall = fetchMock.mock.calls.find((c) =>
      String(c[0]).includes('/balance'),
    )!;
    expect(String(balanceCall[0])).toContain('/sellers/acme-corp/balance');
  });

  it('shows a «처음 N개만 표시» notice when totalElements exceeds the one loaded page', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse(sellerListJson({ totalElements: 150 })),
    );
    vi.stubGlobal('fetch', fetchMock);

    render(<SettlementsScreen accruals={EMPTY_ACCRUALS} periods={EMPTY_PERIODS} />, {
      wrapper: wrapper(),
    });

    await screen.findByTestId('settlements-balance-seller-input-truncated');
    expect(
      screen.getByTestId('settlements-balance-seller-input-truncated'),
    ).toHaveTextContent('처음 2개만 표시합니다');
  });
});

describe('AccrualsSection seller filter — blank selection omits sellerId (AC-2)', () => {
  it('submits the accrual filter without `sellerId` when the seller select is left blank («전체 셀러»)', async () => {
    const fetchMock = vi.fn((url: string) => {
      const u = String(url);
      if (u.includes('/api/ecommerce/sellers?')) {
        return Promise.resolve(jsonResponse(sellerListJson()));
      }
      if (u.includes('/api/ecommerce/settlements/accruals')) {
        return Promise.resolve(jsonResponse(EMPTY_ACCRUALS));
      }
      return Promise.resolve(jsonResponse({ code: 'NOT_FOUND', message: 'nf' }, 404));
    });
    vi.stubGlobal('fetch', fetchMock);

    render(<SettlementsScreen accruals={ACCRUALS} periods={EMPTY_PERIODS} />, {
      wrapper: wrapper(),
    });

    await screen.findByTestId('settlements-accruals-seller-input');
    // The seller select stays at its default blank («전체 셀러») option; typing
    // an orderId forces a real re-fetch (a blank-seller resubmit alone would
    // hit the already-seeded page-0 cache entry and fetch nothing — this
    // isolates the "blank seller" half of the filter from that caching detail).
    await userEvent.type(
      screen.getByTestId('settlements-accruals-order-input'),
      'ord-9',
    );
    await userEvent.click(screen.getByTestId('settlements-accruals-filter-submit'));

    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some((c) => String(c[0]).includes('orderId=ord-9')),
      ).toBe(true),
    );
    const accrualsCall = fetchMock.mock.calls.find((c) =>
      String(c[0]).includes('orderId=ord-9'),
    )!;
    expect(String(accrualsCall[0])).not.toContain('sellerId=');
  });
});
