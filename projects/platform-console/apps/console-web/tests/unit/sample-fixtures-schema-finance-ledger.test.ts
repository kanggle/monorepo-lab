/**
 * finance + ledger domain sample fixtures parse with the SAME production
 * schemas the real screens use, and behave like the real producer over
 * filters/pagination/detail lookups (TASK-PC-FE-286 AC-1 / AC-3 / AC-4 / AC-5).
 *
 * 🔴 AC-1 — a fixture that broke its schema would render as the section
 *    degrade state, indistinguishable from "broken" (task Failure Scenario).
 *    Every assertion below goes THROUGH `sampleResponse` (not the raw seed
 *    arrays) so routing, status codes and the query-string handling are
 *    exercised too, not just the shape of the data.
 * 🔴🔴 AC-3 — the coordinator's three prior reviews (284/285) each found the
 *    SAME defect class: a screen that looks right in isolation but
 *    contradicts a sibling screen. This ledger's whole reason to exist is
 *    double-entry arithmetic, so every invariant below is checked ACROSS
 *    views, THROUGH the router — never by re-deriving from the internal seed
 *    arrays (that would test the test, not the screens):
 *      - trial balance: Σ base debit === Σ base credit (inBalance)
 *      - per account: the journal LINES returned by `.../accounts/{code}/entries`
 *        sum (by direction) to EXACTLY `.../accounts/{code}/balance`'s
 *        debit/credit totals AND to the matching trial-balance row
 *      - the reconciliation statement's `matchedCount`/`discrepancyCount`
 *        equal its own `matches`/`discrepancies` array lengths, and its
 *        discrepancy is the SAME object `.../discrepancies/{id}` answers
 *      - the FX position lot is the SAME acquisition as its source journal
 *        entry (`sourceJournalEntryId`)
 * 🔴 AC-4 — money representation: every amount is an F5 minor-units STRING;
 *    KRW is scale 0, USD is scale 2 (a fixture-internal cross-currency entry
 *    exercises `exchangeRate` + `baseAmount` together).
 */
import { describe, it, expect } from 'vitest';
import { sampleResponse } from '@/shared/sample/router';
import { SAMPLE_READ_ONLY } from '@/shared/sample/codes';
import { messageForCode } from '@/shared/api/errors';
import {
  AccountSchema,
  BalancesResponseSchema,
} from '@/shared/api/finance-accounts-types';
import { TransactionsResponseSchema } from '@/features/finance-ops/api/types';
import {
  TrialBalanceSchema,
  JournalEntrySchema,
  PeriodSchema,
  PeriodsResponseSchema,
  DiscrepanciesResponseSchema,
  DiscrepancySchema,
  StatementSchema,
  AccountBalanceSchema,
  AccountEntriesResponseSchema,
  PositionLotsResponseSchema,
  FxRatesResponseSchema,
  FxRateHistoryResponseSchema,
} from '@/features/ledger-ops/api/types';

function get(surface: string, path: string): Response {
  return sampleResponse({ core: 'flat', surface, method: 'GET', path });
}
const finance = (path: string) => get('finance', path);
const ledger = (path: string) => get('ledger', path);

const SAMPLE_ACCOUNT_ID = 'sample-account-0001';

describe('Edge Case 1 — the sample registry names a default finance account this fixture actually has', () => {
  it("registry finance.operatorContext.defaultAccountId resolves through /finance/accounts' own fixture", async () => {
    const registryRes = sampleResponse({
      core: 'registry',
      surface: 'registry',
      method: 'GET',
      path: '/',
    });
    const registry = (await registryRes.json()) as {
      products: { productKey: string; operatorContext?: { defaultAccountId?: string } }[];
    };
    const financeProduct = registry.products.find((p) => p.productKey === 'finance');
    expect(financeProduct?.operatorContext?.defaultAccountId).toBe(SAMPLE_ACCOUNT_ID);

    const detailRes = finance(`/api/finance/accounts/${financeProduct!.operatorContext!.defaultAccountId}`);
    expect(detailRes.status).toBe(200);
  });
});

