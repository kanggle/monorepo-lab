import { SAMPLE_AS_OF } from '../codes';
import { IAM_FIXTURE_HANDLERS } from './iam';
import { ERP_FIXTURE_HANDLERS } from './erp';
import { ECOMMERCE_FIXTURE_HANDLERS } from './ecommerce';
import { FINANCE_FIXTURE_HANDLERS, SAMPLE_FINANCE_DEFAULT_ACCOUNT_ID } from './finance';
import { WMS_FIXTURE_HANDLERS } from './wms';
import { SCM_FIXTURE_HANDLERS } from './scm';

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
 * TASK-PC-FE-295 — the overview cards now carry the PRODUCER'S OWN response
 * body, because that is what the real leg carries: console-bff's adapters
 * (`FinanceBalanceReadAdapter`, `WmsInventoryReadAdapter`,
 * `ScmInventoryReadAdapter`) load the producer body verbatim into
 * `cards[i].data`, with no reshaping anywhere on the path.
 *
 * 🔴🔴 Before 295 the three constants below deliberately emitted a DIFFERENT
 * shape — the one `FinanceDataSchema` / `WmsDataSchema` / `ScmDataSchema` read
 * — and each carried a comment saying so and calling the mismatch "out of
 * scope". They were right that a sample fixture cannot fix a production shape
 * bug; they were also, between them, the reason nothing went red: the sample
 * world rendered a working card out of an invented shape while the logged-in
 * operator got «잔액 정보 없음» and «—». 295 fixed the schemas against the
 * producers, so the sample world can now hand the card exactly what the wire
 * hands it, and any future divergence shows up HERE as a broken sample screen.
 *
 * Each body is taken from the SAME fixture handler answering the SAME query
 * the real adapter sends, so a card's number and the list screen that number
 * summarises cannot disagree (TASK-PC-FE-285's rule, unchanged).
 */
function legBody(
  handler: ((path: string) => unknown) | undefined,
  path: string,
): Record<string, unknown> {
  const body = handler?.(path);
  if (!body || typeof body !== 'object') {
    throw new Error(`sample overview: no body for ${path}`);
  }
  return body as Record<string, unknown>;
}

/**
 * `GET /api/finance/accounts/{id}/balances` — `{ data: [ {currency, ledger,
 * available, held} ], meta }`. The card reads `data[]` (F5: every money field
 * stays a minor-units string and is never coerced).
 */
const FINANCE_BALANCES_BODY = (() => {
  const path = `/api/finance/accounts/${SAMPLE_FINANCE_DEFAULT_ACCOUNT_ID}/balances`;
  const body = legBody(FINANCE_FIXTURE_HANDLERS['flat:finance'], path);
  const rows = body.data;
  // Not vacuous: an empty `data[]` is the ONE honest "no balance" case, so a
  // sample world that fell to it would prove the card can render nothing.
  if (!Array.isArray(rows) || rows.length === 0) {
    throw new Error(`sample overview: no finance balance rows for ${path}`);
  }
  return body;
})();

/**
 * `GET /api/v1/admin/dashboard/inventory` — the read-model page
 * `{ content[], page: {number,size,totalElements,totalPages}, sort }`. The
 * card reads `page.totalElements` = inventory snapshot ROW count.
 *
 * 🔴 No stock-unit sum is derived here any more. The producer returns one
 * page; summing it and calling the result «총 재고» is the fabricated total
 * TASK-PC-FE-295 AC-6 rules out. The alert tile is gone for the same reason —
 * an alert count needs a second query (`lowStockOnly=true`), i.e. a second
 * outbound leg, which is a cost decision and not a rendering fix.
 */
const WMS_INVENTORY_BODY = (() => {
  const path = '/api/v1/admin/dashboard/inventory';
  const body = legBody(WMS_FIXTURE_HANDLERS['wms:wms'], path);
  const page = body.page as { totalElements?: unknown } | undefined;
  if (!page || typeof page.totalElements !== 'number' || page.totalElements <= 0) {
    throw new Error(`sample overview: no wms page total for ${path}`);
  }
  return body;
})();

/**
 * `GET /api/v1/inventory-visibility/snapshot` — `{ data: { content[], page,
 * size, totalElements, totalPages }, meta: { timestamp, warning, staleness } }`.
 * The card reads `data.totalElements` = snapshot ROW count, plus the S5
 * `meta.warning` hint.
 *
 * 🔵 The literal path differs from the production leg's
 * (`/api/inventory-visibility/snapshot`, direct-to-producer per
 * TASK-MONO-162) because this console's own scm client goes through the scm
 * gateway; the BODY SHAPE is the same envelope, which is what the card reads.
 */
const SCM_SNAPSHOT_BODY = (() => {
  const path = '/api/v1/inventory-visibility/snapshot';
  const body = legBody(SCM_FIXTURE_HANDLERS['flat:scm'], path);
  const data = body.data as { content?: unknown; totalElements?: unknown } | undefined;
  const meta = body.meta as { warning?: unknown } | undefined;
  if (!data || typeof data.totalElements !== 'number' || data.totalElements <= 0) {
    throw new Error(`sample overview: no scm snapshot total for ${path}`);
  }
  if (typeof meta?.warning !== 'string') {
    throw new Error(`sample overview: no scm S5 warning for ${path}`);
  }
  const rows = Array.isArray(data.content) ? data.content : [];
  const nodeIds = new Set(
    rows
      .map((r) => (r as { nodeId?: unknown }).nodeId)
      .filter((id): id is string => typeof id === 'string'),
  );
  // Not vacuous: a single-node world could not tell "rows" from "nodes", and
  // telling those two apart is exactly what 295 corrected on this card.
  if (nodeIds.size < 2) {
    throw new Error('sample overview: scm snapshot must span 2+ nodes');
  }
  return body;
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
    { domain: 'wms', status: 'ok', data: WMS_INVENTORY_BODY },
    { domain: 'scm', status: 'ok', data: SCM_SNAPSHOT_BODY },
    { domain: 'finance', status: 'ok', data: FINANCE_BALANCES_BODY },
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
