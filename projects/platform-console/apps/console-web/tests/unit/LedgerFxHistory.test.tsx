import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { FxRateHistoryTable } from '@/features/ledger-ops/components/FxRateHistoryTable';
import { FxRateHistoryLookup } from '@/features/ledger-ops/components/FxRateHistoryLookup';
import type { FxRateHistoryResponse } from '@/features/ledger-ops';

/**
 * `features/ledger-ops` FX 환율 history 드릴 surface (TASK-PC-FE-104 —
 * STRICTLY READ-ONLY):
 *   - FxRateHistoryTable renders the pair heading + the time-series rows
 *     (rate verbatim string — F5, asOf, fetchedAt, source);
 *   - empty quotes array → empty-state message (NOT a 404 / error);
 *   - `rate` is rendered verbatim as the exact string (no Number coercion);
 *   - the refresh button calls onRefresh when clicked;
 *   - data=null → nothing rendered (no crash);
 *   - FxRateHistoryLookup submits the upper-cased trimmed foreign code and
 *     disables submit while empty.
 */

const HISTORY: FxRateHistoryResponse = {
  base: 'KRW',
  foreign: 'USD',
  quotes: [
    {
      // High-precision decimal — F5 must survive verbatim in the DOM.
      rate: '1300.12345678',
      asOf: '2026-06-15T07:00:00Z',
      fetchedAt: '2026-06-15T07:00:05Z',
      source: 'stub',
    },
    {
      rate: '1299.50000000',
      asOf: '2026-06-15T06:00:00Z',
      fetchedAt: '2026-06-15T06:00:05Z',
      source: 'stub',
    },
  ],
};

const HISTORY_EMPTY: FxRateHistoryResponse = {
  base: 'KRW',
  foreign: 'XXX',
  quotes: [],
};