describe('finance — account (AC-1)', () => {
  it('detail parses with AccountSchema and its embedded balances match GET .../balances byte-for-byte', async () => {
    const detailRes = finance(`/api/finance/accounts/${SAMPLE_ACCOUNT_ID}`);
    expect(detailRes.status).toBe(200);
    const detail = AccountSchema.parse((await detailRes.json() as { data: unknown }).data);
    expect(detail.accountId).toBe(SAMPLE_ACCOUNT_ID);

    const balancesRes = finance(`/api/finance/accounts/${SAMPLE_ACCOUNT_ID}/balances`);
    const balances = BalancesResponseSchema.parse(await balancesRes.json());
    // 🔴 the same two-screens-disagree defect class (TASK-PC-FE-284
    // settlement CORRECTION) applied to finance's own account+balances pair.
    expect(detail.balances).toEqual(balances.data);
  });

  it("AC-3 — the balance's ledger = available + held (a summary tile = the rows it summarises)", async () => {
    const res = finance(`/api/finance/accounts/${SAMPLE_ACCOUNT_ID}/balances`);
    const parsed = BalancesResponseSchema.parse(await res.json());
    const row = parsed.data[0];
    expect(BigInt(row.available) + BigInt(row.held)).toBe(BigInt(row.ledger));
  });

  it('an unknown account id is a real 404 ACCOUNT_NOT_FOUND (not a generic degrade)', async () => {
    const res = finance('/api/finance/accounts/no-such-account');
    expect(res.status).toBe(404);
    const body = (await res.json()) as { code?: string; message?: string; timestamp?: string };
    expect(body.code).toBe('ACCOUNT_NOT_FOUND');
    expect(typeof body.message).toBe('string');
    expect(typeof body.timestamp).toBe('string');

    const balancesRes = finance('/api/finance/accounts/no-such-account/balances');
    expect(balancesRes.status).toBe(404);
    const txRes = finance('/api/finance/accounts/no-such-account/transactions');
    expect(txRes.status).toBe(404);
  });
});

describe('finance — transactions (AC-1 / AC-4)', () => {
  it('list parses with TransactionsResponseSchema; totalElements matches content length', async () => {
    const res = TransactionsResponseSchema.parse(
      await (await finance(`/api/finance/accounts/${SAMPLE_ACCOUNT_ID}/transactions?page=0&size=20`)).json(),
    );
    expect(res.meta.totalElements).toBe(res.data.length);
    expect(res.meta.totalElements).toBeGreaterThan(0);
  });

  it('type=CREDIT and status=FAILED each narrow to a proper, non-empty, non-vacuous subset', async () => {
    const all = TransactionsResponseSchema.parse(
      await (await finance(`/api/finance/accounts/${SAMPLE_ACCOUNT_ID}/transactions?page=0&size=20`)).json(),
    );
    const credits = TransactionsResponseSchema.parse(
      await (
        await finance(`/api/finance/accounts/${SAMPLE_ACCOUNT_ID}/transactions?type=CREDIT&page=0&size=20`)
      ).json(),
    );
    expect(credits.meta.totalElements).toBeGreaterThan(0);
    expect(credits.meta.totalElements).toBeLessThan(all.meta.totalElements ?? 0);
    expect(credits.data.every((t) => t.type === 'CREDIT')).toBe(true);

    const failed = TransactionsResponseSchema.parse(
      await (
        await finance(`/api/finance/accounts/${SAMPLE_ACCOUNT_ID}/transactions?status=FAILED&page=0&size=20`)
      ).json(),
    );
    expect(failed.meta.totalElements).toBeGreaterThan(0);
    expect(failed.data.every((t) => t.status === 'FAILED')).toBe(true);
  });

  it('AC-4 — every money amount is an F5 minor-units integer STRING, never a number', async () => {
    const all = TransactionsResponseSchema.parse(
      await (await finance(`/api/finance/accounts/${SAMPLE_ACCOUNT_ID}/transactions?page=0&size=20`)).json(),
    );
    for (const t of all.data) {
      expect(typeof t.money.amount).toBe('string');
      expect(t.money.amount).toMatch(/^-?\d+$/);
      expect(t.money.currency).toBe('KRW'); // scale 0 — one minor unit is one 원
    }
  });
});

