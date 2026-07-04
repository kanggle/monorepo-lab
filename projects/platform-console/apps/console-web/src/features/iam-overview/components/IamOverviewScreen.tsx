import Link from 'next/link';
import { cn } from '@/shared/lib/cn';
import { formatDateTime } from '@/shared/lib/datetime';
import type { AuditRow } from '@/features/audit/api/types';
import type {
  IamOverviewState,
  CellStatus,
  OperatorsSummary,
} from '../api/overview-state';

/**
 * IAM operator **overview snapshot** presentation (TASK-PC-FE-180). Server
 * component — STRICTLY READ-ONLY, no `'use client'`. Renders the
 * `getIamOverviewState` fan-out: three count/summary cards (each a quick-launch
 * `Link` to its IAM screen), the operator ACTIVE/SUSPENDED split, and the recent
 * audit·security mini-list. A non-`ok` cell renders a compact "점검 필요" /
 * "권한 없음" placeholder instead of a number (never blanks); a `noActiveTenant`
 * state renders a single page-level tenant gate (mirror of the sibling IAM
 * screens). The static role/permission guide now lives at `/iam/guide`.
 */

function cellPlaceholder(status: CellStatus): string {
  return status === 'forbidden' ? '권한 없음' : '점검 필요';
}

/** Per-card status dot — same vocabulary as the ecommerce snapshot / console
 *  home 도메인 상태 요약: ok = 정상, degraded = 점검 필요, forbidden = 권한 없음. */
const STATUS_DOT: Record<CellStatus, string> = {
  ok: 'bg-green-500',
  degraded: 'bg-red-500',
  forbidden: 'bg-muted-foreground/40',
};
const STATUS_LABEL: Record<CellStatus, string> = {
  ok: '정상',
  degraded: '점검 필요',
  forbidden: '권한 없음',
};

function CardShell({
  href,
  testid,
  label,
  status,
  children,
}: {
  href: string;
  testid: string;
  label: string;
  status: CellStatus;
  children: React.ReactNode;
}) {
  return (
    <Link
      href={href}
      data-testid={testid}
      className="flex min-w-[12rem] flex-1 flex-col gap-3 rounded-md border border-border bg-background px-4 py-4 transition-colors hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
    >
      <span
        className="flex items-center gap-1.5 text-sm text-muted-foreground"
        data-testid={`${testid}-status`}
        data-status={status}
      >
        <span
          className={cn('h-1.5 w-1.5 shrink-0 rounded-full', STATUS_DOT[status])}
          aria-hidden="true"
        />
        {label}
        <span className="sr-only">상태: {STATUS_LABEL[status]}</span>
      </span>
      {children}
    </Link>
  );
}

function BigCount({ value, testid }: { value: number; testid: string }) {
  return (
    <span
      className="text-2xl font-semibold tabular-nums text-foreground"
      data-testid={testid}
    >
      {value.toLocaleString()}
    </span>
  );
}

function Placeholder({ status, testid }: { status: CellStatus; testid: string }) {
  return (
    <span
      className="text-sm font-medium text-muted-foreground"
      data-testid={testid}
    >
      {cellPlaceholder(status)}
    </span>
  );
}

function OperatorsCard({ summary }: { summary: OperatorsSummary }) {
  const ok = summary.status === 'ok' && summary.total !== null;
  return (
    <CardShell
      href="/operators"
      testid="iam-overview-operators"
      label="운영자"
      status={summary.status}
    >
      {ok ? (
        <>
          <BigCount value={summary.total!} testid="iam-overview-operators-total" />
          <dl className="flex gap-4 text-xs text-muted-foreground">
            <div className="flex items-center gap-1">
              <dt>활성</dt>
              <dd
                className="font-medium tabular-nums text-foreground"
                data-testid="iam-overview-operators-active"
              >
                {summary.active !== null ? summary.active.toLocaleString() : '—'}
              </dd>
            </div>
            <div className="flex items-center gap-1">
              <dt>정지</dt>
              <dd
                className="font-medium tabular-nums text-foreground"
                data-testid="iam-overview-operators-suspended"
              >
                {summary.suspended !== null
                  ? summary.suspended.toLocaleString()
                  : '—'}
              </dd>
            </div>
          </dl>
        </>
      ) : (
        <Placeholder
          status={summary.status}
          testid="iam-overview-operators-degraded"
        />
      )}
    </CardShell>
  );
}

