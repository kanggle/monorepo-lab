import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { render, screen, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { TransactionsTable } from '@/features/finance-ops/components/TransactionsTable';
import {
  KNOWN_TXN_TYPES,
  TXN_TYPE_FILTER_OPTIONS,
  type TransactionsResponse,
} from '@/features/finance-ops/api/types';
import { labelForUnknown } from '@/shared/lib/tolerant-label';

/**
 * TASK-PC-FE-370 — the transactions `type` filter offered `CREDIT` / `DEBIT`
 * (render-only labels from `KNOWN_TXN_TYPES`). account-service runs
 * `TransactionType.valueOf` on the value, so those two answered 422
 * `AMOUNT_INVALID`. The filter now offers exactly the producer enum.
 *
 * The enum is read from the Java source, not retyped here: a constant added to
 * or removed from `TransactionType` turns this file red until the console
 * follows.
 */

const ENUM_SOURCE = path.resolve(
  __dirname,
  '..', '..', '..', '..', '..',
  'finance-platform', 'apps', 'account-service', 'src', 'main', 'java',
  'com', 'example', 'finance', 'account', 'domain', 'transaction', 'TransactionType.java',
);

function producerTransactionTypes(): string[] {
  const src = readFileSync(ENUM_SOURCE, 'utf8');
  const body = src.match(/enum\s+TransactionType\s*\{([^}]*)\}/);
  if (!body) throw new Error(`no TransactionType enum body in ${ENUM_SOURCE}`);
  return body[1]
    .replace(/\/\*[\s\S]*?\*\/|\/\/.*$/gm, '')
    .split(/[,;]/)
    .map((s) => s.trim())
    .filter((s) => /^[A-Z][A-Z0-9_]*$/.test(s));
}

const EMPTY_PAGE: TransactionsResponse = {
  data: [],
  meta: { page: 0, size: 20, totalElements: 0 },
} as unknown as TransactionsResponse;

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

describe('TASK-PC-FE-370 — the transactions type filter offers only producer values', () => {
  beforeEach(() => {
    // The seeded first page needs no request; anything that fires one hangs rather than hitting a network.
    vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {})));
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('reads a non-empty enum from TransactionType.java (the comparison below is not vacuous)', () => {
    const producer = producerTransactionTypes();
    expect(producer.length).toBeGreaterThanOrEqual(5);
    expect(producer).toContain('TOPUP');
  });

  it('TXN_TYPE_FILTER_OPTIONS is exactly the producer enum, in declaration order', () => {
    expect([...TXN_TYPE_FILTER_OPTIONS]).toEqual(producerTransactionTypes());
  });

  it('the rendered <select> offers «전체» plus exactly those values — no CREDIT / DEBIT', () => {
    render(<TransactionsTable accountId="acc-1" initial={EMPTY_PAGE} />, { wrapper: wrapper() });
    const select = screen.getByTestId('finance-txn-filter-type');
    const values = within(select)
      .getAllByRole('option')
      .map((o) => (o as HTMLOptionElement).value);
    expect(values[0]).toBe('');
    expect(values.slice(1)).toEqual(producerTransactionTypes());
    expect(values).not.toContain('CREDIT');
    expect(values).not.toContain('DEBIT');
  });

  it('control: the RENDER list is unchanged, so a row of any already-known type keeps its label', () => {
    expect([...KNOWN_TXN_TYPES]).toEqual([
      'TOPUP', 'WITHDRAW', 'HOLD', 'CAPTURE', 'RELEASE', 'TRANSFER', 'REVERSAL', 'CREDIT', 'DEBIT',
    ]);
    for (const t of KNOWN_TXN_TYPES) {
      expect(labelForUnknown(t, KNOWN_TXN_TYPES)).toBe(t);
    }
    // A value outside every list still gets the generic tolerant label, as before.
    expect(labelForUnknown('SOMETHING_NEW', KNOWN_TXN_TYPES)).not.toBe('SOMETHING_NEW');
  });
});