describe('ledger — trial balance (AC-1 / AC-3)', () => {
  it('parses with TrialBalanceSchema; base debit total === base credit total (inBalance)', async () => {
    const res = ledger('/api/finance/ledger/trial-balance');
    expect(res.status).toBe(200);
    const body = (await res.json()) as { data: unknown };
    const tb = TrialBalanceSchema.parse(body.data);
    expect(tb.inBalance).toBe(true);
    expect(tb.grandBaseDebitTotal.amount).toBe(tb.grandBaseCreditTotal.amount);
    // 🔴🔴 non-vacuity — a trial balance that is "in balance" because it is
    // EMPTY would trivially pass; assert real, non-zero money moved.
    expect(BigInt(tb.grandBaseDebitTotal.amount)).toBeGreaterThan(0n);
    expect(tb.accounts.length).toBeGreaterThanOrEqual(4);
  });

  it('AC-4 — the FX_USD_HOLDING row carries its OWN currency (USD) totals, distinct from its KRW base totals', async () => {
    const res = ledger('/api/finance/ledger/trial-balance');
    const tb = TrialBalanceSchema.parse((await res.json() as { data: unknown }).data);
    const fx = tb.accounts.find((a) => a.ledgerAccountCode === 'FX_USD_HOLDING')!;
    expect(fx).toBeTruthy();
    expect(fx.debitTotal.currency).toBe('USD');
    expect(fx.debitTotal.amount).toBe('10000'); // $100.00 in USD minor units (cents)
    expect(fx.baseDebitTotal.currency).toBe('KRW');
    expect(fx.baseDebitTotal.amount).toBe('130000'); // 130,000 원 (exchangeRate 1300)
  });
});

describe('ledger — journal entry (AC-1)', () => {
  it('entry-sample-0003 parses with JournalEntrySchema and is balanced with a real FX line triple', async () => {
    const res = ledger('/api/finance/ledger/entries/entry-sample-0003');
    expect(res.status).toBe(200);
    const entry = JournalEntrySchema.parse((await res.json() as { data: unknown }).data);
    expect(entry.balanced).toBe(true);
    const fxLine = entry.lines.find((l) => l.ledgerAccountCode === 'FX_USD_HOLDING')!;
    expect(fxLine.money.currency).toBe('USD');
    expect(fxLine.exchangeRate).toBe('1300');
    expect(fxLine.baseAmount.currency).toBe('KRW');
    expect(fxLine.baseAmount.amount).toBe('130000');
  });

  it('an unknown entry id is a plain 404 JOURNAL_ENTRY_NOT_FOUND', async () => {
    const res = ledger('/api/finance/ledger/entries/no-such-entry');
    expect(res.status).toBe(404);
    expect(((await res.json()) as { code?: string }).code).toBe('JOURNAL_ENTRY_NOT_FOUND');
  });
});

describe('ledger — account balance + entries (AC-1 / AC-3 double entry, THROUGH the router)', () => {
  const ACCOUNT_CODES = ['CASH', 'AR', 'REVENUE', 'FX_USD_HOLDING'] as const;

  it.each(ACCOUNT_CODES)(
    "%s — the journal lines' sum (by direction) equals BOTH the account balance AND the trial-balance row",
    async (code) => {
      const entriesRes = ledger(`/api/finance/ledger/accounts/${code}/entries?page=0&size=20`);
      expect(entriesRes.status).toBe(200);
      const entries = AccountEntriesResponseSchema.parse(await entriesRes.json());
      const debitSum = entries.data
        .filter((l) => l.direction === 'DEBIT')
        .reduce((acc, l) => acc + BigInt(l.money.amount), 0n);
      const creditSum = entries.data
        .filter((l) => l.direction === 'CREDIT')
        .reduce((acc, l) => acc + BigInt(l.money.amount), 0n);

      const balanceRes = ledger(`/api/finance/ledger/accounts/${code}/balance`);
      expect(balanceRes.status).toBe(200);
      const balance = AccountBalanceSchema.parse((await balanceRes.json() as { data: unknown }).data);
      expect(BigInt(balance.debitTotal.amount)).toBe(debitSum);
      expect(BigInt(balance.creditTotal.amount)).toBe(creditSum);

      const tbRes = ledger('/api/finance/ledger/trial-balance');
      const tb = TrialBalanceSchema.parse((await tbRes.json() as { data: unknown }).data);
      const row = tb.accounts.find((a) => a.ledgerAccountCode === code)!;
      expect(row, code).toBeTruthy();
      expect(BigInt(row.debitTotal.amount)).toBe(debitSum);
      expect(BigInt(row.creditTotal.amount)).toBe(creditSum);
    },
  );

  it('an unknown ledger account code is a plain 404 LEDGER_ACCOUNT_NOT_FOUND (both balance and entries)', async () => {
    const balanceRes = ledger('/api/finance/ledger/accounts/NO_SUCH_ACCOUNT/balance');
    expect(balanceRes.status).toBe(404);
    expect(((await balanceRes.json()) as { code?: string }).code).toBe('LEDGER_ACCOUNT_NOT_FOUND');
    const entriesRes = ledger('/api/finance/ledger/accounts/NO_SUCH_ACCOUNT/entries');
    expect(entriesRes.status).toBe(404);
    expect(((await entriesRes.json()) as { code?: string }).code).toBe('LEDGER_ACCOUNT_NOT_FOUND');
  });
});

