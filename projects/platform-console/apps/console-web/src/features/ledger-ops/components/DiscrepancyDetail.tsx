'use client';

import {
  formatMoney,
  discrepancyMoney,
  KNOWN_DISCREPANCY_TYPES,
  KNOWN_DISCREPANCY_STATUSES,
  type Discrepancy,
} from '../api/types';
import { useDiscrepancy } from '../hooks/use-ledger-ops';

/**
 * Reconciliation discrepancy detail (TASK-PC-FE-072 — § 2.4.7.1 /
 * `reconciliation-api.md` § 5).
 *
 * Renders the full discrepancy, including the `resolution` sub-object when
 * `status === 'RESOLVED'` (resolutionType / note / resolvedBy / resolvedAt
 * — surfaced honestly). The expected / actual amounts render via
 * `discrepancyMoney` + `formatMoney` (F5 — no `Number()` coercion). For the
 * 11th-increment FX-difference `AMOUNT_MISMATCH`, BOTH `externalRef` and
 * `journalEntryId` (the matched pair) are surfaced.
 *
 * STRICTLY READ-ONLY — no mutation affordance (no resolve button).
 */
export interface DiscrepancyDetailProps {
  discrepancyId: string;
}

function typeLabel(type: string): string {
  return (KNOWN_DISCREPANCY_TYPES as readonly string[]).includes(type)
    ? type
    : `${type} (unknown)`;
}
function statusLabel(status: string): string {
  return (KNOWN_DISCREPANCY_STATUSES as readonly string[]).includes(status)
    ? status
    : `${status} (unknown)`;
}

export function DiscrepancyDetail({ discrepancyId }: DiscrepancyDetailProps) {
  const q = useDiscrepancy(discrepancyId);
  const d: Discrepancy | null = q.data ?? null;

  if (!d) {
    return (
      <section
        aria-labelledby="ledger-recon-detail-heading"
        className="mb-6 rounded-md border border-border bg-background p-4"
        data-testid="ledger-recon-detail"
      >
        <h3
          id="ledger-recon-detail-heading"
          className="mb-2 text-base font-medium text-foreground"
        >
          대사 차이 상세
        </h3>
        <p
          className="text-sm text-muted-foreground"
          data-testid="ledger-recon-detail-loading"
        >
          불러오는 중…
        </p>
      </section>
    );
  }

  const m = discrepancyMoney(d);
  const resolution = d.resolution ?? null;

  return (
    <section
      aria-labelledby="ledger-recon-detail-heading"
      className="mb-6 rounded-md border border-border bg-background p-4"
      data-testid="ledger-recon-detail"
    >
      <h3
        id="ledger-recon-detail-heading"
        className="mb-3 text-base font-medium text-foreground"
      >
        대사 차이 상세 — {d.discrepancyId}
      </h3>
      <dl className="mb-4 grid grid-cols-2 gap-3 text-sm">
        <div>
          <dt className="text-muted-foreground">유형</dt>
          <dd className="text-foreground" data-testid="ledger-recon-detail-type">
            {typeLabel(d.type)}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">상태</dt>
          <dd
            className="text-foreground"
            data-testid="ledger-recon-detail-status"
          >
            {statusLabel(d.status)}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">외부 참조 (externalRef)</dt>
          <dd
            className="text-foreground"
            data-testid="ledger-recon-detail-extref"
          >
            {d.externalRef ?? '—'}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">분개 ID (journalEntryId)</dt>
          <dd
            className="text-foreground"
            data-testid="ledger-recon-detail-journal"
          >
            {d.journalEntryId ?? '—'}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">기대값 (expected)</dt>
          <dd
            className="text-foreground"
            data-testid="ledger-recon-detail-expected"
          >
            {formatMoney(m.expected)}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">실제값 (actual)</dt>
          <dd
            className="text-foreground"
            data-testid="ledger-recon-detail-actual"
          >
            {formatMoney(m.actual)}
          </dd>
        </div>
      </dl>

      {resolution ? (
        <div
          className="rounded-md border border-border bg-muted p-3"
          data-testid="ledger-recon-resolution"
        >
          <h4 className="mb-2 text-sm font-medium text-foreground">
            해소 내역 (resolution)
          </h4>
          <dl className="grid grid-cols-2 gap-3 text-sm">
            <div>
              <dt className="text-muted-foreground">해소 유형</dt>
              <dd
                className="text-foreground"
                data-testid="ledger-recon-resolution-type"
              >
                {resolution.resolutionType}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">해소자</dt>
              <dd className="text-foreground">{resolution.resolvedBy ?? '—'}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">해소 시각 (UTC)</dt>
              <dd className="text-foreground">{resolution.resolvedAt ?? '—'}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">메모</dt>
              <dd className="text-foreground">{resolution.note ?? '—'}</dd>
            </div>
          </dl>
        </div>
      ) : (
        <p
          className="text-sm text-muted-foreground"
          data-testid="ledger-recon-unresolved"
        >
          아직 해소되지 않은 대사 차이입니다 (OPEN).
        </p>
      )}
    </section>
  );
}
