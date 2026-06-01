import type { ReactNode } from 'react';
import Link from 'next/link';
import {
  type Card,
  type DomainKey,
  type OperatorOverview,
  type DegradedReason,
  type ForbiddenReason,
  GapDataSchema,
  WmsDataSchema,
  ScmDataSchema,
  FinanceDataSchema,
  ErpDataSchema,
} from '../api/operator-overview-types';
import { RetryButton } from './RetryButton';

/**
 * Per-domain card (TASK-PC-FE-011). Server component. Renders one of
 * three branches per `card.status`:
 *
 *  - `ok` — domain-specific summary using `data` (count / snapshot /
 *    balance available). The data shape is the producer's verbatim
 *    body; this card narrows defensively via `*DataSchema.safeParse`.
 *    A parse miss falls through to a "summary unavailable" placeholder
 *    (the card stays `ok` semantically — the BE classified it as ok;
 *    only the UI summary is degraded; never a UI crash).
 *  - `degraded` — "data unavailable" placeholder + `<RetryButton>`.
 *    Reason ∈ { DOWNSTREAM_ERROR, TIMEOUT, CIRCUIT_OPEN } surfaced as
 *    user-friendly Korean copy.
 *  - `forbidden` — "not available to your role / tenant" placeholder.
 *    Reason ∈ { PERMISSION_DENIED, TENANT_FORBIDDEN, MISSING_PREREQUISITE };
 *    `MISSING_PREREQUISITE` on the finance card surfaces an actionable
 *    hint per § 2.4.9.1 Implementation guidance ("Configure a default
 *    finance account in operator profile").
 *
 * F5 money discipline (finance card): `balance.amount` is treated as a
 * STRING; the UI never coerces with `Number(...)`/`parseFloat(...)`/
 * `parseInt(...)`. The MVP surfaces only "balance available" framing
 * with the optional currency code — no numeric formatting.
 */

const DOMAIN_TITLE: Record<DomainKey, string> = {
  gap: 'GAP 계정',
  wms: 'WMS 재고',
  scm: 'SCM 가시성',
  finance: 'Finance 잔액',
  erp: 'ERP 마스터',
};

const DEGRADED_COPY: Record<DegradedReason, string> = {
  DOWNSTREAM_ERROR: '하위 서비스에서 오류가 발생했습니다.',
  TIMEOUT: '응답 시간이 초과되었습니다.',
  CIRCUIT_OPEN: '서비스가 일시적으로 응답할 수 없습니다.',
};

const FORBIDDEN_COPY: Record<ForbiddenReason, string> = {
  PERMISSION_DENIED: '이 도메인 조회 권한이 없습니다.',
  TENANT_FORBIDDEN: '선택한 테넌트에 대한 권한이 없습니다.',
  MISSING_PREREQUISITE: '조회에 필요한 사전 설정이 누락되었습니다.',
};

// -------------------------------------------------------------------------
// `ok` branch — per-domain summary renderers (defensive narrowing).
// Each renderer reads the producer-shaped `data` via its narrow
// schema; a parse miss returns the fallback summary placeholder.
// -------------------------------------------------------------------------

function GapSummary({ data }: { data: unknown }): ReactNode {
  const parsed = GapDataSchema.safeParse(data);
  const total =
    parsed.success && typeof parsed.data.totalElements === 'number'
      ? parsed.data.totalElements
      : null;
  return (
    <dl>
      <dt className="text-sm text-muted-foreground">전체 계정</dt>
      <dd
        className="text-2xl font-semibold tabular-nums text-foreground"
        data-testid="operator-overview-card-gap-total"
      >
        {total === null ? '—' : total.toLocaleString()}
      </dd>
    </dl>
  );
}

