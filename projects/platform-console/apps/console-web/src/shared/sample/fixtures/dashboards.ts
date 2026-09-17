import { SAMPLE_AS_OF } from '../codes';
import { IAM_FIXTURE_HANDLERS } from './iam';
import { ERP_FIXTURE_HANDLERS } from './erp';
import { ECOMMERCE_FIXTURE_HANDLERS } from './ecommerce';
import { FINANCE_FIXTURE_HANDLERS, SAMPLE_FINANCE_DEFAULT_ACCOUNT_ID } from './finance';

/**
 * TASK-PC-FE-285 (coordinator review) — an overview card's count is NOT typed
 * here. It is the answer the SAME domain fixture gives to the SAME request the
 * console-bff adapter makes, so the landing card and the list screen it
 * summarises cannot disagree. Before this, the cards said IAM 128 · ERP 12 ·
 * E-Commerce 342 while the lists they summarise held 5 · 3 · 3 — on the very
 * first screen an anonymous visitor lands on (`/` → `/dashboards/overview`).
 *
 * Paths mirror console-bff's outbound adapters (`IamAccountsReadAdapter`,
 * `ErpDepartmentsReadAdapter`, `EcommerceOverviewReadAdapter`). A fixture that
 * stops answering throws at module load — loud, never a silent fallback number.
 */
function countFrom(
  handler: ((path: string) => unknown) | undefined,
  path: string,
  pick: (body: never) => unknown,
): number {
  const body = handler?.(path);
  const n = body === undefined ? undefined : pick(body as never);
  if (typeof n !== 'number') {
    throw new Error(`sample overview: no count for ${path}`);
  }
  return n;
}

const IAM_ACCOUNT_COUNT = countFrom(
  IAM_FIXTURE_HANDLERS['iam:accounts'],
  '/api/admin/accounts?page=0&size=1',
  (b: { totalElements?: unknown }) => b.totalElements,
);
const ERP_ACTIVE_DEPARTMENT_COUNT = countFrom(
  ERP_FIXTURE_HANDLERS['flat:erp'],
  '/api/erp/masterdata/departments?active=true&page=0&size=1',
  (b: { meta?: { totalElements?: unknown } }) => b.meta?.totalElements,
);
const ECOMMERCE_PRODUCT_COUNT = countFrom(
  ECOMMERCE_FIXTURE_HANDLERS['ecommerce:ecommerce'],
  '/api/admin/products?page=0&size=1',
  (b: { totalElements?: unknown }) => b.totalElements,
);

/**
 * TASK-PC-FE-286 AC-7 — the finance card asks the SAME query the console-bff
 * `FinanceBalanceReadAdapter` sends (`GET /api/finance/accounts/{id}/balances`,
 * § readBalances) of the SAME finance fixture handler the `/finance` overview
 * and `/finance/accounts` screens are answered by. Before this, the card's
 * balance (`1,250,000,000` KRW on `sample-account-0001`) was hand-typed and
 * had no relationship to any browsable account — exactly the defect class
 * this task's coordinator note (285's CORRECTION) flags for wms/scm too.
 *
 * 🔵 the production composition leg (`OperatorOverviewCompositionUseCase.
 * callFinance`) passes `financePort.readBalances(...)`'s raw body straight
 * through as the leg's `data` — i.e. the real wire shape is the balances
 * envelope `{ data: [Balance], meta }`, NOT `{ balance, accountId }`.
 * console-web's OWN `FinanceDataSchema` (`operator-overview-types.ts`)
 * expects the latter — a pre-existing shape mismatch between the BFF leg and
 * the FE card schema that is a console-bff/BFF-composition concern, out of
 * this ticket's scope (no domain fixture can fix a real shape bug in
 * production code). Reproducing that mismatch here would make the sample
 * card render EMPTY for every visitor, which defeats ADR-MONO-074's entire
 * point (a real, working page) — so this fixture keeps emitting the
 * `FinanceDataSchema`-shaped object the card actually parses, with its VALUE
 * derived from the query above (never re-typed).
 */
const FINANCE_BALANCE = (() => {
  const path = `/api/finance/accounts/${SAMPLE_FINANCE_DEFAULT_ACCOUNT_ID}/balances`;
  const body = FINANCE_FIXTURE_HANDLERS['flat:finance']?.(path) as
    | { data?: Array<{ currency?: unknown; ledger?: unknown }> }
    | undefined;
  const row = body?.data?.[0];
  if (!row || typeof row.ledger !== 'string' || typeof row.currency !== 'string') {
    throw new Error(`sample overview: no finance balance for ${path}`);
  }
  return { amount: row.ledger, currency: row.currency };
})();

/**
 * Dashboard fixtures (R3ⓐ — the first screens made `ready`): the console-bff
 * operator overview (§ 2.4.9.1), domain health (§ 2.4.9.2) and the
 * notification aggregator inbox (ADR-MONO-043 §4).
 *
 * Hand-authored synthetic data (ADR-MONO-074 A4). Each shape is parsed by the
 * SAME zod schema the real screen uses (`tests/unit/sample-fixtures-schema.test.ts`)
 * — a fixture that broke the schema would render as a section degrade and be
 * indistinguishable from "broken" (task Failure Scenario).
 *
 * R2ⓐ: human-readable strings end with «(샘플)»; ids, codes, amounts, counts,
 * dates and status enums do not (`tests/unit/sample-label-rule.test.ts`).
 */

