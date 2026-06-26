'use client';

import { messageForCode } from '@/shared/api/errors';
import { JournalEntryLookup } from './JournalEntryLookup';
import { JournalEntryDetail } from './JournalEntryDetail';
import type { useLedgerOpsState } from './use-ledger-ops-state';

type S = ReturnType<typeof useLedgerOpsState>;

/**
 * Journal Entry tab panel content (TASK-PC-FE-134 code split). Extracted
 * verbatim from `LedgerOpsScreen`; entry-id-driven lookup + detail load only
 * when the tab is first activated (or seeded). Pure view — state owned by the
 * parent hook.
 */
export function JournalEntryPanel({
  entryId,
  setEntryId,
  entryForbidden,
  entryNotFound,
  entry,
}: Pick<
  S,
  'entryId' | 'setEntryId' | 'entryForbidden' | 'entryNotFound' | 'entry'
>) {
  return (
    <>
      <JournalEntryLookup
        initialEntryId={entryId ?? undefined}
        onSubmit={setEntryId}
      />
      {!entryId ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="ledger-entry-none"
        >
          조회할 entryId 를 입력하세요.
        </p>
      ) : entryForbidden ? (
        <div
          role="status"
          data-testid="ledger-entry-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('TENANT_FORBIDDEN')}
        </div>
      ) : entryNotFound ? (
        <div
          role="status"
          data-testid="ledger-entry-not-found"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('JOURNAL_ENTRY_NOT_FOUND')}
        </div>
      ) : entry ? (
        <JournalEntryDetail entry={entry} />
      ) : (
        <p
          className="text-sm text-muted-foreground"
          data-testid="ledger-entry-loading"
        >
          불러오는 중…
        </p>
      )}
    </>
  );
}
