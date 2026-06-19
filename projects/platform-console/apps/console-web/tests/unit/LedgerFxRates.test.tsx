import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { FxRatesTable } from '@/features/ledger-ops/components/FxRatesTable';
import type { FxRatesResponse } from '@/features/ledger-ops';

/**
 * `features/ledger-ops` FX 환율 피드 대시보드 surface (TASK-PC-FE-092 —
 * READ-ONLY display; TASK-MONO-300 — manual refresh action):
 *   - FxRatesTable renders the feedEnabled badge (both states);
 *   - stale rows get the warning highlight style + a "STALE" badge;
 *   - fresh rows get the "최신" badge;
 *   - empty rates array → empty-state message (NOT a 404 / error);
 *   - `rate` is rendered verbatim as the exact string (F5 — proves no
 *     Number()/parseFloat() coercion — the precision string appears verbatim);
 *   - the refresh button calls onRefresh when clicked;
 *   - `refreshing=true` disables the button + shows "새로고침 중…" (TASK-MONO-300).
 */

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

const FX_RATES_ENABLED: FxRatesResponse = {
  feedEnabled: true,
  rates: [
    {
      baseCurrency: 'KRW',
      foreignCurrency: 'USD',
      // High-precision decimal — F5 must survive verbatim in the DOM.
      rate: '1300.12345678',
      asOf: '2026-06-15T00:00:00Z',
      source: 'ECB',
      fetchedAt: '2026-06-15T00:01:00Z',
      ageSeconds: 60,
      stale: false,
    },
    {
      baseCurrency: 'KRW',
      foreignCurrency: 'JPY',
      rate: '8.76543210',
      asOf: '2026-06-14T00:00:00Z',
      source: 'FIXER',
      fetchedAt: '2026-06-14T00:01:00Z',
      // stale — this row should get warning highlight.
      ageSeconds: 90000,
      stale: true,
    },
  ],
};

const FX_RATES_DISABLED: FxRatesResponse = {
  feedEnabled: false,
  rates: [],
};

const FX_RATES_EMPTY: FxRatesResponse = {
  feedEnabled: true,
  rates: [],
};

beforeEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// FxRatesTable — feedEnabled badge
// ---------------------------------------------------------------------------

describe('FxRatesTable — feedEnabled badge (both states)', () => {
  it('feedEnabled=true → badge reads "피드 활성"', () => {
    render(<FxRatesTable data={FX_RATES_ENABLED} />, { wrapper: wrapper() });
    const badge = screen.getByTestId('ledger-fx-rates-feed-badge');
    expect(badge).toBeInTheDocument();
    expect(badge.textContent).toContain('피드 활성');
    expect(badge.textContent).not.toContain('비활성');
  });

  it('feedEnabled=false → badge reads "피드 비활성" with fallback warning', () => {
    render(<FxRatesTable data={FX_RATES_DISABLED} />, { wrapper: wrapper() });
    const badge = screen.getByTestId('ledger-fx-rates-feed-badge');
    expect(badge).toBeInTheDocument();
    expect(badge.textContent).toContain('피드 비활성');
    expect(badge.textContent).toContain('환율 폴백이 꺼져 있습니다');
  });
});

// ---------------------------------------------------------------------------
// FxRatesTable — table rendering, stale row highlight, fresh row
// ---------------------------------------------------------------------------

describe('FxRatesTable — table rows (stale highlight + fresh badge)', () => {
  it('renders the table with data-testid ledger-fx-rates-table when rates are present', () => {
    render(<FxRatesTable data={FX_RATES_ENABLED} />, { wrapper: wrapper() });
    expect(screen.getByTestId('ledger-fx-rates-table')).toBeInTheDocument();
  });

  it('row 0 (fresh) is rendered with the "최신" badge and NO warning highlight class', () => {
    render(<FxRatesTable data={FX_RATES_ENABLED} />, { wrapper: wrapper() });
    const table = screen.getByTestId('ledger-fx-rates-table');
    const row0 = within(table).getByTestId('ledger-fx-rates-row-0');
    expect(row0).toBeInTheDocument();
    // Fresh row — no stale warning style.
    expect(row0.className).not.toContain('bg-yellow-50');
    // "최신" badge present somewhere in the row.
    expect(row0.textContent).toContain('최신');
    expect(row0.textContent).not.toContain('STALE');
  });

  it('row 1 (stale) gets the warning highlight class + STALE badge', () => {
    render(<FxRatesTable data={FX_RATES_ENABLED} />, { wrapper: wrapper() });
    const table = screen.getByTestId('ledger-fx-rates-table');
    const row1 = within(table).getByTestId('ledger-fx-rates-row-1');
    expect(row1).toBeInTheDocument();
    // Stale row — warning style applied.
    expect(row1.className).toContain('bg-yellow-50');
    expect(row1.textContent).toContain('STALE');
    expect(row1.textContent).not.toContain('최신');
  });

  it('currency pair column shows baseCurrency/foreignCurrency (e.g. "KRW/USD")', () => {
    render(<FxRatesTable data={FX_RATES_ENABLED} />, { wrapper: wrapper() });
    const table = screen.getByTestId('ledger-fx-rates-table');
    const row0 = within(table).getByTestId('ledger-fx-rates-row-0');
    expect(row0.textContent).toContain('KRW/USD');
  });

  it('source column is rendered', () => {
    render(<FxRatesTable data={FX_RATES_ENABLED} />, { wrapper: wrapper() });
    const table = screen.getByTestId('ledger-fx-rates-table');
    const row0 = within(table).getByTestId('ledger-fx-rates-row-0');
    expect(row0.textContent).toContain('ECB');
  });
});