export const SAMPLE_OPERATOR_OVERVIEW = {
  asOf: SAMPLE_AS_OF,
  cards: [
    { domain: 'iam', status: 'ok', data: { totalElements: IAM_ACCOUNT_COUNT } },
    {
      domain: 'wms',
      status: 'ok',
      data: { inventorySnapshot: { totalStockUnits: 48210, alertCount: 3 } },
    },
    {
      domain: 'scm',
      status: 'ok',
      data: {
        meta: { warning: 'Not for procurement decisions (S5) (샘플)' },
        nodes: [
          { nodeId: 'sample-node-01', name: '평택 물류센터 (샘플)' },
          { nodeId: 'sample-node-02', name: '이천 물류센터 (샘플)' },
          { nodeId: 'sample-node-03', name: '부산 항만창고 (샘플)' },
        ],
      },
    },
    {
      domain: 'finance',
      status: 'ok',
      data: {
        balance: { amount: FINANCE_BALANCE.amount, currency: FINANCE_BALANCE.currency },
        accountId: SAMPLE_FINANCE_DEFAULT_ACCOUNT_ID,
      },
    },
    // 🔵 wms (totalStockUnits/alertCount) and scm (nodes) are still typed
    //    here — their domain fixtures do not exist yet. Each owning ticket
    //    (TASK-PC-FE-287 wms · 288 scm) derives its card the same way and
    //    adds its row to `sample-overview-cards-match-lists.test.ts`.
    { domain: 'erp', status: 'ok', data: { meta: { totalElements: ERP_ACTIVE_DEPARTMENT_COUNT } } },
    { domain: 'ecommerce', status: 'ok', data: { totalElements: ECOMMERCE_PRODUCT_COUNT } },
  ],
} as const;

export const SAMPLE_DOMAIN_HEALTH = {
  asOf: SAMPLE_AS_OF,
  cards: [
    { domain: 'iam', status: 'ok', data: { status: 'UP' } },
    { domain: 'wms', status: 'ok', data: { status: 'UP' } },
    { domain: 'scm', status: 'ok', data: { status: 'UP' } },
    { domain: 'finance', status: 'ok', data: { status: 'UP' } },
    { domain: 'erp', status: 'ok', data: { status: 'UP' } },
    { domain: 'ecommerce', status: 'ok', data: { status: 'UP' } },
  ],
} as const;

/**
 * CORRECTION (TASK-PC-FE-285 coordinator review, 2026-09-17 UTC) — every
 * `sourceId` below MUST be a real id in the erp approval fixture world
 * (`shared/sample/fixtures/erp.ts`'s `ERP_APPROVAL_REQUESTS`), and each
 * notification's `type` MUST match that approval's actual `status` — the
 * bell is mounted on every screen a sample visitor sees, so a dangling
 * `sourceId` is a 404 one click away from anywhere in the console, and a
 * `type`/`status` mismatch is the exact "two screens disagree" defect class
 * TASK-PC-FE-284's settlement CORRECTION already named. Before this fix the
 * three ids (`sample-approval-1001/1000/0998`) existed ONLY in this file —
 * `tests/unit/notification-inbox-approval-links.test.ts` walks the inbox →
 * approval via `sampleResponse` (never the raw constants) and proves it.
 */
export const SAMPLE_NOTIFICATION_INBOX = {
  asOf: SAMPLE_AS_OF,
  items: [
    {
      // appr-sample-0001 — status SUBMITTED, submitterId emp-sample-0002 (이민준).
      id: 'sample-notification-0001',
      sourceDomain: 'erp',
      type: 'APPROVAL_SUBMITTED',
      title: '영업1팀 예산 증액 요청 (샘플)',
      body: '이민준 님이 영업1팀 예산 증액 요청을 상신했습니다 (샘플)',
      sourceType: 'APPROVAL',
      sourceId: 'appr-sample-0001',
      read: false,
      createdAt: '2026-09-15T09:30:00Z',
    },
    {
      // appr-sample-0004 — status APPROVED, title '이민준 부서 이동 승인'.
      id: 'sample-notification-0002',
      sourceDomain: 'erp',
      type: 'APPROVAL_APPROVED',
      title: '이민준 부서 이동 승인 완료 (샘플)',
      body: '이민준 부서 이동 요청이 최종 승인되었습니다 (샘플)',
      sourceType: 'APPROVAL',
      sourceId: 'appr-sample-0004',
      read: false,
      createdAt: '2026-09-14T23:10:00Z',
    },
    {
      // appr-sample-0005 — status REJECTED, reason '예산 부족으로 반려'.
      id: 'sample-notification-0003',
      sourceDomain: 'erp',
      type: 'APPROVAL_REJECTED',
      title: '영업2팀 폐지 결재 반려 (샘플)',
      body: '예산 부족으로 반려되었습니다 (샘플)',
      sourceType: 'APPROVAL',
      sourceId: 'appr-sample-0005',
      read: true,
      createdAt: '2026-09-12T02:00:00Z',
      readAt: '2026-09-12T05:00:00Z',
    },
  ],
  meta: { page: 0, size: 20, totalElements: 3 },
  degradedDomains: [],
} as const;