beforeEach(() => {
  vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// FxRateHistoryTable — heading + rows
// ---------------------------------------------------------------------------

describe('FxRateHistoryTable — heading + time-series rows', () => {
  it('renders the pair heading "KRW/USD 환율 이력"', () => {
    render(<FxRateHistoryTable data={HISTORY} />);
    const heading = screen.getByTestId('ledger-fx-history-heading');
    expect(heading).toBeInTheDocument();
    expect(heading.textContent).toContain('KRW/USD');
  });

  it('renders the table with data-testid ledger-fx-history-table when quotes are present', () => {
    render(<FxRateHistoryTable data={HISTORY} />);
    expect(screen.getByTestId('ledger-fx-history-table')).toBeInTheDocument();
  });

  it('row 0 renders rate / asOf / fetchedAt / source', () => {
    render(<FxRateHistoryTable data={HISTORY} />);
    const table = screen.getByTestId('ledger-fx-history-table');
    const row0 = within(table).getByTestId('ledger-fx-history-row-0');
    expect(row0).toBeInTheDocument();
    expect(row0.textContent).toContain('1300.12345678');
    expect(row0.textContent).toContain('2026. 6. 15. 16:00:00');
    expect(row0.textContent).toContain('2026. 6. 15. 16:00:05');
    expect(row0.textContent).toContain('stub');
  });

  it('renders one row per quote (newest-first order preserved verbatim)', () => {
    render(<FxRateHistoryTable data={HISTORY} />);
    const table = screen.getByTestId('ledger-fx-history-table');
    expect(within(table).getByTestId('ledger-fx-history-row-0')).toBeInTheDocument();
    expect(within(table).getByTestId('ledger-fx-history-row-1')).toBeInTheDocument();
    // row 0 = newest (1300...), row 1 = older (1299.5).
    expect(
      within(table).getByTestId('ledger-fx-history-row-1').textContent,
    ).toContain('1299.50000000');
  });
});

// ---------------------------------------------------------------------------
// FxRateHistoryTable — F5 rate string invariant (verbatim render)
// ---------------------------------------------------------------------------

describe('FxRateHistoryTable — F5 rate string invariant (verbatim, no Number coercion)', () => {
  it('rate "1300.12345678" appears verbatim in the DOM (high precision preserved)', () => {
    render(<FxRateHistoryTable data={HISTORY} />);
    const table = screen.getByTestId('ledger-fx-history-table');
    const row0 = within(table).getByTestId('ledger-fx-history-row-0');
    expect(row0.textContent).toContain('1300.12345678');
  });

  it('trailing-zero rate "1299.50000000" is preserved verbatim', () => {
    render(<FxRateHistoryTable data={HISTORY} />);
    const table = screen.getByTestId('ledger-fx-history-table');
    const row1 = within(table).getByTestId('ledger-fx-history-row-1');
    expect(row1.textContent).toContain('1299.50000000');
  });
});

// ---------------------------------------------------------------------------
// FxRateHistoryTable — empty state
// ---------------------------------------------------------------------------

describe('FxRateHistoryTable — empty state (quotes: [], 200 not 404)', () => {
  it('empty quotes → data-testid ledger-fx-history-empty (no table)', () => {
    render(<FxRateHistoryTable data={HISTORY_EMPTY} />);
    const empty = screen.getByTestId('ledger-fx-history-empty');
    expect(empty).toBeInTheDocument();
    expect(empty.textContent).toContain('환율 이력이 없습니다');
    expect(screen.queryByTestId('ledger-fx-history-table')).toBeNull();
  });

  it('heading + refresh still render for empty history', () => {
    render(<FxRateHistoryTable data={HISTORY_EMPTY} />);
    expect(screen.getByTestId('ledger-fx-history-heading')).toBeInTheDocument();
    expect(screen.getByTestId('ledger-fx-history-refresh')).toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// FxRateHistoryTable — data=null + refresh
// ---------------------------------------------------------------------------

describe('FxRateHistoryTable — null data + refresh button', () => {
  it('null data → nothing rendered (no crash)', () => {
    const { container } = render(<FxRateHistoryTable data={null} />);
    expect(container.firstChild).toBeNull();
    expect(screen.queryByTestId('ledger-fx-history-heading')).toBeNull();
    expect(screen.queryByTestId('ledger-fx-history-table')).toBeNull();
  });

  it('clicking the refresh button calls onRefresh once', () => {
    const onRefresh = vi.fn();
    render(<FxRateHistoryTable data={HISTORY} onRefresh={onRefresh} />);
    fireEvent.click(screen.getByTestId('ledger-fx-history-refresh'));
    expect(onRefresh).toHaveBeenCalledTimes(1);
  });
});

// ---------------------------------------------------------------------------
// FxRateHistoryLookup — submit behaviour
// ---------------------------------------------------------------------------

describe('FxRateHistoryLookup — submit (upper-case + trim, disabled while empty)', () => {
  it('submit is disabled when the field is empty', () => {
    render(<FxRateHistoryLookup onSubmit={vi.fn()} />);
    expect(screen.getByTestId('ledger-fx-history-submit')).toBeDisabled();
  });

  it('typing a code enables submit and emits the upper-cased trimmed code', () => {
    const onSubmit = vi.fn();
    render(<FxRateHistoryLookup onSubmit={onSubmit} />);
    const input = screen.getByTestId('ledger-fx-history-currency-input');
    fireEvent.change(input, { target: { value: ' usd ' } });
    const submit = screen.getByTestId('ledger-fx-history-submit');
    expect(submit).not.toBeDisabled();
    fireEvent.click(submit);
    expect(onSubmit).toHaveBeenCalledWith('USD');
  });

  it('seeds the input from initialCurrency', () => {
    render(<FxRateHistoryLookup initialCurrency="JPY" onSubmit={vi.fn()} />);
    const input = screen.getByTestId(
      'ledger-fx-history-currency-input',
    ) as HTMLInputElement;
    expect(input.value).toBe('JPY');
  });
});