// ---------------------------------------------------------------------------
// FxRatesTable — F5 rate string invariant (NO Number coercion)
// ---------------------------------------------------------------------------

describe('FxRatesTable — F5 rate string invariant (verbatim render, no Number coercion)', () => {
  it('rate "1300.12345678" appears verbatim in the DOM (high precision preserved)', () => {
    render(<FxRatesTable data={FX_RATES_ENABLED} />, { wrapper: wrapper() });
    const table = screen.getByTestId('ledger-fx-rates-table');
    const row0 = within(table).getByTestId('ledger-fx-rates-row-0');
    // The exact string must appear in the row — proves no Number() coercion
    // (parseFloat("1300.12345678") and then .toString() would drop trailing
    // zeros or change precision). The assertion is intentionally the exact
    // source-string representation.
    expect(row0.textContent).toContain('1300.12345678');
  });

  it('JPY rate "8.76543210" appears verbatim (trailing zero preserved)', () => {
    render(<FxRatesTable data={FX_RATES_ENABLED} />, { wrapper: wrapper() });
    const table = screen.getByTestId('ledger-fx-rates-table');
    const row1 = within(table).getByTestId('ledger-fx-rates-row-1');
    expect(row1.textContent).toContain('8.76543210');
  });
});

// ---------------------------------------------------------------------------
// FxRatesTable — empty state
// ---------------------------------------------------------------------------

describe('FxRatesTable — empty state (rates: [], 200 not 404)', () => {
  it('empty rates → data-testid ledger-fx-rates-empty with correct message', () => {
    render(<FxRatesTable data={FX_RATES_EMPTY} />, { wrapper: wrapper() });
    const empty = screen.getByTestId('ledger-fx-rates-empty');
    expect(empty).toBeInTheDocument();
    expect(empty.textContent).toContain('적재된 환율 quote 가 없습니다.');
    // No table rendered for empty state.
    expect(screen.queryByTestId('ledger-fx-rates-table')).toBeNull();
  });

  it('feedEnabled badge still renders for empty rates', () => {
    render(<FxRatesTable data={FX_RATES_EMPTY} />, { wrapper: wrapper() });
    expect(screen.getByTestId('ledger-fx-rates-feed-badge')).toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// FxRatesTable — data=null (no crash)
// ---------------------------------------------------------------------------

describe('FxRatesTable — data=null renders nothing (no crash)', () => {
  it('null data → nothing rendered (no badge, no table, no empty)', () => {
    const { container } = render(<FxRatesTable data={null} />, { wrapper: wrapper() });
    expect(container.firstChild).toBeNull();
    expect(screen.queryByTestId('ledger-fx-rates-feed-badge')).toBeNull();
    expect(screen.queryByTestId('ledger-fx-rates-table')).toBeNull();
    expect(screen.queryByTestId('ledger-fx-rates-empty')).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// FxRatesTable — refresh button
// ---------------------------------------------------------------------------

describe('FxRatesTable — refresh button calls onRefresh', () => {
  it('clicking the refresh button calls onRefresh once', () => {
    const onRefresh = vi.fn();
    render(<FxRatesTable data={FX_RATES_ENABLED} onRefresh={onRefresh} />, {
      wrapper: wrapper(),
    });
    const refreshBtn = screen.getByTestId('ledger-fx-rates-refresh');
    expect(refreshBtn).toBeInTheDocument();
    fireEvent.click(refreshBtn);
    expect(onRefresh).toHaveBeenCalledTimes(1);
  });

  it('refresh button is rendered even with empty rates', () => {
    const onRefresh = vi.fn();
    render(<FxRatesTable data={FX_RATES_EMPTY} onRefresh={onRefresh} />, {
      wrapper: wrapper(),
    });
    expect(screen.getByTestId('ledger-fx-rates-refresh')).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('ledger-fx-rates-refresh'));
    expect(onRefresh).toHaveBeenCalledTimes(1);
  });
});

// ---------------------------------------------------------------------------
// FxRatesTable — refreshing prop (TASK-MONO-300 loading/disabled state)
// ---------------------------------------------------------------------------

describe('FxRatesTable — refreshing prop (TASK-MONO-300 in-flight guard)', () => {
  it('refreshing=true disables the button and shows "새로고침 중…"', () => {
    const onRefresh = vi.fn();
    render(
      <FxRatesTable data={FX_RATES_ENABLED} onRefresh={onRefresh} refreshing />,
      { wrapper: wrapper() },
    );
    const btn = screen.getByTestId('ledger-fx-rates-refresh');
    expect(btn).toBeDisabled();
    expect(btn).toHaveAttribute('aria-busy', 'true');
    expect(btn.textContent).toContain('새로고침 중…');
    // Click should not fire onRefresh when disabled.
    fireEvent.click(btn);
    expect(onRefresh).not.toHaveBeenCalled();
  });

  it('refreshing=false (default) shows "새로고침" + is enabled', () => {
    const onRefresh = vi.fn();
    render(
      <FxRatesTable data={FX_RATES_ENABLED} onRefresh={onRefresh} refreshing={false} />,
      { wrapper: wrapper() },
    );
    const btn = screen.getByTestId('ledger-fx-rates-refresh');
    expect(btn).not.toBeDisabled();
    expect(btn.textContent).toContain('새로고침');
    expect(btn.textContent).not.toContain('중…');
  });

  it('refreshing=true also disables with empty rates', () => {
    render(
      <FxRatesTable data={FX_RATES_EMPTY} refreshing />,
      { wrapper: wrapper() },
    );
    const btn = screen.getByTestId('ledger-fx-rates-refresh');
    expect(btn).toBeDisabled();
    expect(btn.textContent).toContain('새로고침 중…');
  });
});
