import Link from 'next/link';
import { cn } from '@/shared/lib/cn';
import type {
  ErpOverviewState,
  ErpAreaCount,
  CellStatus,
} from '../api/overview-state';

/**
 * erp domain **overview** landing (`/erp` — TASK-PC-FE-232). Server
 * component (no client interactivity needed — a pure read-only snapshot
 * render, mirrors `FinanceOverviewScreen` / `WmsOverview` /
 * `ScmOverview` presentational patterns). PROMOTES the former
 * masters-embedded TASK-PC-FE-161 `ErpMastersOverview` tile-rendering
 * logic into the standalone landing (count tiles unchanged — placeholder
 * / status-dot rendering reused verbatim), and ADDS the 결재 대기 /
 * 활성 위임 tiles.
 *
 * Renders 7 INDEPENDENTLY-degrading count tiles (§ overview-state.ts):
 * 부서 · 직원 · 직급 · 원가센터 · 거래처 · 결재 대기 · 활성 위임. A single
 * leg's 403/503 degrades ONLY its own tile — the sibling tiles and the
 * rest of the overview stay rendered (AC-2).
 *
 * Legacy `/erp` bookmark honesty (Edge Cases — mirrors PC-FE-229's
 * `/finance?accountId=` treatment): the masters surface that used to live
 * at this route moved to `/erp/masters` — the shortcuts nav below always
 * surfaces a `마스터` link so the relocation is never a dead end.
 */
export interface ErpOverviewScreenProps {
  state: ErpOverviewState;
}

function cellPlaceholder(status: CellStatus): string {
  return status === 'forbidden' ? '권한 없음' : '점검 필요';
}

const SERVICE_STATUS_DOT: Record<CellStatus, string> = {
  ok: 'bg-green-500',
  degraded: 'bg-red-500',
  forbidden: 'bg-muted-foreground/40',
};
const SERVICE_STATUS_LABEL: Record<CellStatus, string> = {
  ok: '정상',
  degraded: '점검 필요',
  forbidden: '권한 없음',
};

function CountTile({ area }: { area: ErpAreaCount }) {
  const ok = area.status === 'ok' && area.count !== null;
  return (
    <div className="flex min-w-[9rem] flex-1 flex-col gap-2 rounded-md border border-border bg-background px-4 py-3">
      <span
        className="flex items-center gap-1.5 text-sm text-muted-foreground"
        data-testid={`erp-overview-${area.key}-service-status`}
        data-status={area.status}
      >
        <span
          className={cn(
            'h-1.5 w-1.5 shrink-0 rounded-full',
            SERVICE_STATUS_DOT[area.status],
          )}
          aria-hidden="true"
        />
        {area.label}
        <span className="sr-only">
          서비스 상태: {SERVICE_STATUS_LABEL[area.status]}
        </span>
      </span>
      {ok ? (
        <span
          className="text-2xl font-semibold tabular-nums text-foreground"
          data-testid={`erp-overview-${area.key}-count`}
        >
          {area.count!.toLocaleString()}
        </span>
      ) : (
        <span
          className="text-sm font-medium text-muted-foreground"
          data-testid={`erp-overview-${area.key}-count-degraded`}
        >
          {cellPlaceholder(area.status)}
        </span>
      )}
    </div>
  );
}

export function ErpOverviewScreen({ state }: ErpOverviewScreenProps) {
  return (
    <section aria-labelledby="erp-overview-heading" data-testid="erp-overview">
      <h1 id="erp-overview-heading" className="mb-2 text-2xl font-semibold">
        ERP 개요
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        마스터 5종(부서·직원·직급·원가센터·거래처) 건수와 본인 결재 대기·활성
        위임 건수를 한 번에 확인합니다. 마스터 상세 조회·등록·수정은 아래
        마스터 화면으로 이동하세요.
      </p>

      <nav
        aria-label="ERP 화면 바로가기"
        className="mb-8 flex flex-wrap gap-4 text-sm"
      >
        <Link
          href="/erp/guide"
          data-testid="erp-overview-link-guide"
          className="underline underline-offset-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          가이드
        </Link>
        <Link
          href="/erp/masters"
          data-testid="erp-overview-link-masters"
          className="underline underline-offset-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          마스터
        </Link>
        <Link
          href="/erp/orgview"
          data-testid="erp-overview-link-orgview"
          className="underline underline-offset-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          통합 조회
        </Link>
        <Link
          href="/erp/approval"
          data-testid="erp-overview-link-approval"
          className="underline underline-offset-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          결재함
        </Link>
        <Link
          href="/erp/delegation"
          data-testid="erp-overview-link-delegation"
          className="underline underline-offset-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          위임
        </Link>
      </nav>

      <div
        data-testid="erp-overview-counts"
        className="flex flex-wrap gap-3"
      >
        {state.counts.map((area) => (
          <CountTile key={area.key} area={area} />
        ))}
      </div>
    </section>
  );
}
