'use client';

import { formatMoney, positionLotMoney } from '../api/types';
import type { PositionLotsResponse } from '../api/types';
import { formatDateTime } from '@/shared/lib/datetime';

/**
 * FX position open-lots table + summary (TASK-PC-FE-091 — § 2.4.7.1).
 *
 * Displays the open acquisition lots for one `(account, currency)` position
 * (§ 12 `GET /settlements/{code}/{currency}/lots`) — acquired-at, original /
 * remaining foreign, original / carrying base (KRW), source journal entry —
 * plus a summary card (Σremaining foreign · Σcarrying base · lot count).
 *
 * F5 money rendering (CONTRACT obligation, NOT a UX nicety):
 *   - every `*Minor` field is a minor-units **string** (the producer wire
 *     shape, § 12); rendering goes through `formatMoney(...)` ONLY (pure
 *     string manipulation + per-currency scale lookup);
 *   - NO `Number()` / `parseFloat()` / `parseInt()` is applied to any
 *     `amount` anywhere (a test grep-asserts this against the on-disk source);
 *   - `seq` / `lotCount` ARE plain numbers (lot index / count, not money).
 *
 * Empty position (`lots: []` / `lotCount: 0`) → an empty-state message (NOT
 * an error — the producer returns 200 for an empty position).
 *
 * `onSelectEntry` wires each lot's `sourceJournalEntryId` back to the Journal
 * Entry tab so the operator can drill from a lot into its acquisition entry.
 *
 * STRICTLY READ-ONLY — no mutation affordance.
 */
export interface PositionLotsTableProps {
  lots: PositionLotsResponse | null;
  /** Called when the operator clicks a lot's `sourceJournalEntryId` — re-uses
   *  the Journal Entry tab drill. */
  onSelectEntry?: (entryId: string) => void;
}

export function PositionLotsTable({ lots, onSelectEntry }: PositionLotsTableProps) {
  if (!lots) {
    return (
      <p
        className="text-sm text-muted-foreground"
        data-testid="ledger-lots-none"
      >
        외화 포지션 로트를 불러올 수 없습니다.
      </p>
    );
  }

  return (
    <section
      aria-labelledby="ledger-lots-detail-heading"
      data-testid="ledger-lots-detail"
      className="mb-8"
    >
      <h2
        id="ledger-lots-detail-heading"
        className="mb-4 text-lg font-medium text-foreground"
      >
        외화 포지션 로트 (FX position lots)
      </h2>

      {/* Summary card */}
      <div
        data-testid="ledger-lots-summary"
        className="mb-6 rounded-md border border-border bg-muted p-4"
        aria-label="포지션 요약 (position summary)"
      >
        <dl className="grid grid-cols-1 gap-x-6 gap-y-2 text-sm sm:grid-cols-3">
          <div>
            <dt className="text-muted-foreground">잔량 합계 (Σ remaining)</dt>
            <dd
              className="font-medium text-foreground"
              data-testid="ledger-lots-total-remaining"
            >
              {formatMoney({
                amount: lots.totalRemainingForeignMinor,
                currency: lots.lots[0]?.currency ?? 'USD',
              })}
            </dd>
          </div>
          <div>
            <dt className="text-muted-foreground">
              장부가 합계 (Σ carrying, KRW)
            </dt>
            <dd
              className="font-medium text-foreground"
              data-testid="ledger-lots-total-carrying"
            >
              {formatMoney({
                amount: lots.totalCarryingBaseMinor,
                currency: 'KRW',
              })}
            </dd>
          </div>
          <div>
            <dt className="text-muted-foreground">로트 수 (lot count)</dt>
            <dd
              className="font-medium text-foreground"
              data-testid="ledger-lots-count"
            >
              {lots.lotCount}
            </dd>
          </div>
        </dl>
      </div>

      {/* Lots table */}
      {lots.lots.length === 0 ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="ledger-lots-empty"
        >
          이 포지션에는 열린 외화 로트가 없습니다.
        </p>
      ) : (
        <table
          className="w-full data-table text-sm"
          data-testid="ledger-lots-table"
          aria-label="외화 포지션 로트 내역"
        >
          <caption className="sr-only">외화 포지션 로트 내역</caption>
          <thead>
            <tr className="border-b border-border text-left">
              <th scope="col" className="p-2">
                취득 일시 (acquiredAt)
              </th>
              <th scope="col" className="p-2">
                순번 (seq)
              </th>
              <th scope="col" className="p-2">
                취득 외화 (original)
              </th>
              <th scope="col" className="p-2">
                잔량 외화 (remaining)
              </th>
              <th scope="col" className="p-2">
                취득 기준가 (original base)
              </th>
              <th scope="col" className="p-2">
                장부가 (carrying base)
              </th>
              <th scope="col" className="p-2">
                취득 분개 (sourceJournalEntryId)
              </th>
            </tr>
          </thead>
          <tbody>
            {lots.lots.map((lot, i) => {
              const m = positionLotMoney(lot);
              return (
                <tr
                  key={`${lot.lotId}-${i}`}
                  data-testid={`ledger-lots-row-${i}`}
                  className="border-b border-border"
                >
                  <td className="p-2 text-muted-foreground">{formatDateTime(lot.acquiredAt)}</td>
                  <td className="p-2" data-testid={`ledger-lots-seq-${i}`}>
                    {lot.seq}
                  </td>
                  <td className="p-2" data-testid={`ledger-lots-original-${i}`}>
                    {formatMoney(m.originalForeign)}
                  </td>
                  <td className="p-2" data-testid={`ledger-lots-remaining-${i}`}>
                    {formatMoney(m.remainingForeign)}
                  </td>
                  <td
                    className="p-2"
                    data-testid={`ledger-lots-original-base-${i}`}
                  >
                    {formatMoney(m.originalBase)}
                  </td>
                  <td className="p-2" data-testid={`ledger-lots-carrying-${i}`}>
                    {formatMoney(m.carryingBase)}
                  </td>
                  <td className="p-2">
                    {onSelectEntry ? (
                      <button
                        type="button"
                        onClick={() => onSelectEntry(lot.sourceJournalEntryId)}
                        className="underline text-foreground hover:text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                        data-testid={`ledger-lots-entry-${i}`}
                      >
                        {lot.sourceJournalEntryId}
                      </button>
                    ) : (
                      <span data-testid={`ledger-lots-entry-${i}`}>
                        {lot.sourceJournalEntryId}
                      </span>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </section>
  );
}
