import type { ReactNode } from 'react';
import Link from 'next/link';
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
  // `page.totalElements` = the number of inventory snapshot ROWS the producer
  // holds (location x sku x lot), read from the read-model page the leg
  // already fetches. See `WmsDataSchema` for why this is rows and not stock
  // units, and why there is no alert tile here any more.
  const parsed = WmsDataSchema.safeParse(data);
  const rows =
    parsed.success && typeof parsed.data.page?.totalElements === 'number'
      ? parsed.data.page.totalElements
      : null;
  return (
    <div className="space-y-2">
      <dl>
        <dt className="text-sm text-muted-foreground">재고 행 수</dt>
        <dd
          className="text-2xl font-semibold tabular-nums text-foreground"
          data-testid="operator-overview-card-wms-stock"
        >
          {rows === null ? '—' : rows.toLocaleString()}
        </dd>
      </dl>
      {/*
        TASK-PC-FE-296 — the owner's answer to "how does the operator see the
        low-stock count?" is **ⓒ: a link, not a second leg call**.

        🔴 Why not a number here. An alert count needs a SECOND producer query
        (`lowStockOnly=true&size=1`), and AC-0 measured what that costs: the
        composition fans out one slot PER DOMAIN (`EnumMap<DomainTarget, …>`),
        so the second call cannot be a 7th parallel leg — it runs INSIDE the
        wms leg body, sequentially, and the retry wraps the whole body. Worst
        case goes 2+0.15+2 = 4.15s (survives the 5s COMPOSITION_TIMEOUT) →
        (2+2)+0.15+(2+2) = 8.15s (does not). Exceeding it degrades the card to
        TIMEOUT, i.e. paying for the alert count risks losing the stock-row
        count that works today. The two calls would also share one circuit key
        (WMS, operator-overview).

        🔵 So this link costs zero calls, keeps the § 2.4.9.1 verbatim
        invariant intact, and gives the operator the EXACT number rather than
        the overview's approximation.

        🔴 The href is only honest because `wms/inventory/page.tsx` now seeds
        the filter from this param — before that the screen dropped it and
        rendered the unfiltered list. Do not "simplify" either half alone.
      */}
      <Link
        href="/wms/inventory?lowStockOnly=true"
        data-testid="operator-overview-card-wms-lowstock-link"
        className="inline-block text-sm underline underline-offset-2 text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
      >
        저재고 재고 보기
      </Link>
    </div>
  );
}

function ScmSummary({ data }: { data: unknown }): ReactNode {
  // `data.totalElements` = snapshot ROWS (node x sku), not distinct nodes —
  // a page cannot answer "how many nodes" and the label says rows.
  const parsed = ScmDataSchema.safeParse(data);
  const warning = parsed.success ? parsed.data.meta?.warning : undefined;
  const rows =
    parsed.success && typeof parsed.data.data?.totalElements === 'number'
      ? parsed.data.data.totalElements
      : null;
  return (
    <div className="space-y-2">
      <dl>
        <dt className="text-sm text-muted-foreground">스냅샷 행 수</dt>
        <dd
          className="text-2xl font-semibold tabular-nums text-foreground"
          data-testid="operator-overview-card-scm-nodes"
        >
          {rows === null ? '—' : rows.toLocaleString()}
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
  // F5: every money field in a balance row is a STRING (minor units). NEVER
  // `Number()` / `parseFloat()` / `parseInt()` here. The MVP surfaces only the
  // "balance available" status + the currency code; no numeric formatting or
  // coercion, so a producer-side balance change does not depend on a FE
  // numeric round-trip.
  const parsed = FinanceDataSchema.safeParse(data);
  const rows = parsed.success && parsed.data.data ? parsed.data.data : [];
  const present = rows.length > 0;
  // A single-currency account names its currency on the card. A multi-currency
  // account has no single answer, and the producer body carries no "account
  // currency" field to pick one by — so the chip is omitted rather than
  // showing whichever row happened to come first.
  const currency =
    rows.length === 1 && typeof rows[0].currency === 'string'
      ? rows[0].currency
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
