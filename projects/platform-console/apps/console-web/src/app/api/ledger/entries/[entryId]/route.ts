import { NextResponse } from 'next/server';
import { getJournalEntry } from '@/features/ledger-ops/api/ledger-api';
import { mapLedgerError, newRequestId } from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin finance ledger journal-entry-by-id read proxy (read-only —
 * GET). The HttpOnly domain-facing IAM OIDC access token is attached
 * server-side in `getJournalEntry()` (§ 2.4.7.1 reusing the § 2.4.7 /
 * § 2.4.5 rule — NOT the operator token). Id-driven (the ledger has NO
 * entry list/search GET). READ-ONLY: GET only, no mutation branch. The
 * full `JournalEntry` (lines: F5 money + exchangeRate string + base
 * amount) is forwarded untouched (NO `Number()` coercion). 404
 * `JOURNAL_ENTRY_NOT_FOUND` passes through.
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ entryId: string }> },
) {
  const requestId = newRequestId();
  const { entryId } = await params;
  try {
    const result = await getJournalEntry(entryId);
    return NextResponse.json(result);
  } catch (err) {
    return mapLedgerError(err, requestId);
  }
}
