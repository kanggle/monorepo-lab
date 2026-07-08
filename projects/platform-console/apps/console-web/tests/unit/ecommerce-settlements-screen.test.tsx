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
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        sellerId: 'acme-corp',
        accruedNetMinor: 90000,
        platformCommissionMinor: 10000,
        grossMinor: 100000,
        accrualCount: 1,
        asOf: '2026-06-14T00:00:00Z',
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    render(<SettlementsScreen accruals={EMPTY_ACCRUALS} periods={EMPTY_PERIODS} />, {
      wrapper: wrapper(),
    });

    // no query until submit
    expect(screen.queryByTestId('settlements-balance-detail')).toBeNull();

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
    const [url] = fetchMock.mock.calls[0];
    expect(String(url)).toContain(
      '/api/ecommerce/settlements/sellers/acme-corp/balance',
    );
  });
});
