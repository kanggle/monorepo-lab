'use client';

import { useState } from 'react';
import type {
  TrialBalance,
  PeriodsResponse,
  DiscrepanciesResponse,
  JournalEntry,
  AccountBalance,
  AccountEntriesResponse,
  Statement,
} from '../api/types';
import { FX_HISTORY_DEFAULT_LIMIT } from '../api/types';
import {
  useJournalEntry,
  useAccountBalance,
  useAccountEntries,
  useStatement,
  usePositionLots,
  useFxRates,
  useFxRateHistory,
} from '../hooks/use-ledger-ops';
import { ApiError } from '@/shared/api/errors';
import type { TabKey } from './LedgerOpsTabs';

/**
 * Container state for the ledger ops screen (TASK-PC-FE-106 split). Holds the
 * active-tab state (the per-tab queries gate on it), the entry / account /
 * statement / lots / fx-rates / fx-history id-driven queries + their seed
 * reconciliation + derived not-found / forbidden / bad-request flags, and the
 * cross-tab drill handlers. The `LedgerOpsScreen` component is then a pure
 * view that renders the tab strip + the seven panels from this bag. Extracting
 * the logic here keeps the view file readable; no behavior changes (every
 * query/seed/derivation is moved verbatim).
 */

export interface LedgerOpsScreenProps {
  initialEntryId: string | null;
  trialBalance: TrialBalance | null;
  periods: PeriodsResponse | null;
  discrepancies: DiscrepanciesResponse | null;
  initialEntry: JournalEntry | null;
  /** True when the seeded entryId 404'd (JOURNAL_ENTRY_NOT_FOUND) —
   *  rendered inline in the Journal Entry tab; the lookup stays mounted. */
  initialNotFound?: boolean;
  /** The server-seeded account code (TASK-PC-FE-074). */
  initialAccountCode?: string | null;
  /** The server-seeded account balance (TASK-PC-FE-074). */
  initialAccountBalance?: AccountBalance | null;
  /** The server-seeded account entries (TASK-PC-FE-074). */
  initialAccountEntries?: AccountEntriesResponse | null;
  /** True when the seeded accountCode 404'd (LEDGER_ACCOUNT_NOT_FOUND) —
   *  rendered inline in the Account tab; the lookup stays mounted. */
  initialAccountNotFound?: boolean;
  /** The server-seeded statement id (TASK-PC-FE-075). */
  initialStatementId?: string | null;
  /** The server-seeded statement detail (TASK-PC-FE-075). */
  initialStatement?: Statement | null;
  /** True when the seeded statementId 404'd
   *  (RECONCILIATION_STATEMENT_NOT_FOUND) — rendered inline in the 대사
   *  tab; the lookup stays mounted (TASK-PC-FE-075). */
  initialStatementNotFound?: boolean;
}