function WmsSummary({ data }: { data: unknown }): ReactNode {
  const parsed = WmsDataSchema.safeParse(data);
  const snap =
    parsed.success && parsed.data.inventorySnapshot
      ? parsed.data.inventorySnapshot
      : null;
  const stock =
    snap && typeof snap.totalStockUnits === 'number'
      ? snap.totalStockUnits
      : null;
  const alerts =
    snap && typeof snap.alertCount === 'number' ? snap.alertCount : null;
  return (
    <dl className="grid grid-cols-2 gap-4">
      <div>
        <dt className="text-sm text-muted-foreground">총 재고</dt>
        <dd
          className="text-2xl font-semibold tabular-nums text-foreground"
          data-testid="operator-overview-card-wms-stock"
        >
          {stock === null ? '—' : stock.toLocaleString()}
        </dd>
      </div>
      <div>
        <dt className="text-sm text-muted-foreground">알림</dt>
        <dd
          className="text-2xl font-semibold tabular-nums text-foreground"
          data-testid="operator-overview-card-wms-alerts"
        >
          {alerts === null ? '—' : alerts.toLocaleString()}
        </dd>
      </div>
    </dl>
  );
}

function ScmSummary({ data }: { data: unknown }): ReactNode {
  const parsed = ScmDataSchema.safeParse(data);
  const warning = parsed.success ? parsed.data.meta?.warning : undefined;
  const nodes =
    parsed.success && Array.isArray(parsed.data.nodes)
      ? parsed.data.nodes.length
      : null;
  return (
    <div className="space-y-2">
      <dl>
        <dt className="text-sm text-muted-foreground">스냅샷 노드 수</dt>
        <dd
          className="text-2xl font-semibold tabular-nums text-foreground"
          data-testid="operator-overview-card-scm-nodes"
        >
          {nodes === null ? '—' : nodes.toLocaleString()}
        </dd>
      </dl>
      {warning ? (
        <p
          role="note"
          data-testid="operator-overview-card-scm-warning"
          className="rounded border border-border bg-muted px-2 py-1 text-xs text-muted-foreground"
        >
          {warning}
        </p>
      ) : null}
    </div>
  );
}

function FinanceSummary({ data }: { data: unknown }): ReactNode {
  // F5: `amount` is a STRING (minor units). NEVER `Number()` /
  // `parseFloat()` / `parseInt()` here. The MVP surfaces only the
  // "balance available" status + the optional currency code; no
  // numeric formatting / coercion. A producer-side balance change
  // does not depend on a FE numeric round-trip.
  const parsed = FinanceDataSchema.safeParse(data);
  const present = parsed.success && parsed.data.balance !== undefined;
  const currency =
    parsed.success && typeof parsed.data.balance?.currency === 'string'
      ? parsed.data.balance!.currency
      : null;
  return (
    <dl>
      <dt className="text-sm text-muted-foreground">잔액 정보</dt>
      <dd
        className="text-base font-medium text-foreground"
        data-testid="operator-overview-card-finance-status"
      >
        {present ? '잔액 조회 가능' : '잔액 정보 없음'}
        {present && currency ? (
          <span
            className="ml-2 rounded border border-border bg-muted px-2 py-0.5 text-xs font-normal text-muted-foreground"
            data-testid="operator-overview-card-finance-currency"
          >
            {currency}
          </span>
        ) : null}
      </dd>
    </dl>
  );
}

function ErpSummary({ data }: { data: unknown }): ReactNode {
  const parsed = ErpDataSchema.safeParse(data);
  const total =
    parsed.success && typeof parsed.data.meta?.totalElements === 'number'
      ? parsed.data.meta.totalElements
      : null;
  return (
    <dl>
      <dt className="text-sm text-muted-foreground">활성 부서 수</dt>
      <dd
        className="text-2xl font-semibold tabular-nums text-foreground"
        data-testid="operator-overview-card-erp-departments"
      >
        {total === null ? '—' : total.toLocaleString()}
      </dd>
    </dl>
  );
}

