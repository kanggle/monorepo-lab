import type { ReactNode } from 'react';
import Link from 'next/link';
import {
  type Card,
  type DomainKey,
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
 * (the card stays `ok` semantically — the composition classified it as ok;
 * only the UI summary is degraded; never a UI crash).
 *
 * TASK-PC-FE-364 — every card now shows ONE business figure (the page total of
 * a filtered list read — contract § 2.4.9.1) instead of a row count, a one-line
 * description of what is counted, and a link to the screen where that work is
 * done. 🔴 The links carry no filter parameter: none of the destinations seeds
 * its status filter from the URL today, and a parameter the screen drops lands
 * on the unfiltered list with nothing reporting it (the TASK-PC-FE-296 lesson —
 * see the wms low-stock link below, whose destination DOES honour its param).
 *
 * A `0` is rendered as "0" (explicit `typeof === 'number'`, never truthiness):
 * «nothing waiting» is a real answer. The ONE case that must not be a 0 is the
 * erp card for an account linked to no employee — see {@link ErpSummary}.
 *
 * F5 money discipline (finance card): the discrepancy rows carry minor-units
 * strings; this card reads only the count and never touches a row.
 */

const NUMBER_CLASS = 'text-2xl font-semibold tabular-nums text-foreground';
const LINK_CLASS =
  'inline-block text-sm underline underline-offset-2 text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary';

/** The one number, its label and the line explaining what is counted. */
function CountTile({
  domain,
  label,
  description,
  value,
  testId,
}: {
  domain: DomainKey;
  label: string;
  description: string;
  value: number | null;
  testId: string;
}): ReactNode {
  return (
    <dl>
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd className={NUMBER_CLASS} data-testid={testId}>
        {value === null ? '—' : value.toLocaleString()}
      </dd>
      <dd
        className="mt-1 text-xs text-muted-foreground"
        data-testid={`operator-overview-card-${domain}-description`}
      >
        {description}
      </dd>
    </dl>
  );
}

/** The work screen this card summarises (no filter parameter — see module doc). */
function WorkLink({
  domain,
  href,
  children,
}: {
  domain: DomainKey;
  href: string;
  children: string;
}): ReactNode {
  return (
    <Link
      href={href}
      data-testid={`operator-overview-card-${domain}-work-link`}
      className={LINK_CLASS}
    >
      {children}
    </Link>
  );
}

function GapSummary({ data }: { data: unknown }): ReactNode {
  const parsed = GapDataSchema.safeParse(data);
  const total =
    parsed.success && typeof parsed.data.totalElements === 'number'
      ? parsed.data.totalElements
      : null;
  return (
    <div className="space-y-2">
      {/*
        TASK-PC-FE-364 — `status=LOCKED` filters the ACCOUNT's status
        (AccountJpaRepository site query): a pool account locked out of one
        site only is not counted, hence «계정». Still the SELECTED tenant's own +
        pool-member end-user accounts (operators excluded — TASK-PC-FE-304).
      */}
      <CountTile
        domain="iam"
        label="잠긴 계정 (이 테넌트)"
        description="계정 자체가 잠긴 회원 계정입니다. 사이트에서만 잠긴 계정은 세지 않습니다."
        value={total}
        testId="operator-overview-card-iam-locked"
      />
      <WorkLink domain="iam" href="/accounts">
        계정 운영으로 이동
      </WorkLink>
    </div>
  );
}

function WmsSummary({ data }: { data: unknown }): ReactNode {
  // `page.totalElements` of `dashboard/orders?status=RECEIVED` — received and
  // neither shipped nor cancelled. Tenant-owned dashboard: a customer tenant
  // with no orders of its own correctly reads 0.
  const parsed = WmsDataSchema.safeParse(data);
  const unshipped =
    parsed.success && typeof parsed.data.page?.totalElements === 'number'
      ? parsed.data.page.totalElements
      : null;
  return (
    <div className="space-y-2">
      <CountTile
        domain="wms"
        label="미출고 주문"
        description="접수됐고 아직 출하 · 취소되지 않은 출고 주문입니다."
        value={unshipped}
        testId="operator-overview-card-wms-unshipped"
      />
      <div className="flex flex-col gap-1">
        <WorkLink domain="wms" href="/wms/outbound">
          출고 화면으로 이동
        </WorkLink>
        {/*
          TASK-PC-FE-296 — the owner's answer to "how does the operator see the
          low-stock count?" is **ⓒ: a link, not a second leg call**. It stays
          after TASK-PC-FE-364 replaced this card's number: a low-stock count
          would still be a SECOND call on the wms leg (one card per domain,
          one shared LEG_TIMEOUT_MS), which «one call per card» rules out.

          🔴 The href is only honest because `wms/inventory/page.tsx` seeds the
          filter from this param — before that the screen dropped it and
          rendered the unfiltered list. Do not "simplify" either half alone.
        */}
        <Link
          href="/wms/inventory?lowStockOnly=true"
          data-testid="operator-overview-card-wms-lowstock-link"
          className={LINK_CLASS}
        >
          저재고 재고 보기
        </Link>
      </div>
    </div>
  );
}

function ScmSummary({ data }: { data: unknown }): ReactNode {
  // `data.totalElements` of `procurement/po?status=DRAFT` — the caller's
  // tenant (JWT `tenant_id`), POs not yet submitted to a supplier.
  const parsed = ScmDataSchema.safeParse(data);
  const drafts =
    parsed.success && typeof parsed.data.data?.totalElements === 'number'
      ? parsed.data.data.totalElements
      : null;
  return (
    <div className="space-y-2">
      <CountTile
        domain="scm"
        label="제출 대기 발주 (초안)"
        description="초안 상태로 아직 공급사에 제출하지 않은 발주입니다."
        value={drafts}
        testId="operator-overview-card-scm-draft-po"
      />
      <WorkLink domain="scm" href="/scm/procurement">
        조달 화면으로 이동
      </WorkLink>
    </div>
  );
}

function FinanceSummary({ data }: { data: unknown }): ReactNode {
  // `meta.totalElements` of `reconciliation/discrepancies?status=OPEN`. F5: no
  // row is read, so no minor-units string can be coerced here.
  const parsed = FinanceDataSchema.safeParse(data);
  const open =
    parsed.success && typeof parsed.data.meta?.totalElements === 'number'
      ? parsed.data.meta.totalElements
      : null;
  return (
    <div className="space-y-2">
      <CountTile
        domain="finance"
        label="미해소 대사 차이"
        description="대사에서 발견됐고 아직 해소하지 않은 차이입니다."
        value={open}
        testId="operator-overview-card-finance-open-discrepancies"
      />
      <WorkLink domain="finance" href="/ledger">
        원장 화면으로 이동
      </WorkLink>
    </div>
  );
}

function ErpSummary({ data }: { data: unknown }): ReactNode {
  const parsed = ErpDataSchema.safeParse(data);
  // 🔴 TASK-PC-FE-364 Failure Scenario 2 — an account linked to no employee gets
  // an empty page WITHOUT `meta.actorEmployeeId` (approval-api.md § v2.4). That
  // is «not linked», not «nothing waiting»: rendering its 0 would read as «no
  // approvals to do». So the link state is decided FIRST and the count is only
  // shown when the producer says whose queue it is. The id is never rendered.
  const linked =
    parsed.success &&
    typeof parsed.data.meta?.actorEmployeeId === 'string' &&
    parsed.data.meta.actorEmployeeId.length > 0;
  const pending =
    parsed.success && linked && typeof parsed.data.meta?.totalElements === 'number'
      ? parsed.data.meta.totalElements
      : null;

  if (parsed.success && !linked) {
    return (
      <div className="space-y-2">
        <dl>
          <dt className="text-sm text-muted-foreground">내 결재 대기</dt>
          <dd
            className="text-base font-medium text-foreground"
            data-testid="operator-overview-card-erp-not-linked"
          >
            직원 연결 없음
          </dd>
          <dd className="mt-1 text-xs text-muted-foreground">
            이 운영자 계정에 연결된 직원이 없어 결재 대기를 셀 수 없습니다.
          </dd>
        </dl>
        <WorkLink domain="erp" href="/erp/approval">
          결재함으로 이동
        </WorkLink>
      </div>
    );
  }

  return (
    <div className="space-y-2">
      <CountTile
        domain="erp"
        label="내 결재 대기"
        description="지금 내가 결재할 차례인 요청(상신 · 검토 중)입니다."
        value={pending}
        testId="operator-overview-card-erp-inbox"
      />
      <WorkLink domain="erp" href="/erp/approval">
        결재함으로 이동
      </WorkLink>
    </div>
  );
}

function EcommerceSummary({ data }: { data: unknown }): ReactNode {
  // `totalElements` of `orders?status=CONFIRMED` — paid, shipping not started.
  // `0` renders "0" (explicit number check, not truthiness).
  const parsed = EcommerceDataSchema.safeParse(data);
  const toShip =
    parsed.success && typeof parsed.data.totalElements === 'number'
      ? parsed.data.totalElements
      : null;
  return (
    <div className="space-y-2">
      <CountTile
        domain="ecommerce"
        label="출고 대기 주문 (결제 완료)"
        description="결제가 확인됐고 아직 배송을 시작하지 않은 주문입니다."
        value={toShip}
        testId="operator-overview-card-ecommerce-to-ship"
      />
      <WorkLink domain="ecommerce" href="/ecommerce/orders">
        주문 화면으로 이동
      </WorkLink>
    </div>
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