export function useLedgerOpsState({
  initialEntryId,
  initialEntry,
  initialNotFound = false,
  initialAccountCode = null,
  initialAccountBalance = null,
  initialAccountEntries = null,
  initialAccountNotFound = false,
  initialStatementId = null,
  initialStatement = null,
  initialStatementNotFound = false,
}: LedgerOpsScreenProps) {
  const [active, setActive] = useState<TabKey>(
    initialAccountCode
      ? 'account'
      : initialEntryId
        ? 'entry'
        : initialStatementId
          ? 'reconciliation'
          : 'trial-balance',
  );

  // Periods + reconciliation row-selection (drives the detail reads).
  const [selectedPeriodId, setSelectedPeriodId] = useState<string | null>(null);
  const [selectedDiscrepancyId, setSelectedDiscrepancyId] = useState<
    string | null
  >(null);

  // Journal entry — entry-id-driven. Seeded from the server when provided.
  const [entryId, setEntryId] = useState<string | null>(initialEntryId);
  const entryQ = useJournalEntry(
    entryId,
    entryId && entryId === initialEntryId ? initialEntry ?? undefined : undefined,
  );
  const entry: JournalEntry | null =
    entryQ.data ?? (entryId === initialEntryId ? initialEntry : null);
  const entryApiErr = entryQ.error instanceof ApiError ? entryQ.error : null;
  // notFound: either the server-seeded 404, or a client lookup 404 — but a
  // freshly-typed id that differs from the seed clears the seeded flag.
  const entryNotFound =
    entryApiErr?.status === 404 ||
    (initialNotFound && entryId === initialEntryId);
  const entryForbidden = entryApiErr?.status === 403;

  // Account — account-code-driven (TASK-PC-FE-074). Seeded from the server
  // when an `?accountCode=` query param is supplied.
  const [selectedAccountCode, setSelectedAccountCode] = useState<string | null>(
    initialAccountCode,
  );
  const accountBalanceQ = useAccountBalance(
    selectedAccountCode,
    selectedAccountCode === initialAccountCode
      ? (initialAccountBalance ?? undefined)
      : undefined,
  );
  const accountEntriesQ = useAccountEntries(
    selectedAccountCode,
    { page: 0, size: 20 },
    selectedAccountCode === initialAccountCode
      ? (initialAccountEntries ?? undefined)
      : undefined,
  );
  const accountBalance: AccountBalance | null =
    accountBalanceQ.data ??
    (selectedAccountCode === initialAccountCode ? initialAccountBalance : null);
  const accountEntries: AccountEntriesResponse | null =
    accountEntriesQ.data ??
    (selectedAccountCode === initialAccountCode ? initialAccountEntries : null);
  const accountApiErr =
    accountBalanceQ.error instanceof ApiError ? accountBalanceQ.error : null;
  const accountNotFound =
    accountApiErr?.status === 404 ||
    (initialAccountNotFound && selectedAccountCode === initialAccountCode);

  // Statement — reconciliation statement id-driven (TASK-PC-FE-075).
  // Seeded from the server when a ?statementId= query param is supplied.
  const [selectedStatementId, setSelectedStatementId] = useState<string | null>(
    initialStatementId,
  );
  const statementQ = useStatement(
    selectedStatementId,
    selectedStatementId === initialStatementId
      ? (initialStatement ?? undefined)
      : undefined,
  );
  const statement: Statement | null =
    statementQ.data ??
    (selectedStatementId === initialStatementId ? initialStatement : null);
  const statementApiErr =
    statementQ.error instanceof ApiError ? statementQ.error : null;
  const statementNotFound =
    statementApiErr?.status === 404 ||
    (initialStatementNotFound && selectedStatementId === initialStatementId);

  // FX position lots — (account, currency)-driven (TASK-PC-FE-091). Purely
  // client-driven: the lookup form submit gates the query (no server seed).
  const [lotsAccountCode, setLotsAccountCode] = useState<string | null>(null);
  const [lotsCurrency, setLotsCurrency] = useState<string | null>(null);
  const lotsQ = usePositionLots(lotsAccountCode, lotsCurrency, true);
  const lotsApiErr = lotsQ.error instanceof ApiError ? lotsQ.error : null;
  const lotsForbidden = lotsApiErr?.status === 403;
  // A 400 (unsupported currency) is the only producer 4xx for this read; an
  // empty position is a 200 (lots: []), NOT a 404. Surface 400 inline.
  const lotsBadRequest = lotsApiErr?.status === 400;

  function handleSubmitLots(code: string, currency: string) {
    setLotsAccountCode(code);
    setLotsCurrency(currency);
  }

  // FX 환율 피드 — global read, no input (TASK-PC-FE-092). Gated on the active
  // tab so a hidden panel never fetches on mount. `rate` stays a string (F5).
  const fxRatesQ = useFxRates(active === 'fx-rates');
  const fxRatesApiErr =
    fxRatesQ.error instanceof ApiError ? fxRatesQ.error : null;
  const fxRatesForbidden = fxRatesApiErr?.status === 403;

  // FX 환율 history 드릴 — per-pair read (TASK-PC-FE-104). Foreign-currency-
  // driven: set either by clicking a feed-table pair OR by the manual lookup
  // form. Gated on the active tab + a non-empty foreign code so a hidden panel
  // (or an unselected pair) never fetches. `rate` stays a string (F5).
  const [fxHistoryForeign, setFxHistoryForeign] = useState<string | null>(null);
  const fxHistoryQ = useFxRateHistory(
    fxHistoryForeign,
    FX_HISTORY_DEFAULT_LIMIT,
    active === 'fx-rates',
  );
  const fxHistoryApiErr =
    fxHistoryQ.error instanceof ApiError ? fxHistoryQ.error : null;
  const fxHistoryForbidden = fxHistoryApiErr?.status === 403;

  /** Called when the operator clicks a trial-balance account code. */
  function handleSelectAccount(code: string) {
    setSelectedAccountCode(code);
    setActive('account');
  }

  /** Called when the operator clicks an entry's entryId in the account
   *  entries table OR a statement match-row journalEntryId — drills into
   *  the Journal Entry tab. */
  function handleSelectEntry(eid: string) {
    setEntryId(eid);
    setActive('entry');
  }

  return {
    active,
    setActive,
    selectedPeriodId,
    setSelectedPeriodId,
    selectedDiscrepancyId,
    setSelectedDiscrepancyId,
    entryId,
    setEntryId,
    entry,
    entryNotFound,
    entryForbidden,
    selectedAccountCode,
    setSelectedAccountCode,
    accountBalance,
    accountEntries,
    accountNotFound,
    selectedStatementId,
    setSelectedStatementId,
    statement,
    statementNotFound,
    lotsAccountCode,
    lotsCurrency,
    lotsQ,
    lotsForbidden,
    lotsBadRequest,
    lotsApiErr,
    handleSubmitLots,
    fxRatesQ,
    fxRatesForbidden,
    fxRatesApiErr,
    fxHistoryForeign,
    setFxHistoryForeign,
    fxHistoryQ,
    fxHistoryForbidden,
    fxHistoryApiErr,
    handleSelectAccount,
    handleSelectEntry,
  };
}