describe('ledger — accounting periods (AC-1)', () => {
  it('list parses with PeriodsResponseSchema, carries NO snapshot field, most-recent first', async () => {
    const res = PeriodsResponseSchema.parse(
      await (await ledger('/api/finance/ledger/periods?page=0&size=20')).json(),
    );
    expect(res.meta.totalElements).toBe(2);
    expect(res.data[0].periodId).toBe('period-sample-0002'); // OPEN, most recent
    expect(res.data.every((p) => p.snapshot === undefined)).toBe(true);
  });

  it('CLOSED period detail carries an in-balance snapshot; OPEN period detail carries none (not an error)', async () => {
    const closed = PeriodSchema.parse(
      await (await ledger('/api/finance/ledger/periods/period-sample-0001')).json().then((j) => (j as { data: unknown }).data),
    );
    expect(closed.status).toBe('CLOSED');
    expect(closed.snapshot?.inBalance).toBe(true);
    expect(closed.snapshot?.grandDebitTotal.amount).toBe(closed.snapshot?.grandCreditTotal.amount);

    const openRes = ledger('/api/finance/ledger/periods/period-sample-0002');
    expect(openRes.status).toBe(200);
    const open = PeriodSchema.parse((await openRes.json() as { data: unknown }).data);
    expect(open.status).toBe('OPEN');
    expect(open.snapshot).toBeNull();
  });

  it('an unknown period id is a plain 404 ACCOUNTING_PERIOD_NOT_FOUND', async () => {
    const res = ledger('/api/finance/ledger/periods/no-such-period');
    expect(res.status).toBe(404);
    expect(((await res.json()) as { code?: string }).code).toBe('ACCOUNTING_PERIOD_NOT_FOUND');
  });
});

describe('ledger — reconciliation discrepancies (AC-1 / AC-3)', () => {
  it('status=OPEN and status=RESOLVED each narrow to a disjoint, non-empty subset', async () => {
    const open = DiscrepanciesResponseSchema.parse(
      await (await ledger('/api/finance/ledger/reconciliation/discrepancies?status=OPEN&page=0&size=20')).json(),
    );
    const resolved = DiscrepanciesResponseSchema.parse(
      await (
        await ledger('/api/finance/ledger/reconciliation/discrepancies?status=RESOLVED&page=0&size=20')
      ).json(),
    );
    expect(open.meta.totalElements).toBeGreaterThan(0);
    expect(resolved.meta.totalElements).toBeGreaterThan(0);
    expect(open.data.every((d) => d.status === 'OPEN')).toBe(true);
    expect(resolved.data.every((d) => d.status === 'RESOLVED')).toBe(true);
  });

  it('AC-3 — the OPEN discrepancy references a REAL journal entry that really exists', async () => {
    const detail = DiscrepancySchema.parse(
      await (
        await ledger('/api/finance/ledger/reconciliation/discrepancies/discrepancy-sample-0001')
      ).json().then((j) => (j as { data: unknown }).data),
    );
    expect(detail.journalEntryId).toBeTruthy();
    const entryRes = ledger(`/api/finance/ledger/entries/${detail.journalEntryId}`);
    expect(entryRes.status).toBe(200);
  });

  it('the RESOLVED discrepancy carries a resolution sub-object', async () => {
    const res = ledger('/api/finance/ledger/reconciliation/discrepancies/discrepancy-sample-0002');
    const detail = DiscrepancySchema.parse((await res.json() as { data: unknown }).data);
    expect(detail.status).toBe('RESOLVED');
    expect(detail.resolution?.resolutionType).toBe('WRITTEN_OFF');
  });

  it('an unknown discrepancy id is a plain 404 RECONCILIATION_DISCREPANCY_NOT_FOUND', async () => {
    const res = ledger('/api/finance/ledger/reconciliation/discrepancies/no-such-discrepancy');
    expect(res.status).toBe(404);
    expect(((await res.json()) as { code?: string }).code).toBe('RECONCILIATION_DISCREPANCY_NOT_FOUND');
  });
});

