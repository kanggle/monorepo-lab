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
 * TASK-PC-FE-287 AC-8 — the wms card asks the SAME query the console-bff
 * `WmsInventoryReadAdapter` sends (`GET /api/v1/admin/dashboard/inventory`,
 * no query params — the adapter's own `read()` forwards no page/size) of the
 * SAME wms fixture handler the `/wms` overview and `/wms/inventory` screen
 * are answered by. Before this, the card's numbers (총 재고 `48,210` · 알림 `3`)
 * were hand-typed and had no relationship to any browsable inventory row —
 * the same defect class 285's CORRECTION named for IAM/ERP/E-Commerce and 286
 * closed for finance.
 *
 * 🔵 `WmsDataSchema` (`operator-overview-types.ts`) expects
 * `{ inventorySnapshot: { totalStockUnits, alertCount } }`, but the real wire
 * shape `WmsInventoryReadAdapter.read()` puts on the leg is the RAW read-model
 * page (`{content, page}`) — there is no `inventorySnapshot` key anywhere in
 * the real response. This is the SAME class of BFF-leg / FE-card shape
 * mismatch 286 D2 found for finance (`TASK-PC-FE-295` already tracks the
 * finance instance; this is a console-bff/BFF-composition concern out of a
 * sample-fixture ticket's scope — no domain fixture can fix a real shape bug
 * in production code). Reproducing the mismatch here would render the card
 * EMPTY for every sample visitor, defeating ADR-MONO-074's point — so this
 * fixture keeps the `WmsDataSchema`-shaped object the card actually parses,
 * with its VALUES derived from the ONE query the real adapter makes: total
 * stock = Σ `onHandQty` over the rows that query returns, alerts = the count
 * of rows THAT SAME `/wms/inventory` screen flags `lowStockFlag` on (the only
 * "alert" concept the inventory page itself renders — `WmsInventoryDataTable`
 * `저재고` badge — `dashboard/alerts` is a SEPARATE producer table the adapter
 * never calls, so a card built from it would not be "the same query").
 */
const WMS_INVENTORY_SNAPSHOT = (() => {
  const path = '/api/v1/admin/dashboard/inventory';
  const body = WMS_FIXTURE_HANDLERS['wms:wms']?.(path) as
    | { content?: Array<{ onHandQty?: unknown; lowStockFlag?: unknown }> }
    | undefined;
  const rows = body?.content;
  if (!Array.isArray(rows) || rows.length === 0) {
    throw new Error(`sample overview: no wms inventory rows for ${path}`);
  }
  const totalStockUnits = rows.reduce(
    (sum, r) => sum + (typeof r.onHandQty === 'number' ? r.onHandQty : 0),
    0,
  );
  const alertCount = rows.filter((r) => r.lowStockFlag === true).length;
  return { totalStockUnits, alertCount };
})();

/**
 * TASK-PC-FE-288 AC-8 — the scm card's node set/names/warning are derived
 * from THIS ticket's own `flat:scm` fixture handler answering the cross-node
 * inventory-visibility snapshot query — the SAME data `/scm/inventory`
 * renders (the snapshot table's «노드» column is each row's `nodeId`; the
 * node NAMES come from the same fixture's `/nodes` registry, so a node id
 * that appears in the snapshot is guaranteed to resolve there — AC-8's "노드
 * 수·노드 id·이름이 `/scm/inventory` 의 노드와 같아야 한다").
 *
 * 🔵 the production composition leg (`ScmInventoryReadAdapter.read()`) calls
 * the scm inventory-visibility PRODUCER directly — `GET
 * /api/inventory-visibility/snapshot` (no `/v1`, no scm gateway; TASK-MONO-162
 * topology note in that adapter's own docstring) — a DIFFERENT literal path
 * than the one this console's OWN `scm-inventory-visibility-api.ts` sends
 * through the scm gateway (`/api/v1/inventory-visibility/snapshot`), and its
 * `data` shape (`{content,page,size,totalElements}` or a bare array) has no
 * top-level `nodes` key at all — a pre-existing BFF-leg / FE-card
 * (`ScmDataSchema`) shape mismatch (the SAME class of defect 286 D2 found for
 * finance and 287 AC-8 found for wms; tracked by `TASK-PC-FE-295`, which the
 * coordinator has extended to cover scm too — out of THIS ticket's scope, no
 * domain fixture can fix a real shape bug in production code). Reproducing
 * that raw shape here would render the card EMPTY for every sample visitor,
 * defeating ADR-MONO-074's point — so this fixture keeps the
 * `ScmDataSchema`-shaped object (`{meta:{warning}, nodes:[{nodeId,name}]}`)
 * the card actually parses, with its VALUES derived from this ticket's own
 * gateway-shaped snapshot query (the closest analogous "cross-node inventory"
 * read this domain's sample world can answer).
 */
const SCM_OVERVIEW_NODES = (() => {
  const path = '/api/v1/inventory-visibility/snapshot';
  const snapshotBody = SCM_FIXTURE_HANDLERS['flat:scm']?.(path) as
    | { data?: { content?: Array<{ nodeId?: unknown }> }; meta?: { warning?: unknown } }
    | undefined;
  const rows = snapshotBody?.data?.content;
  if (!Array.isArray(rows) || rows.length === 0) {
    throw new Error(`sample overview: no scm snapshot rows for ${path}`);
  }
  const warning = snapshotBody?.meta?.warning;
  if (typeof warning !== 'string') {
    throw new Error(`sample overview: no scm S5 warning for ${path}`);
  }
  const nodeIds = Array.from(
    new Set(rows.map((r) => r.nodeId).filter((id): id is string => typeof id === 'string')),
  );
  if (nodeIds.length < 2) {
    // Not vacuous — a single-node world could not prove "node SET matches",
    // only "a node matches" (287's identical discipline for its alertCount).
    throw new Error('sample overview: scm snapshot must span 2+ nodes');
  }
  const nodesPath = '/api/v1/inventory-visibility/nodes';
  const nodesBody = SCM_FIXTURE_HANDLERS['flat:scm']?.(nodesPath) as
    | { data?: Array<{ id?: unknown; name?: unknown }> }
    | undefined;
  const nodeRows = nodesBody?.data;
  if (!Array.isArray(nodeRows) || nodeRows.length === 0) {
    throw new Error(`sample overview: no scm node rows for ${nodesPath}`);
  }
  const nodes = nodeIds.map((nodeId) => {
    const found = nodeRows.find((n) => n.id === nodeId);
    if (!found || typeof found.name !== 'string') {
      throw new Error(`sample overview: scm node ${nodeId} does not resolve on ${nodesPath}`);
    }
    return { nodeId, name: found.name };
  });
  return { nodes, warning };
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
      data: {
        inventorySnapshot: {
          totalStockUnits: WMS_INVENTORY_SNAPSHOT.totalStockUnits,
          alertCount: WMS_INVENTORY_SNAPSHOT.alertCount,
        },
      },
    },
    {
      domain: 'scm',
      status: 'ok',
      data: {
        meta: { warning: SCM_OVERVIEW_NODES.warning },
        nodes: SCM_OVERVIEW_NODES.nodes,
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