function OkSummary({ card }: { card: Card & { status: 'ok' } }): ReactNode {
  switch (card.domain) {
    case 'gap':
      return <GapSummary data={card.data} />;
    case 'wms':
      return <WmsSummary data={card.data} />;
    case 'scm':
      return <ScmSummary data={card.data} />;
    case 'finance':
      return <FinanceSummary data={card.data} />;
    case 'erp':
      return <ErpSummary data={card.data} />;
  }
}

// -------------------------------------------------------------------------
// Card shell — renders title + status-specific body. Server component.
// -------------------------------------------------------------------------

export interface DomainCardProps {
  card: Card;
  /** Seeds the per-card retry button (only relevant on `degraded`). */
  overviewForRetry: OperatorOverview;
}

export function DomainCard({ card, overviewForRetry }: DomainCardProps) {
  const id = `domain-card-${card.domain}`;

  // GAP-card drill-down (TASK-PC-FE-034 / AC-5 + AC-6): on the home
  // cross-domain overview the GAP card links to the GAP-only composed
  // overview detail (`/dashboards`, § 2.4.4 / ADR-MONO-015 D1-B —
  // accounts · audit · operators 3-leg). AC-6 default: the affordance is
  // present ONLY when the GAP card is `ok` — on `degraded` / `forbidden`
  // the GAP detail would itself degrade, so the link is suppressed and the
  // card keeps its existing placeholder + retry behaviour. The other 4
  // domain cards are unchanged (no drill-down — a separate future task).
  const gapDrilldown = card.domain === 'gap' && card.status === 'ok';

  return (
    <section
      aria-labelledby={`${id}-heading`}
      data-testid={`operator-overview-card-${card.domain}`}
      data-domain={card.domain}
      data-status={card.status}
      className="flex flex-col rounded-lg border border-border bg-background p-5"
    >
      <h2
        id={`${id}-heading`}
        className="mb-3 text-lg font-semibold text-foreground"
      >
        {gapDrilldown ? (
          <Link
            href="/dashboards"
            data-testid="operator-overview-card-gap-drilldown"
            className="inline-flex items-center gap-1 rounded underline-offset-2 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            {DOMAIN_TITLE[card.domain]}
            <span aria-hidden="true">→</span>
            <span className="sr-only">상세 보기</span>
          </Link>
        ) : (
          DOMAIN_TITLE[card.domain]
        )}
      </h2>

      <div className="flex-1">
        {card.status === 'ok' && <OkSummary card={card} />}

        {card.status === 'degraded' && (
          <div
            role="status"
            data-testid={`operator-overview-card-${card.domain}-degraded`}
            className="space-y-3"
          >
            <p className="text-sm text-muted-foreground">
              {DEGRADED_COPY[card.reason]}
            </p>
            <p
              className="text-xs text-muted-foreground"
              data-testid={`operator-overview-card-${card.domain}-degraded-reason`}
            >
              사유: {card.reason}
            </p>
            <RetryButton
              initial={overviewForRetry}
              label="다시 시도"
              testidSuffix={`${card.domain}-degraded`}
            />
          </div>
        )}

        {card.status === 'forbidden' && (
          <div
            role="status"
            data-testid={`operator-overview-card-${card.domain}-forbidden`}
            className="space-y-2"
          >
            <p className="text-sm text-muted-foreground">
              {FORBIDDEN_COPY[card.reason]}
            </p>
            <p
              className="text-xs text-muted-foreground"
              data-testid={`operator-overview-card-${card.domain}-forbidden-reason`}
            >
              사유: {card.reason}
            </p>
            {card.domain === 'finance' &&
              card.reason === 'MISSING_PREREQUISITE' && (
                <p
                  data-testid="operator-overview-card-finance-missing-hint"
                  className="text-xs text-muted-foreground"
                >
                  운영자 프로필에서 기본 finance 계정을 설정하세요.
                </p>
              )}
          </div>
        )}
      </div>
    </section>
  );
}