describe('ledger — reconciliation statement (AC-1 / AC-3)', () => {
  it("AC-3 — matchedCount/discrepancyCount equal the statement's OWN matches/discrepancies array lengths", async () => {
    const res = ledger('/api/finance/ledger/reconciliation/statements/statement-sample-0001');
    expect(res.status).toBe(200);
    const statement = StatementSchema.parse((await res.json() as { data: unknown }).data);
    expect(statement.matchedCount).toBe(statement.matches.length);
    expect(statement.discrepancyCount).toBe(statement.discrepancies.length);
    expect(statement.matches.length).toBeGreaterThan(0);
    expect(statement.discrepancies.length).toBeGreaterThan(0);
  });

  it("AC-3 — the statement's discrepancy is the SAME object .../discrepancies/{id} answers", async () => {
    const stRes = ledger('/api/finance/ledger/reconciliation/statements/statement-sample-0001');
    const statement = StatementSchema.parse((await stRes.json() as { data: unknown }).data);
    const embedded = statement.discrepancies[0];
    const standaloneRes = ledger(
      `/api/finance/ledger/reconciliation/discrepancies/${embedded.discrepancyId}`,
    );
    const standalone = DiscrepancySchema.parse((await standaloneRes.json() as { data: unknown }).data);
    expect(embedded).toEqual(standalone);
  });

  it("AC-3 — every statement match's journalEntryId resolves to a real journal entry", async () => {
    const res = ledger('/api/finance/ledger/reconciliation/statements/statement-sample-0001');
    const statement = StatementSchema.parse((await res.json() as { data: unknown }).data);
    for (const m of statement.matches) {
      const entryRes = ledger(`/api/finance/ledger/entries/${m.journalEntryId}`);
      expect(entryRes.status, m.journalEntryId).toBe(200);
    }
  });

  it('an unknown statement id is a plain 404 RECONCILIATION_STATEMENT_NOT_FOUND', async () => {
    const res = ledger('/api/finance/ledger/reconciliation/statements/no-such-statement');
    expect(res.status).toBe(404);
    expect(((await res.json()) as { code?: string }).code).toBe('RECONCILIATION_STATEMENT_NOT_FOUND');
  });
});

