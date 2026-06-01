'use client';

import Link from 'next/link';
import type { ReactNode } from 'react';
import { Button } from '@/shared/ui/Button';
import { useOperatorOverview } from '../hooks/use-overview';
import {
  OVERVIEW_QUICK_LINKS,
  type OperatorOverview,
  type CardStatus,
} from '../api/types';

/**
 * GAP composed operator overview (TASK-PC-FE-005 — ADR-MONO-013 Phase 2
 * slice 4 / ADR-MONO-015 D1-B). READ-ONLY: there is NO mutation here — no
 * reason capture, no Idempotency-Key, NO destructive/confirm dialog (those
 * are FE-002/004 concerns; carrying them over would be a defect, mirroring
 * the FE-003 read discipline).
 *
 * The server-rendered initial overview is passed in; the client re-query
 * (one bounded fan-out per load — the audit leg is producer meta-audited,
 * so there is NO auto-refetch loop) backs an explicit user retry.
 *
 * Per-source isolation (§ 2.4.4 / ADR-015 D3): each card renders its data
 * OR its OWN degraded / "not available to your role" placeholder. One
 * source down (a `degraded` / `forbidden` card) never blanks the overview
 * or the console shell — the other cards still render. A `401` is never a
 * card status — it is a whole-overview re-login handled server-side.
 *
 * Quick-links into the EXISTING in-console routes (FE-002/003/004);
 * `gap.baseRoute` (`/accounts`) is unchanged (these are nav destinations,
 * not catalog routes).
 */

export interface OperatorOverviewScreenProps {
  initial: OperatorOverview;
}

function statusBadge(status: CardStatus): ReactNode {
  if (status === 'ok') return null;
  const label =
    status === 'forbidden' ? '권한 없음' : '일시적으로 불러올 수 없음';
  return (
    <span
      className="ml-2 rounded-full border border-border bg-muted px-2 py-0.5 text-xs font-normal text-muted-foreground"
      data-testid={`card-status-${status}`}
    >
      {label}
    </span>
  );
}

interface CardProps {
  id: string;
  testid: string;
  title: string;
  status: CardStatus;
  quickLinkHref: string;
  quickLinkLabel: string;
  /** Rendered only when `status === 'ok'`. */
  children: ReactNode;
  /** The "not available to your role" copy for a `forbidden` card. */
  forbiddenCopy: string;
}

function OverviewCard({
  id,
  testid,
  title,
  status,
  quickLinkHref,
  quickLinkLabel,
  children,
  forbiddenCopy,
}: CardProps) {
  return (
    <section
      aria-labelledby={`${id}-heading`}
      data-testid={testid}
      data-status={status}
      className="flex flex-col rounded-lg border border-border bg-background p-5"
    >
      <h2
        id={`${id}-heading`}
        className="mb-3 flex items-center text-lg font-semibold text-foreground"
      >
        {title}
        {statusBadge(status)}
      </h2>

      <div className="flex-1">
        {status === 'ok' && children}

        {status === 'forbidden' && (
          <p
            role="status"
            data-testid={`${testid}-forbidden`}
            className="text-sm text-muted-foreground"
          >
            {forbiddenCopy}
          </p>
        )}

        {status === 'degraded' && (
          <p
            role="status"
            data-testid={`${testid}-degraded`}
            className="text-sm text-muted-foreground"
          >
            이 항목을 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은
            계속 사용할 수 있습니다. 아래에서 다시 시도하거나 잠시 후
            새로고침하세요.
          </p>
        )}
      </div>

      <div className="mt-4 border-t border-border pt-3">
        <Link
          href={quickLinkHref}
          data-testid={`${testid}-quicklink`}
          className="text-sm font-medium underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          {quickLinkLabel}
        </Link>
      </div>
    </section>
  );
}

function Metric({
  label,
  value,
  testid,
}: {
  label: string;
  value: number | null;
  testid: string;
}) {
  return (
    <div>
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd
        className="text-2xl font-semibold tabular-nums text-foreground"
        data-testid={testid}
      >
        {value === null ? '—' : value.toLocaleString()}
      </dd>
    </div>
  );
}