function AccountsCard({ status, total }: { status: CellStatus; total: number | null }) {
  const ok = status === 'ok' && total !== null;
  return (
    <CardShell
      href="/accounts"
      testid="iam-overview-accounts"
      label="계정"
      status={status}
    >
      {ok ? (
        <>
          <BigCount value={total!} testid="iam-overview-accounts-total" />
          <span className="text-xs text-muted-foreground">활성 테넌트 계정</span>
        </>
      ) : (
        <Placeholder status={status} testid="iam-overview-accounts-degraded" />
      )}
    </CardShell>
  );
}

const AUDIT_SOURCE_LABEL: Record<string, string> = {
  admin: '관리 작업',
  login_history: '로그인',
  suspicious: '의심 활동',
};

/**
 * Row display shape — tolerant of the AuditRow discriminated union (+ the
 * generic `.passthrough()` fallback whose broad `source: string` defeats clean
 * discriminated narrowing), so a producer evolution never crashes the mini-list.
 * Read fields off one permissive record view instead of per-variant narrowing.
 */
function auditRowView(
  row: AuditRow,
  index: number,
): { key: string; source: string; primary: string; occurredAt?: string } {
  const r = row as {
    source: string;
    auditId?: string;
    eventId?: string;
    actionCode?: string;
    outcome?: string | null;
    occurredAt?: string;
  };
  const source = AUDIT_SOURCE_LABEL[r.source] ?? r.source;
  const key = r.auditId ?? r.eventId ?? `row-${index}`;
  let primary: string;
  if (r.source === 'admin') {
    primary = r.actionCode ?? source;
  } else if (r.source === 'login_history' || r.source === 'suspicious') {
    primary = r.outcome ?? source;
  } else {
    primary = source;
  }
  return { key, source, primary, occurredAt: r.occurredAt };
}

function AuditCard({
  status,
  total,
  recent,
}: {
  status: CellStatus;
  total: number | null;
  recent: AuditRow[] | null;
}) {
  const ok = status === 'ok';
  return (
    <div
      data-testid="iam-overview-audit"
      data-status={status}
      className="flex flex-col gap-3 rounded-md border border-border bg-background px-4 py-4"
    >
      <Link
        href="/audit"
        data-testid="iam-overview-audit-link"
        className="flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
      >
        <span
          className={cn('h-1.5 w-1.5 shrink-0 rounded-full', STATUS_DOT[status])}
          aria-hidden="true"
        />
        감사 · 보안
        <span className="sr-only">상태: {STATUS_LABEL[status]}</span>
      </Link>
      {!ok ? (
        <Placeholder status={status} testid="iam-overview-audit-degraded" />
      ) : (
        <>
          <span className="text-xs text-muted-foreground">
            스코프 내 총 이벤트{' '}
            <span
              className="font-medium tabular-nums text-foreground"
              data-testid="iam-overview-audit-total"
            >
              {(total ?? 0).toLocaleString()}
            </span>
          </span>
          {!recent || recent.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              최근 이벤트가 없습니다.
            </p>
          ) : (
            <ul className="space-y-2 text-sm" data-testid="iam-overview-audit-recent">
              {recent.map((row, i) => {
                const v = auditRowView(row, i);
                return (
                  <li
                    key={v.key}
                    className="flex items-center justify-between gap-3 border-b border-border pb-2 last:border-0 last:pb-0"
                  >
                    <span className="shrink-0 rounded bg-muted px-1.5 py-0.5 text-[11px] text-muted-foreground">
                      {v.source}
                    </span>
                    <span className="min-w-0 flex-1 truncate text-foreground">
                      {v.primary}
                    </span>
                    <span className="hidden shrink-0 text-xs text-muted-foreground sm:inline">
                      {formatDateTime(v.occurredAt)}
                    </span>
                  </li>
                );
              })}
            </ul>
          )}
        </>
      )}
    </div>
  );
}

export function IamOverviewScreen({ state }: { state: IamOverviewState }) {
  if (state.noActiveTenant) {
    return (
      <div
        role="status"
        data-testid="iam-overview-no-tenant"
        className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
      >
        <p className="mb-1 font-medium text-foreground">테넌트를 먼저 선택해주세요.</p>
        <p>
          IAM 개요는 선택한 테넌트 범위의 운영자·계정·감사 현황을 보여줍니다. 상단
          테넌트 스위처에서 테넌트를 선택하면 현황이 표시됩니다.
        </p>
      </div>
    );
  }

  return (
    <section data-testid="iam-overview" aria-label="IAM 운영 개요">
      <div className="mb-8">
        <div className="mb-3 flex flex-wrap gap-3">
          <OperatorsCard summary={state.operators} />
          <AccountsCard
            status={state.accounts.status}
            total={state.accounts.total}
          />
        </div>
        <AuditCard
          status={state.audit.status}
          total={state.audit.total}
          recent={state.audit.recent}
        />
      </div>
    </section>
  );
}
