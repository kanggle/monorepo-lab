import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import {
  SettlementsScreen,
  PeriodPayoutsScreen,
} from '@/features/ecommerce-ops';
import type {
  AccrualsResponse,
  PeriodsResponse,
  PayoutsResponse,
} from '@/features/ecommerce-ops';

/**
 * Settlement mutation UI (TASK-PC-FE-221 Phase B): every mutation is
 * confirm-gated (no fetch until the confirm), client validation guards, and a
 * 409 surfaces inline in the dialog.
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

const EMPTY_ACCRUALS: AccrualsResponse = {
  items: [],
  page: 0,
  size: 20,
  totalElements: 0,
};
const OPEN_PERIODS: PeriodsResponse = {
  items: [
    {
      periodId: '2026-07',
      from: '2026-07-01T00:00:00Z',
      to: '2026-08-01T00:00:00Z',
      status: 'OPEN',
      closedAt: null,
      sellerCount: 2,
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
};
const PENDING_PAYOUTS: PayoutsResponse = {
  items: [
    {
      payoutId: 'po-1',
      sellerId: 'acme-corp',
      payableNetMinor: 90000,
      commissionMinor: 10000,
      accrualCount: 1,
      status: 'PENDING',
      payoutReference: null,
      paidAt: null,
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function ecomError(code: string, status: number) {
  return new Response(JSON.stringify({ code, message: 'e', timestamp: 't' }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('commission-rate SET form — validation + confirm gate', () => {
  it('rejects an out-of-range bps (submit disabled + range error)', async () => {
    render(<SettlementsScreen accruals={EMPTY_ACCRUALS} periods={OPEN_PERIODS} />, {
      wrapper: wrapper(),
    });
    await userEvent.type(
      screen.getByTestId('settlements-rate-set-seller-input'),
      'acme-corp',
    );
    fireEvent.change(screen.getByTestId('settlements-rate-set-bps-input'), {
      target: { value: '99999' },
    });
    expect(
      screen.getByTestId('settlements-rate-set-range-error'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('settlements-rate-set-submit')).toBeDisabled();
  });

  it('does NOT PUT until the confirm is pressed, then PUTs to the right URL/body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ sellerId: 'acme-corp', rateBps: 1500, source: 'SELLER_OVERRIDE' }),
    );
    vi.stubGlobal('fetch', fetchMock);

    render(<SettlementsScreen accruals={EMPTY_ACCRUALS} periods={OPEN_PERIODS} />, {
      wrapper: wrapper(),
    });
    await userEvent.type(
      screen.getByTestId('settlements-rate-set-seller-input'),
      'acme-corp',
    );
    fireEvent.change(screen.getByTestId('settlements-rate-set-bps-input'), {
      target: { value: '1500' },
    });
    await userEvent.click(screen.getByTestId('settlements-rate-set-submit'));

    // confirm dialog open, but NO request yet
    expect(screen.getByTestId('ecommerce-confirm-dialog')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();

    await userEvent.click(screen.getByTestId('ecommerce-confirm-confirm'));
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain(
      '/api/ecommerce/settlements/commission-rates/acme-corp',
    );
    expect((init as RequestInit).method).toBe('PUT');
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      rateBps: 1500,
    });
  });
});

describe('period OPEN form — ordering validation + confirm gate', () => {
  it('shows an ordering error when from >= to (submit disabled)', async () => {
    render(<SettlementsScreen accruals={EMPTY_ACCRUALS} periods={OPEN_PERIODS} />, {
      wrapper: wrapper(),
    });
    fireEvent.change(screen.getByTestId('settlements-period-open-from'), {
      target: { value: '2026-08-01T00:00' },
    });
    fireEvent.change(screen.getByTestId('settlements-period-open-to'), {
      target: { value: '2026-07-01T00:00' },
    });
    expect(
      screen.getByTestId('settlements-period-open-order-error'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('settlements-period-open-submit')).toBeDisabled();
  });

  it('confirm-gates the open POST (ISO body)', async () => {
    const fetchMock = vi.fn((_url: string, init?: RequestInit) =>
      Promise.resolve(
        (init?.method ?? 'GET') === 'GET'
          ? jsonResponse(OPEN_PERIODS)
          : jsonResponse(OPEN_PERIODS.items[0], 201),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    render(<SettlementsScreen accruals={EMPTY_ACCRUALS} periods={OPEN_PERIODS} />, {
      wrapper: wrapper(),
    });
    fireEvent.change(screen.getByTestId('settlements-period-open-from'), {
      target: { value: '2026-07-01T00:00' },
    });
    fireEvent.change(screen.getByTestId('settlements-period-open-to'), {
      target: { value: '2026-08-01T00:00' },
    });
    await userEvent.click(screen.getByTestId('settlements-period-open-submit'));
    expect(fetchMock).not.toHaveBeenCalled();

    await userEvent.click(screen.getByTestId('ecommerce-confirm-confirm'));
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain('/api/ecommerce/settlements/periods');
    expect((init as RequestInit).method).toBe('POST');
    const body = JSON.parse((init as RequestInit).body as string);
    // datetime-local → UTC ISO
    expect(body.from).toMatch(/^\d{4}-\d{2}-\d{2}T.*Z$/);
    expect(body.to).toMatch(/^\d{4}-\d{2}-\d{2}T.*Z$/);
  });
});

describe('period CLOSE — irreversible confirm gate', () => {
  it('OPEN row exposes 마감 → confirm (irreversible) → POST close', async () => {
    const fetchMock = vi.fn((_url: string, init?: RequestInit) =>
      Promise.resolve(
        (init?.method ?? 'GET') === 'GET'
          ? jsonResponse(OPEN_PERIODS)
          : jsonResponse({ ...OPEN_PERIODS.items[0], status: 'CLOSED', payouts: [] }),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    render(<SettlementsScreen accruals={EMPTY_ACCRUALS} periods={OPEN_PERIODS} />, {
      wrapper: wrapper(),
    });
    await userEvent.click(screen.getByTestId('period-close-0'));

    const dialog = screen.getByTestId('ecommerce-confirm-dialog');
    expect(dialog).toHaveTextContent('되돌릴 수 없습니다');
    expect(fetchMock).not.toHaveBeenCalled();

    await userEvent.click(screen.getByTestId('ecommerce-confirm-confirm'));
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain(
      '/api/ecommerce/settlements/periods/2026-07/close',
    );
    expect((init as RequestInit).method).toBe('POST');
  });
});

describe('payout EXECUTE — simulation confirm gate + 409 inline', () => {
  it('surfaces 409 PERIOD_NOT_CLOSED inline in the dialog', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ecomError('PERIOD_NOT_CLOSED', 409));
    vi.stubGlobal('fetch', fetchMock);

    render(
      <PeriodPayoutsScreen periodId="2026-07" initialPayouts={PENDING_PAYOUTS} />,
      { wrapper: wrapper() },
    );
    await userEvent.click(screen.getByTestId('settlements-payouts-execute'));
    expect(screen.getByTestId('ecommerce-confirm-dialog')).toHaveTextContent(
      '시뮬레이션',
    );
    expect(fetchMock).not.toHaveBeenCalled();

    await userEvent.click(screen.getByTestId('ecommerce-confirm-confirm'));
    await waitFor(() =>
      expect(screen.getByTestId('ecommerce-confirm-error')).toBeInTheDocument(),
    );
    expect(screen.getByTestId('ecommerce-confirm-error')).toHaveTextContent(
      '마감되지 않은 기간',
    );
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain(
      '/api/ecommerce/settlements/periods/2026-07/payouts/execute',
    );
    expect((init as RequestInit).method).toBe('POST');
  });
});