export function OperatorOverviewScreen({
  initial,
}: OperatorOverviewScreenProps) {
  const overview = useOperatorOverview(initial);
  const data = overview.data ?? initial;

  const { accounts, audit, operators } = data;
  const allDegraded =
    accounts.status === 'degraded' &&
    audit.status === 'degraded' &&
    operators.status === 'degraded';

  return (
    <section aria-labelledby="overview-heading">
      <Link
        href="/dashboards/overview"
        data-testid="gap-detail-back-link"
        className="mb-4 inline-block text-sm underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
      >
        ← 통합 개요로 돌아가기
      </Link>
      <div className="mb-6 flex items-start justify-between gap-4">
        <div>
          <h1
            id="overview-heading"
            className="mb-2 text-2xl font-semibold"
          >
            GAP 상세 (계정 · 감사 · 운영자)
          </h1>
          <p className="text-sm text-muted-foreground">
            통합 개요의 GAP 카드에서 진입한 GAP 플랫폼 운영 상세입니다. 계정 ·
            감사/보안 · 운영자 현황 합성 개요 (읽기 전용). 각 항목은 독립적으로
            동작하며, 한 항목 장애가 다른 항목이나 콘솔 전체에 영향을 주지
            않습니다.
          </p>
        </div>
        <Button
          type="button"
          variant="secondary"
          onClick={() => overview.refetch()}
          disabled={overview.isFetching}
          data-testid="overview-refresh"
        >
          {overview.isFetching ? '새로고침 중…' : '새로고침'}
        </Button>
      </div>

      {allDegraded && (
        <div
          role="status"
          data-testid="overview-all-degraded"
          className="mb-6 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          모든 개요 항목을 일시적으로 불러올 수 없습니다. 콘솔 자체는 정상
          동작합니다. 위의 새로고침으로 다시 시도하거나 각 화면으로 직접
          이동하세요.
        </div>
      )}

      <div
        className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3"
        data-testid="overview-cards"
      >
        <OverviewCard
          id="ov-accounts"
          testid="overview-card-accounts"
          title="계정"
          status={accounts.status}
          quickLinkHref={OVERVIEW_QUICK_LINKS.accounts}
          quickLinkLabel="계정 관리로 이동 →"
          forbiddenCopy="이 항목은 현재 권한으로 조회할 수 없습니다."
        >
          <dl className="grid grid-cols-2 gap-4">
            <Metric
              label="전체 계정"
              value={accounts.totalElements}
              testid="overview-accounts-total"
            />
            <Metric
              label="최근 스냅샷"
              value={accounts.sampleCount}
              testid="overview-accounts-sample"
            />
          </dl>
        </OverviewCard>

        <OverviewCard
          id="ov-audit"
          testid="overview-card-audit"
          title="감사 · 보안"
          status={audit.status}
          quickLinkHref={OVERVIEW_QUICK_LINKS.audit}
          quickLinkLabel="감사 · 보안 조회로 이동 →"
          forbiddenCopy="감사·보안 조회 권한이 없습니다. (로그인 이력·의심 활동은 보안 이벤트 조회 권한이 추가로 필요합니다.)"
        >
          <dl className="grid grid-cols-2 gap-4">
            <Metric
              label="최근 기록 수"
              value={audit.totalElements}
              testid="overview-audit-total"
            />
            <Metric
              label="이번 스냅샷"
              value={audit.recentCount}
              testid="overview-audit-recent"
            />
          </dl>
          <p
            className="mt-3 text-xs text-muted-foreground"
            data-testid="overview-audit-latest"
          >
            {audit.latestOccurredAt
              ? `최근 활동: ${audit.latestOccurredAt} (UTC)`
              : '표시할 최근 활동이 없습니다.'}
          </p>
        </OverviewCard>

        <OverviewCard
          id="ov-operators"
          testid="overview-card-operators"
          title="운영자"
          status={operators.status}
          quickLinkHref={OVERVIEW_QUICK_LINKS.operators}
          quickLinkLabel="운영자 관리로 이동 →"
          forbiddenCopy="운영자 현황은 현재 권한으로 조회할 수 없습니다 (SUPER_ADMIN / operator.manage 권한 필요)."
        >
          <dl className="grid grid-cols-3 gap-4">
            <Metric
              label="전체"
              value={operators.totalElements}
              testid="overview-operators-total"
            />
            <Metric
              label="활성"
              value={operators.activeCount}
              testid="overview-operators-active"
            />
            <Metric
              label="정지"
              value={operators.suspendedCount}
              testid="overview-operators-suspended"
            />
          </dl>
        </OverviewCard>
      </div>
    </section>
  );
}