describe('ledger — FX position lots (AC-1 / AC-3)', () => {
  it('AC-3 — the FX_USD_HOLDING/USD position is the SAME acquisition as entry-sample-0003 (sourceJournalEntryId)', async () => {
    const res = ledger('/api/finance/ledger/settlements/FX_USD_HOLDING/USD/lots');
    expect(res.status).toBe(200);
    const lots = PositionLotsResponseSchema.parse((await res.json() as { data: unknown }).data);
    expect(lots.lotCount).toBe(1);
    expect(lots.lots[0].sourceJournalEntryId).toBe('entry-sample-0003');
    const entryRes = ledger(`/api/finance/ledger/entries/${lots.lots[0].sourceJournalEntryId}`);
    const entry = await entryRes.json().then((j) => (j as { data: { lines: { ledgerAccountCode: string; money: { amount: string; currency: string } }[] } }).data);
    const fxLine = entry.lines.find((l) => l.ledgerAccountCode === 'FX_USD_HOLDING')!;
    // the lot's ORIGINAL foreign amount equals the entry line's own money amount.
    expect(lots.lots[0].originalForeignMinor).toBe(fxLine.money.amount);
  });

  it('an unknown (code, currency) pair is a normal 200 empty-state, never a 404', async () => {
    const res = ledger('/api/finance/ledger/settlements/CASH/EUR/lots');
    expect(res.status).toBe(200);
    const lots = PositionLotsResponseSchema.parse((await res.json() as { data: unknown }).data);
    expect(lots.lotCount).toBe(0);
    expect(lots.lots).toEqual([]);
    expect(lots.totalRemainingForeignMinor).toBe('0');
  });
});

describe('ledger — FX rate feed + history (AC-1 / Edge Case 2)', () => {
  it('feed cache parses with FxRatesResponseSchema; a stale rate is surfaced honestly, not hidden', async () => {
    const res = ledger('/api/finance/ledger/fx-rates');
    expect(res.status).toBe(200);
    const feed = FxRatesResponseSchema.parse((await res.json() as { data: unknown }).data);
    expect(feed.feedEnabled).toBe(true);
    expect(feed.rates.length).toBeGreaterThan(0);
    expect(feed.rates.some((r) => r.stale)).toBe(true);
    expect(feed.rates.some((r) => !r.stale)).toBe(true);
  });

  it('Edge Case 2 — every asOf/fetchedAt is NOT in the future relative to now (TZ=UTC)', async () => {
    const res = ledger('/api/finance/ledger/fx-rates');
    const feed = FxRatesResponseSchema.parse((await res.json() as { data: unknown }).data);
    const now = Date.now();
    for (const r of feed.rates) {
      expect(new Date(r.asOf).getTime(), r.foreignCurrency).toBeLessThanOrEqual(now);
      expect(new Date(r.fetchedAt).getTime(), r.foreignCurrency).toBeLessThanOrEqual(now);
    }
  });

  it('history for a known pair (USD) is ordered newest-first; an unknown pair is a 200 empty list, never a 404', async () => {
    const res = ledger('/api/finance/ledger/fx-rates/USD/history');
    expect(res.status).toBe(200);
    const history = FxRateHistoryResponseSchema.parse((await res.json() as { data: unknown }).data);
    expect(history.base).toBe('KRW');
    expect(history.quotes.length).toBeGreaterThan(1);
    const times = history.quotes.map((q) => new Date(q.asOf).getTime());
    expect(times).toEqual([...times].sort((a, b) => b - a));

    const unknownRes = ledger('/api/finance/ledger/fx-rates/GBP/history');
    expect(unknownRes.status).toBe(200);
    const unknown = FxRateHistoryResponseSchema.parse((await unknownRes.json() as { data: unknown }).data);
    expect(unknown.quotes).toEqual([]);
  });
});

describe('AC-5 — a representative write is refused with the sample copy', () => {
  it('POST reconciliation discrepancy resolve → 403 SAMPLE_READ_ONLY → the R1ⓐ copy via messageForCode', async () => {
    const res = sampleResponse({
      core: 'flat',
      surface: 'ledger',
      method: 'POST',
      path: '/api/finance/ledger/reconciliation/discrepancies/discrepancy-sample-0001/resolve',
    });
    expect(res.status).toBe(403);
    const body = (await res.json()) as { code: string };
    expect(body.code).toBe(SAMPLE_READ_ONLY);
    expect(messageForCode(body.code)).toBe(
      '샘플 화면에서는 실행되지 않습니다. 로그인하면 실제로 실행됩니다',
    );
  });

  it('POST fx-rates refresh → 403 SAMPLE_READ_ONLY', async () => {
    const res = sampleResponse({
      core: 'flat',
      surface: 'ledger',
      method: 'POST',
      path: '/api/finance/ledger/fx-rates/refresh',
    });
    expect(res.status).toBe(403);
    expect(((await res.json()) as { code: string }).code).toBe(SAMPLE_READ_ONLY);
  });
});
