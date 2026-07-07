import type { ReactNode } from 'react';
import {
  type Card,
  GapDataSchema,
  WmsDataSchema,
  ScmDataSchema,
  FinanceDataSchema,
  ErpDataSchema,
  EcommerceDataSchema,
} from '../api/operator-overview-types';

/**
 * Per-domain `ok`-branch summary renderers for {@link DomainCard}
 * (TASK-PC-FE-011 — extracted TASK-PC-FE-212 presentational split). Server
 * components. Each renderer reads the producer-shaped `data` via its narrow
 * `*DataSchema.safeParse`; a parse miss returns the fallback "—" placeholder
 * (the card stays `ok` semantically — the BE classified it as ok; only the UI
 * summary is degraded; never a UI crash). Markup / testids are byte-verbatim
 * from the former god-file.
 *
 * F5 money discipline (finance card): `balance.amount` is treated as a
 * STRING; the UI never coerces with `Number(...)`/`parseFloat(...)`/
 * `parseInt(...)`. The MVP surfaces only "balance available" framing with the
 * optional currency code — no numeric formatting.
 */

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
        data-testid="operator-overview-card-iam-total"
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

function EcommerceSummary({ data }: { data: unknown }): ReactNode {
  // `totalElements` is the tenant's total product count (catalog size,
  // status-unfiltered). `totalElements: 0` is a valid empty catalog —
  // surface it as "0", NOT hidden. The explicit `=== null` check (NOT a
  // truthiness gate) keeps 0 rendering as "0" rather than the "—" fallback.
  const parsed = EcommerceDataSchema.safeParse(data);
  const total =
    parsed.success && typeof parsed.data.totalElements === 'number'
      ? parsed.data.totalElements
      : null;
  return (
    <dl>
      <dt className="text-sm text-muted-foreground">상품 수</dt>
      <dd
        className="text-2xl font-semibold tabular-nums text-foreground"
        data-testid="operator-overview-card-ecommerce-products"
      >
        {total === null ? '—' : total.toLocaleString()}
      </dd>
    </dl>
  );
}

export function OkSummary({
  card,
}: {
  card: Card & { status: 'ok' };
}): ReactNode {
  switch (card.domain) {
    case 'iam':
      return <GapSummary data={card.data} />;
    case 'wms':
      return <WmsSummary data={card.data} />;
    case 'scm':
      return <ScmSummary data={card.data} />;
    case 'finance':
      return <FinanceSummary data={card.data} />;
    case 'erp':
      return <ErpSummary data={card.data} />;
    case 'ecommerce':
      return <EcommerceSummary data={card.data} />;
  }
}
